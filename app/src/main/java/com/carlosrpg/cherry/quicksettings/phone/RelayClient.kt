package com.carlosrpg.cherry.quicksettings.phone

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The car's side of the relay (relay/ in this repo): one outbound WebSocket to the car's Durable
 * Object, over which the relay forwards phones' requests; each is answered by [handle] exactly as
 * a LAN request would be, marked remote. Reconnects with backoff (2 s doubling to 5 min), and at
 * once on [wake] (the network came back). A small RFC 6455 client: text frames, no extensions,
 * so no new dependency.
 */
class RelayClient(
    private val url: String,
    private val key: String,
    private val carId: String,
    private val handle: (HttpRequest) -> HttpResponse,
) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile private var running = false
    @Volatile private var socket: Socket? = null
    private val lock = Object()
    private var thread: Thread? = null
    private val pool = ThreadPoolExecutor(0, MAX_THREADS, 30, TimeUnit.SECONDS, SynchronousQueue(), ThreadPoolExecutor.CallerRunsPolicy())

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "QsRelay").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        runCatching { socket?.close() }
        wake()
    }

    /** Retry now instead of waiting out the backoff. */
    fun wake() = synchronized(lock) { lock.notifyAll() }

    private fun loop() {
        var backoff = MIN_BACKOFF_MS
        while (running) {
            val started = System.currentTimeMillis()
            try {
                session()
            } catch (e: Exception) {
                if (running) Log.i(TAG, "relay connection ended: $e")
            } finally {
                _connected.value = false
                runCatching { socket?.close() }
                socket = null
            }
            if (!running) break
            // A connection that held a while resets the backoff.
            if (System.currentTimeMillis() - started > STABLE_MS) backoff = MIN_BACKOFF_MS
            synchronized(lock) { if (running) lock.wait(backoff) }
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }

    private fun session() {
        val uri = URI(url)
        val secure = uri.scheme == "https" || uri.scheme == "wss"
        val host = uri.host
        val port = if (uri.port > 0) uri.port else if (secure) 443 else 80
        val s = if (secure) {
            ((SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(Socket().apply { connect(InetSocketAddress(host, port), CONNECT_MS) }, host, port, true) as SSLSocket).also {
                it.startHandshake()
                // A bare SSLSocket checks the chain but not the name.
                if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(host, it.session)) error("certificate is not for $host")
            }
        } else {
            Socket().apply { connect(InetSocketAddress(host, port), CONNECT_MS) }
        }
        socket = s
        s.soTimeout = READ_TIMEOUT_MS
        val input = BufferedInputStream(s.getInputStream())
        val output = s.getOutputStream()

        val nonce = Base64.getEncoder().encodeToString(ByteArray(16).also(random::nextBytes))
        val path = (uri.rawPath.trimEnd('/')) + "/car?id=" + carId
        val hostHeader = if (uri.port > 0) "$host:$port" else host
        output.write((
            "GET $path HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Key: $nonce\r\nSec-WebSocket-Version: 13\r\nx-relay-key: $key\r\n" +
                "User-Agent: QuickSettings\r\n\r\n"
            ).toByteArray())
        output.flush()
        val head = readHead(input)
        val status = head.firstOrNull()?.split(' ')?.getOrNull(1)
        if (status != "101") error("relay answered ${head.firstOrNull()}")
        val accept = head.drop(1).firstOrNull { it.lowercase().startsWith("sec-websocket-accept:") }?.substringAfter(':')?.trim()
        if (accept != acceptFor(nonce)) error("bad websocket accept")

        _connected.value = true
        Log.i(TAG, "relay connected")
        val pinger = Thread({
            try {
                while (running && !s.isClosed) {
                    Thread.sleep(PING_MS)
                    send(output, OP_TEXT, "ping".toByteArray())
                }
            } catch (e: Exception) {
                runCatching { s.close() }
            }
        }, "QsRelayPing").apply { isDaemon = true; start() }
        try {
            val message = ByteArrayOutputStream()
            while (running) {
                val b0 = input.readByte()
                val b1 = input.readByte()
                val op = b0 and 0x0F
                var len = (b1 and 0x7F).toLong()
                if (len == 126L) len = ((input.readByte() shl 8) or input.readByte()).toLong()
                else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or input.readByte().toLong() } }
                if (len > MAX_MESSAGE) error("message too large")
                val mask = if (b1 and 0x80 != 0) ByteArray(4).also { input.readFully(it) } else null
                val payload = ByteArray(len.toInt()).also { input.readFully(it) }
                if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
                when (op) {
                    OP_TEXT, OP_CONTINUATION -> {
                        message.write(payload)
                        if (message.size() > MAX_MESSAGE) error("message too large")
                        if (b0 and 0x80 != 0) {
                            val text = message.toString(Charsets.UTF_8.name())
                            message.reset()
                            if (text != "pong") pool.execute { answer(output, text) }
                        }
                    }
                    OP_PING -> send(output, OP_PONG, payload)
                    OP_CLOSE -> { runCatching { send(output, OP_CLOSE, ByteArray(0)) }; return }
                }
            }
        } finally {
            pinger.interrupt()
        }
    }

    private fun answer(output: OutputStream, text: String) {
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        val auth = msg.optString("authorization")
        val req = HttpRequest(
            method = msg.optString("method", "GET").uppercase(),
            path = msg.optString("path", "/"),
            query = PhoneServer.query(msg.optString("query")),
            headers = if (auth.isEmpty()) emptyMap() else mapOf("authorization" to auth),
            body = msg.optString("body"),
        )
        val res = try {
            handle(req)
        } catch (e: Exception) {
            Log.w(TAG, "relay request failed", e)
            HttpResponse(500, PhoneServer.TEXT, ByteArray(0))
        }
        val reply = JSONObject()
            .put("id", msg.opt("id"))
            .put("status", res.status)
            .put("type", res.contentType)
            .put("cache", res.headers["Cache-Control"] ?: "no-store")
            .put("body", Base64.getEncoder().encodeToString(res.body))
        runCatching { send(output, OP_TEXT, reply.toString().toByteArray()) }
    }

    /** One masked frame (a client must mask); one writer at a time. */
    private fun send(output: OutputStream, op: Int, payload: ByteArray) = synchronized(output) {
        val frame = ByteArrayOutputStream(payload.size + 14)
        frame.write(0x80 or op)
        when {
            payload.size < 126 -> frame.write(0x80 or payload.size)
            payload.size < 65536 -> { frame.write(0x80 or 126); frame.write(payload.size shr 8); frame.write(payload.size and 0xFF) }
            else -> { frame.write(0x80 or 127); for (i in 7 downTo 0) frame.write(((payload.size.toLong() shr (8 * i)) and 0xFF).toInt()) }
        }
        val mask = ByteArray(4).also(random::nextBytes)
        frame.write(mask)
        for (i in payload.indices) frame.write(payload[i].toInt() xor mask[i % 4].toInt())
        output.write(frame.toByteArray())
        output.flush()
    }

    companion object {
        private const val TAG = "QsRelay"
        private const val OP_CONTINUATION = 0
        private const val OP_TEXT = 1
        private const val OP_CLOSE = 8
        private const val OP_PING = 9
        private const val OP_PONG = 10
        private const val CONNECT_MS = 10_000
        // A ping goes out every 30 s and the relay answers it, so 75 s of silence is a dead link.
        private const val PING_MS = 30_000L
        private const val READ_TIMEOUT_MS = 75_000
        private const val MIN_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 5 * 60_000L
        private const val STABLE_MS = 60_000L
        private const val MAX_MESSAGE = 1 shl 20
        private const val MAX_THREADS = 4
        private val random = SecureRandom()

        internal fun acceptFor(nonce: String): String =
            Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((nonce + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray()),
            )

        private fun readHead(input: InputStream): List<String> {
            val head = ByteArrayOutputStream()
            var last4 = 0
            while (true) {
                val b = input.read()
                if (b < 0) throw EOFException()
                head.write(b)
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) break
                if (head.size() > 16 * 1024) error("header too large")
            }
            return head.toString(Charsets.UTF_8.name()).split("\r\n")
        }

        private fun InputStream.readByte(): Int = read().also { if (it < 0) throw EOFException() }

        private fun InputStream.readFully(buf: ByteArray) {
            var got = 0
            while (got < buf.size) {
                val n = read(buf, got, buf.size - got)
                if (n < 0) throw EOFException()
                got += n
            }
        }
    }
}
