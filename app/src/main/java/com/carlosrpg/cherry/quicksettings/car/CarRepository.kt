package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.OutsideTempSpec
import com.carlosrpg.cherry.quicksettings.car.CarItems.BatteryMode
import com.carlosrpg.cherry.quicksettings.car.CarItems.Regen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One snapshot of the car. `null` = not read or not available. */
data class CarState(
    val speedKmh: Int? = null,
    val cabinTempC: Int? = null,
    val fanLevel: Int? = null,
    val outsideTempC: Int? = null,
    val batteryMode: BatteryMode? = null,
    val holdTarget: Int? = null,
    val regen: Regen? = null,
    val batterySoc: Int? = null,
    val chargeLimit: Int? = null,
    val electricRangeKm: Int? = null,
    val fuelRangeKm: Int? = null,
    val fuelPercent: Int? = null,
    val aeb: Boolean? = null,
    val autoHold: Boolean? = null,
    val laneKeep: Boolean? = null,
)

/**
 * Reads the car by polling: CarInfo has no subscribe, and values change from other places
 * (stock settings, voice, physical buttons), so every read is repeated on a timer and after
 * each write. Writes happen only from explicit user actions.
 */
class CarRepository(private val bus: CarBus, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var pollJob: Job? = null

    /** Polls while something is on screen; call [stopPolling] when the drawer closes. */
    fun startPolling(intervalMs: Long = POLL_MS) {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                refresh()
                delay(intervalMs)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    suspend fun refresh() = mutex.withLock { _state.value = read(_state.value) }

    internal fun read(previous: CarState): CarState {
        fun int(module: Int, id: Int): Int? = bus.getItemValueOrNull(module, id)
        fun array(module: Int, id: Int): IntArray = bus.getItemValues(module, id)
        val ne = CarItems.NEW_ENERGY
        val ro = CarItems.READONLY
        val hvac = CarItems.HVAC
        val set = CarItems.CAR_SETTING
        return CarState(
            speedKmh = int(ro, CarItems.SPEED),
            cabinTempC = int(hvac, CarItems.CABIN_TEMP_READ)
                ?.let(CabinTempSpec::toCelsius)?.takeIf(CabinTempSpec::isValid),
            fanLevel = int(hvac, CarItems.FAN_READ)?.takeIf { it in 0..FanSpec.MAX },
            outsideTempC = int(hvac, CarItems.OUTSIDE_TEMP)?.takeIf { it > 0 }
                ?.let(OutsideTempSpec::toCelsius),
            // 255 / ignored statuses keep the last known value, as the stock UI does.
            batteryMode = BatteryMode.fromStatus(int(ne, CarItems.BATTERY_MODE)) ?: previous.batteryMode,
            holdTarget = int(ne, CarItems.HOLD_TARGET)
                ?.takeIf { it in CarItems.HOLD_MIN..CarItems.HOLD_MAX } ?: previous.holdTarget,
            regen = Regen.fromStatus(int(ne, CarItems.REGEN)) ?: previous.regen,
            batterySoc = CarItems.percent(int(ne, CarItems.BATTERY_SOC)),
            chargeLimit = CarItems.percent(int(ne, CarItems.CHARGE_LIMIT))?.takeIf { it > 0 },
            electricRangeKm = CarItems.electricRangeKm(array(ne, CarItems.ELECTRIC_RANGE)),
            fuelRangeKm = CarItems.fuelRangeKm(array(ro, CarItems.FUEL_RANGE)),
            fuelPercent = CarItems.fuelPercent(
                array(ro, CarItems.FUEL_PERCENT), int(ro, CarItems.FUEL_PERCENT)
            ),
            aeb = int(set, CarItems.AEB)?.let { it == 1 },
            autoHold = int(set, CarItems.AUTO_HOLD)?.let { it == 1 || it == 2 },
            laneKeep = int(set, CarItems.LANE_KEEP)?.let { it == 1 },
        )
    }

    // Regen and battery mode are writable while driving; the stock settings does not gate them.
    fun setRegen(regen: Regen) = write(CarItems.NEW_ENERGY, CarItems.REGEN, regen.command)

    fun setBatteryMode(mode: BatteryMode) =
        write(CarItems.NEW_ENERGY, CarItems.BATTERY_MODE, mode.command)

    fun setHoldTarget(percent: Int) = write(
        CarItems.NEW_ENERGY, CarItems.HOLD_TARGET,
        percent.coerceIn(CarItems.HOLD_MIN, CarItems.HOLD_MAX)
    )

    fun stepCabinTemp(delta: Int) {
        val current = state.value.cabinTempC ?: return
        val target = (current + delta).coerceIn(CabinTempSpec.MIN, CabinTempSpec.MAX)
        if (target != current) write(CarItems.HVAC, CarItems.CABIN_TEMP_WRITE, CabinTempSpec.toRaw(target))
    }

    fun stepFan(delta: Int) {
        val current = state.value.fanLevel ?: return
        val target = (current + delta).coerceIn(FanSpec.MIN, FanSpec.MAX)
        if (target != current) write(CarItems.HVAC, CarItems.FAN_WRITE, target)
    }

    private fun write(module: Int, id: Int, value: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                bus.sendItemValue(module, id, value)
            } catch (e: LinkageError) {
                // Vendor class mismatch on the device; the next poll shows the real state.
            }
            delay(WRITE_SETTLE_MS)
            refresh()
        }
    }

    companion object {
        const val POLL_MS = 1_500L
        const val WRITE_SETTLE_MS = 400L
    }
}
