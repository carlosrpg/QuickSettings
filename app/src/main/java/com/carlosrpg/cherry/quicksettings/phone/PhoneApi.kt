package com.carlosrpg.cherry.quicksettings.phone

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** What the phones can see and do; the car app supplies it. */
interface PhoneBackend {
    /** The state document of docs/companion-api.md, without `path`, `asOfMs` and `permissions`. */
    fun state(): JSONObject

    /**
     * Runs one command; `{ok: true}` or `{ok: false, blocked: <reason>}`. [remote]: it came through
     * the relay and the car doesn't allow driver assist from there.
     */
    suspend fun command(action: String, args: JSONObject, remote: Boolean): JSONObject

    /** PNG bytes of an image the state referred to as `/img/<key>`, or null. */
    fun image(key: String): ByteArray?
}

/**
 * The routes of docs/companion-api.md on top of [PhoneServer]: ping, pairing, state, commands,
 * images and the page itself. Every route but ping, pairing and the page needs a paired phone's
 * token. Commands run on [commandContext] (the main thread on the car, where the drawer runs them).
 */
class PhoneApi(
    private val registry: PhoneRegistry,
    private val backend: PhoneBackend,
    private val asset: (String) -> ByteArray?,
    private val commandContext: CoroutineContext,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Called when a phone starts watching after none did: the car's polling must wake up. */
    private val onWatch: () -> Unit = {},
    /** Settings → Phones → driver assist over the internet (off by default). */
    private val remoteAssist: () -> Boolean = { false },
) {
    /** [clock] time of the last state request; the overlay keeps polling the car while it is recent. */
    @Volatile var lastStateAt = 0L
        private set

    fun watching(now: Long = clock()) = now - lastStateAt < WATCH_MS

    private val rate = HashMap<String, Pair<Long, Int>>()

    /** [remote]: the request came through the relay, not from the car's own network. */
    fun handle(req: HttpRequest, remote: Boolean = false): HttpResponse = when {
        req.path == "/api/ping" -> json(200, JSONObject().put("carId", registry.carId).put("online", true)
            .put("lastSeenMs", clock()).put("path", if (remote) "relay" else "lan"))
        // Pairing needs someone at the car anyway, so it stays on the car's network.
        req.path == "/api/pair" && remote -> json(403, JSONObject().put("error", "LAN_ONLY"))
        req.path == "/api/pair" && req.method == "POST" -> pair(req)
        req.path == "/api/state" -> authed(req) { _ ->
            val first = !watching()
            lastStateAt = clock()
            if (first) onWatch()
            json(200, backend.state().put("path", if (remote) "relay" else "lan").put("asOfMs", clock())
                .put("permissions", JSONObject().put("assist", !remote || remoteAssist())))
        }
        req.path == "/api/cmd" && req.method == "POST" -> authed(req) { phone ->
            if (!allow(phone.id)) return@authed json(429, JSONObject().put("ok", false).put("blocked", "RATE_LIMIT"))
            val body = parse(req.body) ?: return@authed json(400, JSONObject().put("error", "BAD_JSON"))
            val action = body.optString("action")
            val args = body.optJSONObject("args") ?: JSONObject()
            json(200, runBlocking(commandContext) { backend.command(action, args, remote = remote && !remoteAssist()) })
        }
        req.path.startsWith("/img/") -> authed(req) { _ ->
            backend.image(req.path.removePrefix("/img/"))
                ?.let { HttpResponse(200, "image/png", it, mapOf("Cache-Control" to "private, max-age=86400")) }
                ?: HttpResponse(404, PhoneServer.TEXT, ByteArray(0))
        }
        req.method == "GET" -> page(req.path)
        else -> HttpResponse(404, PhoneServer.TEXT, "not found".toByteArray())
    }

    private fun pair(req: HttpRequest): HttpResponse {
        val body = parse(req.body) ?: return json(400, JSONObject().put("error", "BAD_JSON"))
        return when (val result = runBlocking { registry.pair(body.optString("code"), body.optString("name")) }) {
            is PairResult.Paired -> json(200, JSONObject().put("token", result.token).put("carId", registry.carId))
            is PairResult.Refused -> json(403, JSONObject().put("error", result.reason))
        }
    }

    /** The bearer token, or `?t=` for images (an `<img>` can't send a header). */
    private inline fun authed(req: HttpRequest, block: (Phone) -> HttpResponse): HttpResponse {
        val token = req.headers["authorization"]?.removePrefix("Bearer ")?.trim() ?: req.query["t"]
        val phone = token?.let(registry::phoneFor) ?: return json(401, JSONObject().put("error", "UNPAIRED"))
        return block(phone)
    }

    /** [MAX_COMMANDS_PER_SECOND] per phone. */
    @Synchronized
    private fun allow(phoneId: String): Boolean {
        val second = clock() / 1000
        val (at, count) = rate[phoneId] ?: (second to 0)
        val next = if (at == second) count + 1 else 1
        rate[phoneId] = second to next
        return next <= MAX_COMMANDS_PER_SECOND
    }

    private fun page(path: String): HttpResponse {
        val name = if (path == "/" || path.isEmpty()) "index.html" else path.removePrefix("/")
        // Only plain file names from assets/web: no way up out of the folder.
        if (!name.matches(Regex("[A-Za-z0-9_-]+\\.(html|js|css|svg|png|json)"))) return HttpResponse(404, PhoneServer.TEXT, ByteArray(0))
        val bytes = asset("web/$name") ?: return HttpResponse(404, PhoneServer.TEXT, "not found".toByteArray())
        val type = when (name.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            else -> "application/json"
        }
        return HttpResponse(200, type, bytes, mapOf("Cache-Control" to "no-cache"))
    }

    private fun parse(body: String) = runCatching { JSONObject(body) }.getOrNull()

    private fun json(status: Int, o: JSONObject) =
        HttpResponse(status, "application/json; charset=utf-8", o.toString().toByteArray(), mapOf("Cache-Control" to "no-store"))

    companion object {
        const val PORT = 8080
        /** A phone polls every 2 s while its page shows; a few missed polls and it counts as gone. */
        const val WATCH_MS = 10_000L
        const val MAX_COMMANDS_PER_SECOND = 10
    }
}
