package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.ui.geometry.Offset
import com.carlosrpg.cherry.quicksettings.settings.SettingsStore
import com.carlosrpg.cherry.quicksettings.settings.WidgetType
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.ASSIST
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.AUTONOMY
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.CAR_MODE
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.CLIMATE
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.MEDIA
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.NAVIGATION
import com.carlosrpg.cherry.quicksettings.settings.WidgetType.WEATHER
import com.carlosrpg.cherry.quicksettings.settings.addWeatherBesideRange
import com.carlosrpg.cherry.quicksettings.media.youtubeSearchUrl
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LayoutLogicTest {
    @Test
    fun `drag locks to the first clear axis and counts whole steps`() {
        val steps = DragSteps(lockPx = 24f, stepPx = 16f)
        assertThat(steps.steps(Offset(5f, -10f))).isEqualTo(0)
        assertThat(steps.axis).isNull()
        assertThat(steps.steps(Offset(4f, -40f))).isEqualTo(-2)
        assertThat(steps.axis).isEqualTo(Axis.VERTICAL)
        // Horizontal movement after the lock is ignored.
        assertThat(steps.steps(Offset(200f, -40f))).isEqualTo(-2)
    }

    @Test
    fun `media zones follow the zone map`() {
        fun zone(x: Float, y: Float) = mediaZone(Offset(x, y), deadPx = 16f, bandHalfPx = 64f, sidePx = 56f)

        // Dead band in the centre column.
        assertThat(zone(0f, 0f)).isEqualTo(MediaZone.NONE)
        assertThat(zone(30f, -10f)).isEqualTo(MediaZone.NONE)
        // Slow volume just above / below the dead band, fast beyond the band, at any width.
        assertThat(zone(10f, -30f)).isEqualTo(MediaZone.VOLUME_UP)
        assertThat(zone(-20f, 40f)).isEqualTo(MediaZone.VOLUME_DOWN)
        assertThat(zone(120f, -80f)).isEqualTo(MediaZone.VOLUME_UP_FAST)
        assertThat(zone(-120f, 90f)).isEqualTo(MediaZone.VOLUME_DOWN_FAST)
        // Sides inside the band are tracks, even a little off the centre line.
        assertThat(zone(70f, -40f)).isEqualTo(MediaZone.NEXT)
        assertThat(zone(-60f, 30f)).isEqualTo(MediaZone.PREVIOUS)
        assertThat(MediaZone.VOLUME_UP_FAST.stepsPerSecond).isEqualTo(2)
        assertThat(MediaZone.VOLUME_DOWN.stepsPerSecond).isEqualTo(1)
    }

    @Test
    fun `landscape packs the approved four column grid`() {
        val rows = packRows(listOf(MEDIA, NAVIGATION, ASSIST, CLIMATE, CAR_MODE, AUTONOMY, WEATHER), columns = 4)
        assertThat(rows).containsExactly(
            listOf(MEDIA, NAVIGATION, ASSIST),
            listOf(CLIMATE, CAR_MODE, AUTONOMY, WEATHER),
        ).inOrder()
    }

    @Test
    fun `portrait gives wide cards their own row`() {
        val rows = packRows(listOf(MEDIA, NAVIGATION, ASSIST, CLIMATE, CAR_MODE, AUTONOMY, WEATHER), columns = 2)
        assertThat(rows).containsExactly(
            listOf(MEDIA), listOf(NAVIGATION, ASSIST), listOf(CLIMATE, CAR_MODE), listOf(AUTONOMY, WEATHER),
        ).inOrder()
        assertThat(packRows(emptyList(), 4)).isEmpty()
    }

    @Test
    fun `widget lists round trip and drop unknown names`() {
        val list = listOf(NAVIGATION, MEDIA)
        assertThat(SettingsStore.decode(SettingsStore.encode(list))).isEqualTo(list)
        assertThat(SettingsStore.decode("MEDIA,BOGUS,MEDIA")).containsExactly(MEDIA)
        assertThat(SettingsStore.decode(null)).isNull()
        assertThat(SettingsStore.decode(SettingsStore.encode(emptyList<WidgetType>()))).isEmpty()
    }

    @Test
    fun `a hidden widget keeps its place in the settings order`() {
        // Hiding CLIMATE leaves it between MEDIA and CAR_MODE; an order saved before a widget
        // existed gets that widget appended.
        val shown = listOf(MEDIA, CAR_MODE)
        assertThat(com.carlosrpg.cherry.quicksettings.settings.withHidden(listOf(MEDIA, CLIMATE, CAR_MODE)))
            .containsExactly(MEDIA, CLIMATE, CAR_MODE, AUTONOMY, NAVIGATION, ASSIST, WEATHER).inOrder()
        assertThat(listOf(MEDIA, CLIMATE, CAR_MODE).filter { it in shown }).containsExactly(MEDIA, CAR_MODE).inOrder()
    }

    @Test
    fun `a layout saved before weather gets it beside the range card`() {
        assertThat(addWeatherBesideRange(listOf(MEDIA, AUTONOMY, ASSIST))).containsExactly(MEDIA, AUTONOMY, WEATHER, ASSIST).inOrder()
        assertThat(addWeatherBesideRange(listOf(MEDIA, ASSIST))).containsExactly(MEDIA, ASSIST).inOrder()
        assertThat(addWeatherBesideRange(listOf(WEATHER, AUTONOMY))).containsExactly(WEATHER, AUTONOMY).inOrder()
    }

    @Test
    fun `swipe closes only toward the drawer's origin`() {
        // Left drawer: swipe left closes; right or up does not; a mostly diagonal drag does not.
        assertThat(isCloseSwipe(Offset(-120f, 20f), -1f, 0f, 96f)).isTrue()
        assertThat(isCloseSwipe(Offset(120f, 0f), -1f, 0f, 96f)).isFalse()
        assertThat(isCloseSwipe(Offset(-120f, 80f), -1f, 0f, 96f)).isFalse()
        assertThat(isCloseSwipe(Offset(-60f, 0f), -1f, 0f, 96f)).isFalse()
        // From the top: swipe up closes, down does not.
        assertThat(isCloseSwipe(Offset(10f, -150f), 0f, -1f, 96f)).isTrue()
        assertThat(isCloseSwipe(Offset(0f, 150f), 0f, -1f, 96f)).isFalse()
    }

    @Test
    fun `youtube apps search through their own page`() {
        assertThat(youtubeSearchUrl("com.google.android.youtube", "lo fi beats"))
            .isEqualTo("https://www.youtube.com/results?search_query=lo+fi+beats")
        assertThat(youtubeSearchUrl("app.morphe.android.apps.youtube.music", "Café"))
            .isEqualTo("https://music.youtube.com/search?q=Caf%C3%A9")
        assertThat(youtubeSearchUrl("app.morphe.android.youtube", "x")).startsWith("https://www.youtube.com/")
        assertThat(youtubeSearchUrl("com.spotify.music", "x")).isNull()
        assertThat(youtubeSearchUrl(null, "x")).isNull()
    }
}
