package com.carlosrpg.cherry.quicksettings.phone

import android.util.Log
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

data class HttpRequest(
    val method: String,
    val path: String,
    val query: Map<String, String>,
    /** Lower-case names. */
    val headers: Map<String, String>,
    val body: String,
)

class HttpResponse(val status: Int, val contentType: String, val body: ByteArray, val headers: Map<String, String> = emptyMap())

/**
 * A small HTTP/1.1 server for the phones on the car's Wi-Fi: one request per connection, a few
 * threads, small bodies. Only addresses on a local network (and loopback, for `adb forward`) are
 * served; anything arriving on a public address, such as a cellular modem's, is dropped.
 */
class PhoneServer(private val port: Int, private val handle: (HttpRequest) -> HttpResponse) {
    private var socket: ServerSocket? = null
    private val pool = ThreadPoolExecutor(0, MAX_THREADS, 30, TimeUnit.SECONDS, SynchronousQueue())
    private val acceptor = Executors.newSingleThreadExecutor()

    /** Opens the port; false when it can't be opened (in use). */
    fun start(): Boolean {
        val server = try {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(port))
            }
        } catch (e: Exception) {
            Log.w(TAG, "port $port not available", e)
            return false
        }
        socket = server
        acceptor.execute {
            while (!server.isClosed) {
                val client = try { server.accept() } catch (e: Exception) { break }
                if (!isLocal(client.inetAddress)) {
                    client.close()
                    continue
                }
                try {
                    pool.execute { serve(client) }
                } catch (e: RejectedExecutionException) {
                    client.close() // all threads busy (several pairings waiting for an answer)
                }
            }
        }
        return true
    }

    /** The port actually bound: [port], or a free one when that was 0 (tests). */
    val boundPort: Int get() = socket?.localPort ?: -1

    fun stop() {
        runCatching { socket?.close() }
        socket = null
        acceptor.shutdownNow()
        pool.shutdownNow()
    }

    private fun serve(client: Socket) = client.use { s ->
        s.soTimeout = READ_TIMEOUT_MS
        val response = try {
            val request = read(BufferedInputStream(s.getInputStream()))
            if (request == null) HttpResponse(400, TEXT, "bad request".toByteArray()) else handle(request)
        } catch (e: Exception) {
            Log.w(TAG, "request failed", e)
            HttpResponse(500, TEXT, "error".toByteArray())
        }
        runCatching {
            val out = s.getOutputStream()
            val head = buildString {
                append("HTTP/1.1 ${response.status} ${reason(response.status)}\r\n")
                append("Content-Type: ${response.contentType}\r\n")
                append("Content-Length: ${response.body.size}\r\n")
                append("Connection: close\r\n")
                response.headers.forEach { (k, v) -> append("$k: $v\r\n") }
                append("\r\n")
            }
            out.write(head.toByteArray())
            out.write(response.body)
            out.flush()
        }
    }

    companion object {
        private const val TAG = "QsPhoneServer"
        private const val MAX_THREADS = 8
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_HEADER = 16 * 1024
        private const val MAX_BODY = 64 * 1024
        const val TEXT = "text/plain; charset=utf-8"

        fun isLocal(a: InetAddress) = a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress

        internal fun read(input: InputStream): HttpRequest? {
            val head = ByteArrayOutputStream()
            var last4 = 0
            while (true) {
                val b = input.read()
                if (b < 0) return null
                head.write(b)
                last4 = (last4 shl 8) or b
                if (last4 == 0x0D0A0D0A) break
                if (head.size() > MAX_HEADER) return null
            }
            val lines = head.toString(Charsets.UTF_8.name()).split("\r\n")
            val parts = lines.first().split(' ')
            if (parts.size < 3) return null
            val headers = lines.drop(1).filter { ':' in it }
                .associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
            val length = headers["content-length"]?.toIntOrNull() ?: 0
            if (length < 0 || length > MAX_BODY) return null
            val body = ByteArray(length)
            var got = 0
            while (got < length) {
                val n = input.read(body, got, length - got)
                if (n < 0) return null
                got += n
            }
            val target = parts[1]
            return HttpRequest(parts[0].uppercase(), decode(target.substringBefore('?')), query(target.substringAfter('?', "")), headers, String(body, Charsets.UTF_8))
        }

        /** `a=1&b=2` to a map, decoded. */
        internal fun query(s: String): Map<String, String> = s.split('&').filter { '=' in it }.associate {
            decode(it.substringBefore('=')) to decode(it.substringAfter('='))
        }

        private fun decode(s: String) = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

        private fun reason(status: Int) = when (status) {
            200 -> "OK"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            429 -> "Too Many Requests"
            503 -> "Service Unavailable"
            else -> "Error"
        }
    }
}
