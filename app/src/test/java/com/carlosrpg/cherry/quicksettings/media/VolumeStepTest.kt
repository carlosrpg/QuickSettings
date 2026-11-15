package com.carlosrpg.cherry.quicksettings.media

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test

class VolumeStepTest {
    @Test
    fun `a volume jump goes out as single steps, debounced to the last target`() = runBlocking {
        var level = 10
        val sent = mutableListOf<Int>()
        val thread = newSingleThreadContext("volume")
        val stepper = LevelStepper(CoroutineScope(thread), read = { level }, step = { level += it; sent += level })
        withContext(thread) {
            stepper.moveTo(20)
            stepper.moveTo(7)
            stepper.moveTo(13)
        }
        // Wait for the move to end rather than a fixed sleep (slow CI runners).
        val deadline = System.currentTimeMillis() + 10_000
        do {
            delay(50)
            check(System.currentTimeMillis() < deadline) { "volume move did not finish within 10 s" }
        } while (withContext(thread) { stepper.moving })
        assertThat(sent).containsExactly(11, 12, 13).inOrder()
        thread.close()
    }
}
