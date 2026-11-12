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
    fun `media drag angle picks volume or track`() {
        fun guide(x: Float, y: Float, adjustable: Boolean = true, previous: Hud.Media? = null) =
            mediaGuide(Offset(x, y), startVolume = 10, max = 15, adjustable = adjustable,
                volumeStepPx = 16f, trackThresholdPx = 64f, previous = previous)

        // Steep, even well off vertical (about 63°): volume only.
        val up = guide(25f, -50f)
        assertThat(up.volume).isEqualTo(13)
        assertThat(up.volumeDirection).isEqualTo(1)
        assertThat(up.trackProgress).isEqualTo(0f)
        // A true diagonal (45°) is volume now, not a skip.
        val diagonal = guide(80f, 80f)
        assertThat(diagonal.volume).isEqualTo(5)
        assertThat(diagonal.trackArmed).isFalse()
        // Shallow (about 14°) and past the threshold: the skip arms, volume stays put.
        val skip = guide(-80f, 20f, previous = up)
        assertThat(skip.trackDirection).isEqualTo(-1)
        assertThat(skip.trackArmed).isTrue()
        assertThat(skip.volume).isEqualTo(13)
        assertThat(skip.volumeDirection).isEqualTo(0)
        // Clamped at the top; fixed-volume builds never change it.
        assertThat(guide(0f, -400f).volume).isEqualTo(15)
        assertThat(guide(0f, -400f, adjustable = false).volume).isEqualTo(10)
    }

    @Test
    fun `landscape packs the approved four column grid`() {
        val rows = packRows(listOf(MEDIA, NAVIGATION, ASSIST, CLIMATE, CAR_MODE, AUTONOMY), columns = 4)
        assertThat(rows).containsExactly(
            listOf(MEDIA, NAVIGATION, ASSIST),
            listOf(CLIMATE, CAR_MODE, AUTONOMY),
        ).inOrder()
    }

    @Test
    fun `portrait gives wide cards their own row`() {
        val rows = packRows(listOf(MEDIA, NAVIGATION, ASSIST, CLIMATE, CAR_MODE, AUTONOMY), columns = 2)
        assertThat(rows).containsExactly(
            listOf(MEDIA), listOf(NAVIGATION, ASSIST), listOf(CLIMATE, CAR_MODE), listOf(AUTONOMY),
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
}
