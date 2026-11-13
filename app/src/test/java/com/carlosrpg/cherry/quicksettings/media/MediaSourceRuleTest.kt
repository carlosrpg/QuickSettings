package com.carlosrpg.cherry.quicksettings.media

import app.jconfig.carbus.media.CarMediaSource
import app.jconfig.carbus.media.CarMediaState
import app.jconfig.carbus.media.CarPlayStatus
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class MediaSourceRuleTest {
    private val bluetooth = CarMediaState(13, CarMediaSource.BLUETOOTH, null, CarPlayStatus.PAUSED, 0, 0)

    private val playing = CarMediaState(13, CarMediaSource.BLUETOOTH, null, CarPlayStatus.PLAYING, 0, 0)

    @Test
    fun `whichever feed is playing wins, an app first`() {
        assertThat(showCarSource(sessionPresent = true, sessionPlaying = true, car = playing, carShown = true)).isFalse()
        assertThat(showCarSource(sessionPresent = true, sessionPlaying = false, car = playing, carShown = false)).isTrue()
    }

    @Test
    fun `pausing an app keeps the app on screen`() {
        // The bug: an app paused from the drawer was replaced by an idle car source.
        assertThat(showCarSource(sessionPresent = true, sessionPlaying = false, car = bluetooth, carShown = false)).isFalse()
        assertThat(showCarSource(sessionPresent = true, sessionPlaying = false, car = bluetooth, carShown = true)).isTrue()
    }

    @Test
    fun `a car source shows when there is no app session, and never without a source`() {
        assertThat(showCarSource(sessionPresent = false, sessionPlaying = false, car = bluetooth, carShown = false)).isTrue()
        assertThat(showCarSource(sessionPresent = true, sessionPlaying = false, car = null, carShown = true)).isFalse()
    }

    @Test
    fun `every car source has a label`() {
        assertThat(CarMediaSource.entries.map { it.labelRes() }.toSet()).hasSize(CarMediaSource.entries.size)
    }
}
