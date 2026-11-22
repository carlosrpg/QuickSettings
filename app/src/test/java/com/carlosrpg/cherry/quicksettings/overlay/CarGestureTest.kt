package com.carlosrpg.cherry.quicksettings.overlay

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CarGestureTest {
    @Test
    fun `gesture codes read as fingers and kind`() {
        assertThat(CarGesture.name(CarGesture.TWO_FINGER_SWIPE_DOWN)).isEqualTo("two-finger swipe down (202)")
        assertThat(CarGesture.name(204)).isEqualTo("two-finger pinch (204)")
        assertThat(CarGesture.name(305)).isEqualTo("three-finger long press (305)")
        assertThat(CarGesture.name(7)).isEqualTo("unknown (7)")
    }
}
