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

    @Test
    fun `recent apps keep the newest first, once each, capped`() {
        assertThat(bumpRecent(listOf("a", "b", "c"), "c", keep = 3)).containsExactly("c", "a", "b").inOrder()
        assertThat(bumpRecent(listOf("a", "b", "c"), "d", keep = 3)).containsExactly("d", "a", "b").inOrder()
        assertThat(bumpRecent(emptyList(), "a", keep = 3)).containsExactly("a")
    }

    @Test
    fun `phone projections open their head unit app`() {
        assertThat(CarMediaSource.ANDROID_AUTO.appPackage()).isEqualTo("com.google.android.projection.sink")
        assertThat(CarMediaSource.CARPLAY.appPackage()).isEqualTo("com.desaysv.vehicle.carplayapp")
        assertThat(CarMediaSource.RADIO.appPackage()).isNull()
    }
}
