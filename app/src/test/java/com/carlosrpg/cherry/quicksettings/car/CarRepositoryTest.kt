package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.RegenLevel
import app.jconfig.carbus.carinfo.TelemetryIds
import app.jconfig.carbus.carinfo.VDEventCarInfo
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
        // Debounce, four single steps each (side by side), the panel watch, then a read back.
        awaitMoveDone(repo)
        repo.refresh()
        assertThat(repo.state.value.cabinTempC).isEqualTo(26)
        assertThat(repo.state.value.fanLevel).isEqualTo(7)
    }

    @Test
    fun `the passenger zone moves on its own item and leaves the driver's alone`() = runBlocking {
        val repo = CarRepository(seededMockBus(), scope)
        repo.refresh()
        val driver = repo.state.value.cabinTempC
        assertThat(repo.state.value.sync).isFalse()
        repo.setPassengerTemp(19)
        awaitMoveDone(repo)
        repo.refresh()
        assertThat(repo.state.value.passengerTempC).isEqualTo(19)
        assertThat(repo.state.value.cabinTempC).isEqualTo(driver)
    }

    @Test
    fun `a partial read follows climate and keeps the slow items from the last full read`() = runBlocking {
        val bus = seededMockBus()
        val repo = CarRepository(bus, scope)
        repo.refresh()
        bus.sendItemValue(VDEventCarInfo.MODULE_NEW_ENERGY, TelemetryIds.ITEM_BATTERY_SOC, 40)
        bus.sendItemValue(VDEventCarInfo.MODULE_HVAC, CabinTempSpec.ITEM_WRITE, CabinTempSpec.toRaw(27))
        repo.refresh(full = false)
        assertThat(repo.state.value.cabinTempC).isEqualTo(27)
        assertThat(repo.state.value.batterySoc).isEqualTo(72)
        repo.refresh()
        assertThat(repo.state.value.batterySoc).isEqualTo(40)
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

    @Test
    fun `a climate jump goes out as single steps, debounced to the last target`() = runBlocking {
        val bus = seededMockBus()
        val sent = mutableListOf<Int>()
        val recording = object : CarBus by bus {
            override fun sendItemValue(module: Int, id: Int, value: Int) {
                if (id == CabinTempSpec.ITEM_WRITE) sent += CabinTempSpec.toCelsius(value)
                bus.sendItemValue(module, id, value)
            }
        }
        val repo = CarRepository(recording, scope)
        repo.refresh()
        // Three quick targets: only the last one is followed, one degree per command.
        repo.setCabinTemp(28)
        repo.setCabinTemp(20)
        repo.setCabinTemp(25)
        // The controls hold the target while it is applied, then follow the car's reading.
        assertThat(repo.cabinTempTarget.value).isEqualTo(25)
        awaitMoveDone(repo)
        assertThat(sent).containsExactly(23, 24, 25).inOrder()
        assertThat(repo.cabinTempTarget.value).isNull()
        assertThat(repo.state.value.cabinTempC).isEqualTo(25)
    }

    @Test
    fun `a fan jump goes out as single steps on the set item, never on the panel's own`() = runBlocking {
        val bus = seededMockBus()
        val sent = mutableListOf<Pair<Int, Int>>()
        val recording = object : CarBus by bus {
            override fun sendItemValue(module: Int, id: Int, value: Int) {
                sent += id to value
                bus.sendItemValue(module, id, value)
            }
        }
        val repo = CarRepository(recording, scope)
        repo.refresh()
        val start = repo.state.value.fanLevel!!
        repo.setFan(start + 3)
        awaitMoveDone(repo)
        // HVAC 77 sets the level quietly; a write on 12 (where it is read) raises the car's panel.
        assertThat(sent).containsExactly(77 to start + 1, 77 to start + 2, 77 to start + 3).inOrder()
        assertThat(repo.state.value.fanLevel).isEqualTo(start + 3)
    }

    /**
     * Waits for the climate moves to finish (their targets clear once the car is read back),
     * instead of a fixed sleep: a slow CI runner takes longer than the nominal timings.
     */
    private suspend fun awaitMoveDone(repo: CarRepository) {
        delay(CarRepository.CLIMATE_DEBOUNCE_MS)
        val deadline = System.currentTimeMillis() + 10_000
        while (repo.cabinTempTarget.value != null || repo.fanTarget.value != null || repo.passengerTempTarget.value != null) {
            check(System.currentTimeMillis() < deadline) { "climate move did not finish within 10 s" }
            delay(50)
        }
    }
}
