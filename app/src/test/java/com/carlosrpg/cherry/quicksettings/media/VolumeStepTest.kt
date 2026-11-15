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
        delay(300 + 3 * 100 + 400)
        assertThat(sent).containsExactly(11, 12, 13).inOrder()
        assertThat(stepper.moving).isFalse()
        thread.close()
    }
}
