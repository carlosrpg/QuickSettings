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
    fun `media volume follows vertical travel while a skip arms independently`() {
        fun guide(x: Float, y: Float, adjustable: Boolean = true) =
            mediaGuide(Offset(x, y), startVolume = 10, max = 15, adjustable = adjustable, volumeStepPx = 16f, trackThresholdPx = 64f)

        // Up three steps and a little sideways: volume rises, no skip armed.
        val up = guide(20f, -50f)
        assertThat(up.volume).isEqualTo(13)
        assertThat(up.volumeDirection).isEqualTo(1)
        assertThat(up.trackArmed).isFalse()
        // Diagonal: both at once, the skip is armed but only applied on release by the caller.
        val both = guide(-70f, 40f)
        assertThat(both.volume).isEqualTo(8)
        assertThat(both.trackDirection).isEqualTo(-1)
        assertThat(both.trackArmed).isTrue()
        // Clamped at the top; fixed-volume builds never change it.
        assertThat(guide(0f, -400f).volume).isEqualTo(15)
        assertThat(guide(0f, -400f, adjustable = false).volume).isEqualTo(10)
        assertThat(guide(0f, -400f, adjustable = false).volumeDirection).isEqualTo(0)
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
