package com.carlosrpg.cherry.quicksettings.phone

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** A phone the car has accepted. Only the hash of its token is kept. */
data class Phone(val id: String, val name: String, val tokenHash: String, val addedAt: Long, val lastUsedAt: Long)

/** The QR code's one-time code, valid until [expiresAt] and for one pairing attempt. */
/** [code] goes in the QR; [pin] is the same code typed in the companion app. */
data class PairingCode(val code: String, val pin: String, val expiresAt: Long)

/** A phone asking to pair, waiting for someone in the car to answer on the head unit. */
class PairRequest(val name: String) {
    internal val answer = CompletableDeferred<Boolean>()
}

sealed interface PairResult {
    data class Paired(val token: String) : PairResult
    /** BAD_CODE (wrong, used or expired), DENIED, or TIMEOUT (nobody answered on the car). */
    data class Refused(val reason: String) : PairResult
}

/**
 * Who may use the car from a phone: the paired phones, the car's id and the pairing in progress.
 * Only someone at the head unit can add a phone (Allow on [request]) or remove one. [load] and
 * [save] hold one JSON document (SharedPreferences on the car).
 */
class PhoneRegistry(
    private val load: () -> String?,
    private val save: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val random = SecureRandom()
    val carId: String
    private val _phones: MutableStateFlow<List<Phone>>
    val phones: StateFlow<List<Phone>> get() = _phones.asStateFlow()

    private val _code = MutableStateFlow<PairingCode?>(null)
    val code: StateFlow<PairingCode?> = _code.asStateFlow()

    private val _request = MutableStateFlow<PairRequest?>(null)
    val request: StateFlow<PairRequest?> = _request.asStateFlow()

    init {
        val saved = load()?.let { runCatching { JSONObject(it) }.getOrNull() }
        carId = saved?.optString("carId")?.takeIf { it.isNotEmpty() } ?: randomId(9)
        _phones = MutableStateFlow(saved?.optJSONArray("phones")?.let(::decode).orEmpty())
        if (saved == null) persist()
    }

    /** A fresh code for the QR; any earlier one stops working. */
    fun newCode(): PairingCode =
        PairingCode(randomId(16), "%06d".format(random.nextInt(1_000_000)), clock() + CODE_VALID_MS).also { _code.value = it }

    fun cancelCode() {
        _code.value = null
    }

    /**
     * Takes the code (one attempt, right or wrong answer on the car), asks the head unit and, on
     * Allow, adds the phone and returns its token. Waits up to [timeoutMs] for the answer.
     */
    suspend fun pair(code: String, name: String, timeoutMs: Long = ANSWER_TIMEOUT_MS): PairResult {
        val expected = _code.value
        val valid = expected != null && clock() < expected.expiresAt &&
            (MessageDigest.isEqual(expected.code.toByteArray(), code.toByteArray()) ||
                MessageDigest.isEqual(expected.pin.toByteArray(), code.toByteArray()))
        // One attempt per code, right or wrong: the six-digit PIN can't be guessed by trying.
        if (expected != null) _code.compareAndSet(expected, null)
        if (!valid) return PairResult.Refused("BAD_CODE")
        val request = PairRequest(name.trim().take(MAX_NAME).ifEmpty { "Phone" })
        _request.value = request
        val allowed = try {
            withTimeoutOrNull(timeoutMs) { request.answer.await() }
        } finally {
            _request.compareAndSet(request, null)
        }
        return when (allowed) {
            null -> PairResult.Refused("TIMEOUT")
            false -> PairResult.Refused("DENIED")
            true -> {
                val token = randomId(32)
                val now = clock()
                _phones.value = _phones.value + Phone(randomId(6), request.name, hash(token), now, now)
                persist()
                PairResult.Paired(token)
            }
        }
    }

    /** The head unit's Allow or Deny for the phone waiting in [request]. */
    fun answer(allow: Boolean) {
        _request.value?.answer?.complete(allow)
    }

    /** The phone holding [token], or null (never paired, or removed). Marks it used. */
    fun phoneFor(token: String): Phone? {
        val h = hash(token)
        val phone = _phones.value.firstOrNull { MessageDigest.isEqual(it.tokenHash.toByteArray(), h.toByteArray()) } ?: return null
        val now = clock()
        // Saved at most every few minutes: every request would otherwise write the preferences.
        if (now - phone.lastUsedAt > LAST_USED_SAVE_MS) {
            _phones.value = _phones.value.map { if (it.id == phone.id) it.copy(lastUsedAt = now) else it }
            persist()
        }
        return phone
    }

    fun remove(id: String) {
        _phones.value = _phones.value.filterNot { it.id == id }
        persist()
    }

    private fun persist() {
        val list = JSONArray()
        _phones.value.forEach {
            list.put(JSONObject().put("id", it.id).put("name", it.name).put("tokenHash", it.tokenHash)
                .put("addedAt", it.addedAt).put("lastUsedAt", it.lastUsedAt))
        }
        save(JSONObject().put("carId", carId).put("phones", list).toString())
    }

    private fun decode(list: JSONArray) = (0 until list.length()).mapNotNull { i ->
        val o = list.optJSONObject(i) ?: return@mapNotNull null
        Phone(o.optString("id"), o.optString("name"), o.optString("tokenHash"), o.optLong("addedAt"), o.optLong("lastUsedAt"))
            .takeIf { it.id.isNotEmpty() && it.tokenHash.isNotEmpty() }
    }

    private fun randomId(bytes: Int): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    companion object {
        const val CODE_VALID_MS = 5 * 60_000L
        const val ANSWER_TIMEOUT_MS = 60_000L
        private const val LAST_USED_SAVE_MS = 5 * 60_000L
        private const val MAX_NAME = 40

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
