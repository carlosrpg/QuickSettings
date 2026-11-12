package com.carlosrpg.cherry.quicksettings.car

import com.carlosrpg.cherry.quicksettings.car.CarItems.BatteryMode
import com.carlosrpg.cherry.quicksettings.car.CarItems.Regen
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CarItemsTest {
    @Test
    fun `regen status is inverted from the command`() {
        assertThat(Regen.fromStatus(0)).isEqualTo(Regen.HIGH)
        assertThat(Regen.fromStatus(1)).isEqualTo(Regen.MEDIUM)
        assertThat(Regen.fromStatus(2)).isEqualTo(Regen.LOW)
        assertThat(Regen.fromStatus(7)).isEqualTo(Regen.LOW)
        assertThat(Regen.fromStatus(255)).isNull()
        assertThat(Regen.fromStatus(null)).isNull()
        assertThat(Regen.entries.map { it.command }).containsExactly(1, 2, 3).inOrder()
    }

    @Test
    fun `battery mode statuses map like the stock settings`() {
        assertThat(BatteryMode.fromStatus(0)).isEqualTo(BatteryMode.INITIAL)
        assertThat(BatteryMode.fromStatus(255)).isEqualTo(BatteryMode.INITIAL)
        assertThat(BatteryMode.fromStatus(1)).isEqualTo(BatteryMode.SMART)
        assertThat(BatteryMode.fromStatus(3)).isEqualTo(BatteryMode.FORCED)
        assertThat(BatteryMode.fromStatus(2)).isNull()
        assertThat(BatteryMode.entries.map { it.command }).containsExactly(1, 2, 4).inOrder()
    }

    @Test
    fun `ranges are two byte big endian with sentinels`() {
        assertThat(CarItems.fuelRangeKm(intArrayOf(0x02, 0x2A))).isEqualTo(554)
        assertThat(CarItems.fuelRangeKm(intArrayOf(0xF0, 0x00))).isNull()
        assertThat(CarItems.fuelRangeKm(intArrayOf(0, 0))).isNull()
        assertThat(CarItems.fuelRangeKm(intArrayOf(1))).isNull()
        assertThat(CarItems.electricRangeKm(intArrayOf(0, 58))).isEqualTo(58)
        assertThat(CarItems.electricRangeKm(intArrayOf(0x03, 0xFF))).isNull()
        // Signed bytes from the bus must not go negative.
        assertThat(CarItems.be16(intArrayOf(-1, -1))).isEqualTo(0xFFFF)
    }

    @Test
    fun `fuel percent takes a plausible first byte`() {
        assertThat(CarItems.fuelPercent(intArrayOf(45), null)).isEqualTo(45)
        assertThat(CarItems.fuelPercent(IntArray(0), 60)).isEqualTo(60)
        assertThat(CarItems.fuelPercent(intArrayOf(200), null)).isNull()
    }
}
