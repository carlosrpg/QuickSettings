package com.carlosrpg.cherry.quicksettings.phone

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class PhoneRegistryTest {
    private var stored: String? = null
    private var now = 1_000_000L
    private fun registry() = PhoneRegistry(load = { stored }, save = { stored = it }, clock = { now })

    /** Pairs once, answering [allow] on the head unit as soon as the request shows. */
    private fun pair(r: PhoneRegistry, allow: Boolean?, code: String = r.newCode().code, timeout: Long = 2_000) = runBlocking {
        val result = async { r.pair(code, " Ana's phone ", timeout) }
        if (allow != null) {
            while (r.request.value == null) delay(5)
            assertThat(r.request.value!!.name).isEqualTo("Ana's phone")
            r.answer(allow)
        }
        result.await()
    }

    @Test
    fun `allowed on the car, the phone gets a token that identifies it`() {
        val r = registry()
        val result = pair(r, allow = true) as PairResult.Paired
        assertThat(r.phones.value.map { it.name }).containsExactly("Ana's phone")
        assertThat(r.phoneFor(result.token)?.name).isEqualTo("Ana's phone")
        assertThat(r.phoneFor("not-a-token")).isNull()
        // Only the hash is stored.
        assertThat(stored).doesNotContain(result.token)
    }

    @Test
    fun `denied, unanswered, wrong, used and expired codes add nothing`() {
        val r = registry()
        assertThat(pair(r, allow = false)).isEqualTo(PairResult.Refused("DENIED"))
        assertThat(pair(r, allow = null, timeout = 50)).isEqualTo(PairResult.Refused("TIMEOUT"))
        r.newCode()
        assertThat(pair(r, allow = null, code = "guess")).isEqualTo(PairResult.Refused("BAD_CODE"))
        // A code takes one attempt, whatever the answer.
        val used = r.newCode().code
        pair(r, allow = false, code = used)
        assertThat(pair(r, allow = null, code = used)).isEqualTo(PairResult.Refused("BAD_CODE"))
        val expired = r.newCode().code
        now += PhoneRegistry.CODE_VALID_MS
        assertThat(pair(r, allow = null, code = expired)).isEqualTo(PairResult.Refused("BAD_CODE"))
        assertThat(r.phones.value).isEmpty()
        assertThat(r.request.value).isNull()
    }

    @Test
    fun `a removed phone's token stops working, and the list survives a restart`() {
        val r = registry()
        val keep = pair(r, allow = true) as PairResult.Paired
        val drop = pair(r, allow = true) as PairResult.Paired
        r.remove(r.phoneFor(drop.token)!!.id)
        assertThat(r.phoneFor(drop.token)).isNull()
        val restarted = registry()
        assertThat(restarted.carId).isEqualTo(r.carId)
        assertThat(restarted.phoneFor(keep.token)).isNotNull()
        assertThat(restarted.phoneFor(drop.token)).isNull()
    }

    @Test
    fun `the six-digit pin pairs like the QR code, and a wrong guess burns the code`() {
        val r = registry()
        val code = r.newCode()
        assertThat(code.pin).matches("[0-9]{6}")
        assertThat(pair(r, allow = true, code = code.pin)).isInstanceOf(PairResult.Paired::class.java)

        val next = r.newCode()
        val wrong = if (next.pin == "000000") "000001" else "000000"
        assertThat(pair(r, allow = null, code = wrong)).isEqualTo(PairResult.Refused("BAD_CODE"))
        assertThat(pair(r, allow = null, code = next.pin)).isEqualTo(PairResult.Refused("BAD_CODE"))
        assertThat(r.phones.value).hasSize(1)
    }
}
