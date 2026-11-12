package com.carlosrpg.cherry.quicksettings.media

import app.jconfig.carbus.media.CarMediaSource
import app.jconfig.carbus.media.CarMediaState
import app.jconfig.carbus.media.CarPlayStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MediaSourceRuleTest {
    private val bluetooth = CarMediaState(13, CarMediaSource.BLUETOOTH, null, CarPlayStatus.PAUSED, 0, 0)

    @Test
    fun `an Android app that is playing beats the car source`() {
        assertThat(showCarSource(sessionPlaying = true, car = bluetooth)).isFalse()
    }

    @Test
    fun `the car source shows whenever no app is playing, even paused`() {
        assertThat(showCarSource(sessionPlaying = false, car = bluetooth)).isTrue()
        assertThat(showCarSource(sessionPlaying = false, car = null)).isFalse()
    }

    @Test
    fun `every car source has a label`() {
        assertThat(CarMediaSource.entries.map { it.labelRes() }.toSet()).hasSize(CarMediaSource.entries.size)
    }
}
