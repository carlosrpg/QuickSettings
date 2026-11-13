package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.RegenLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class CarRepositoryTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun `seeded mock car decodes through the library codecs`() = runBlocking {
        val repo = CarRepository(seededMockBus(), scope)
        repo.refresh()
        val s = repo.state.value
        assertThat(s.batterySoc).isEqualTo(72)
        assertThat(s.regen).isEqualTo(RegenLevel.MEDIUM)
        assertThat(s.batteryMode).isEqualTo(BatteryMode.SMART)
        assertThat(s.electricRangeKm).isEqualTo(58)
        assertThat(s.fuelRangeKm).isEqualTo(554)
        assertThat(s.fuelPercent).isEqualTo(45)
        assertThat(s.outsideTempC).isEqualTo(14)
        assertThat(s.autoHold).isTrue()
        assertThat(s.laneKeep).isFalse()
    }

    @Test
    fun `temperature and fan writes read back`() = runBlocking {
        val repo = CarRepository(seededMockBus(), scope)
        repo.refresh()
        repo.setCabinTemp(26)
        repo.setFan(7)
        // Temperature, then fan: one after the other, each watched for the car's panel.
        delay(2 * CarRepository.CLIMATE_WRITE_MS + CarRepository.WRITE_SETTLE_MS + 400)
        repo.refresh()
        assertThat(repo.state.value.cabinTempC).isEqualTo(26)
        assertThat(repo.state.value.fanLevel).isEqualTo(7)
    }

    @Test
    fun `nothing is published until the vendor service connects`() = runBlocking {
        val zeros = object : CarBus {
            override fun sendItemValue(module: Int, id: Int, value: Int) = Unit
            override fun sendItemValues(module: Int, id: Int, values: IntArray) = Unit
            override fun getItemValue(module: Int, id: Int) = 0
            override fun isServiceConnected() = false
        }
        val repo = CarRepository(zeros, scope)
        repo.refresh()
        // A 0 would otherwise decode as regen High and battery mode Initial.
        assertThat(repo.state.value).isEqualTo(CarState())
    }
}
