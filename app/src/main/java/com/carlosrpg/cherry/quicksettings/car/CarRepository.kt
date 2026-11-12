package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.AssistIds
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.NewEnergyIds
import app.jconfig.carbus.carinfo.OutsideTempSpec
import app.jconfig.carbus.carinfo.RangeCodec
import app.jconfig.carbus.carinfo.RegenLevel
import app.jconfig.carbus.carinfo.TelemetryIds
import app.jconfig.carbus.carinfo.VDEventCarInfo
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
    val regen: RegenLevel? = null,
    val batterySoc: Int? = null,
    val chargeLimit: Int? = null,
    val electricRangeKm: Int? = null,
    val fuelRangeKm: Int? = null,
    val fuelPercent: Int? = null,
    val aeb: Boolean? = null,
    val autoHold: Boolean? = null,
    val laneKeep: Boolean? = null,
)

private const val ENERGY = VDEventCarInfo.MODULE_NEW_ENERGY
private const val READONLY = VDEventCarInfo.MODULE_READONLY_INFO
private const val HVAC = VDEventCarInfo.MODULE_HVAC

/**
 * Reads the car by polling: CarInfo has no subscribe, and values change from other places
 * (stock settings, voice, physical buttons), so every read is repeated on a timer and after
 * each write. Writes happen only from explicit user actions. Ids and encodings all come from
 * jconfig-carbus.
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

    suspend fun refresh() = mutex.withLock {
        // Before the vendor proxy connects every read returns 0, and 0 is a valid status for
        // several items (regen 0 = high, battery mode 0 = initial): show nothing rather than that.
        if (bus.isServiceConnected()) _state.value = read(_state.value)
    }

    internal fun read(previous: CarState): CarState {
        fun int(module: Int, id: Int): Int? = bus.getItemValueOrNull(module, id)
        fun percent(module: Int, id: Int): Int? = int(module, id)?.takeIf { it in 0..100 }
        fun assist(id: Int) = AssistIds.isOn(id, int(AssistIds.MODULE, id))
        return CarState(
            speedKmh = int(READONLY, TelemetryIds.ITEM_CAR_SPEED),
            cabinTempC = int(HVAC, CabinTempSpec.ITEM_READ)
                ?.let(CabinTempSpec::toCelsius)?.takeIf(CabinTempSpec::isValid),
            fanLevel = int(HVAC, FanSpec.ITEM_READ)?.takeIf { it in 0..FanSpec.MAX },
            outsideTempC = int(HVAC, OutsideTempSpec.ITEM_ID)?.takeIf { it > 0 }
                ?.let(OutsideTempSpec::toCelsius),
            // Unavailable / ignored statuses keep the last known value, as the stock UI does.
            batteryMode = BatteryMode.fromStatus(int(ENERGY, NewEnergyIds.ITEM_SOC_MANAGER_TYPE))
                ?: previous.batteryMode,
            holdTarget = int(ENERGY, NewEnergyIds.ITEM_SOC_TARGET)
                ?.takeIf { it in NewEnergyIds.SOC_TARGET_MIN..NewEnergyIds.SOC_TARGET_MAX }
                ?: previous.holdTarget,
            regen = RegenLevel.fromStatus(int(ENERGY, NewEnergyIds.ITEM_ENERGY_RECYCLE_LEVEL))
                ?: previous.regen,
            batterySoc = percent(ENERGY, TelemetryIds.ITEM_BATTERY_SOC),
            chargeLimit = percent(ENERGY, NewEnergyIds.ITEM_CHARGING_STOP_SOC)?.takeIf { it > 0 },
            electricRangeKm = RangeCodec.electricRangeKm(bus.getItemValues(ENERGY, NewEnergyIds.ITEM_ELECTRIC_RANGE)),
            fuelRangeKm = RangeCodec.fuelRangeKm(bus.getItemValues(READONLY, TelemetryIds.ITEM_RANGE_KM)),
            fuelPercent = RangeCodec.fuelPercent(
                bus.getItemValues(READONLY, TelemetryIds.ITEM_FUEL_PERCENT),
                int(READONLY, TelemetryIds.ITEM_FUEL_PERCENT),
            ),
            aeb = assist(AssistIds.ITEM_AEB),
            autoHold = assist(AssistIds.ITEM_AUTO_HOLD),
            laneKeep = assist(AssistIds.ITEM_EMERGENCY_LANE_KEEPING),
        )
    }

    // Regen and battery mode are writable while driving; the stock settings does not gate them.
    fun setRegen(regen: RegenLevel) = write(ENERGY, NewEnergyIds.ITEM_ENERGY_RECYCLE_LEVEL, regen.command)

    fun setBatteryMode(mode: BatteryMode) = write(ENERGY, NewEnergyIds.ITEM_SOC_MANAGER_TYPE, mode.command)

    fun setHoldTarget(percent: Int) = write(
        ENERGY, NewEnergyIds.ITEM_SOC_TARGET,
        percent.coerceIn(NewEnergyIds.SOC_TARGET_MIN, NewEnergyIds.SOC_TARGET_MAX),
    )

    fun stepCabinTemp(delta: Int) {
        state.value.cabinTempC?.let { setCabinTemp(it + delta) }
    }

    fun stepFan(delta: Int) {
        state.value.fanLevel?.let { setFan(it + delta) }
    }

    /** Writes only when the clamped value differs from what the car last reported. */
    fun setCabinTemp(celsius: Int) {
        val target = celsius.coerceIn(CabinTempSpec.MIN, CabinTempSpec.MAX)
        if (target != state.value.cabinTempC) write(HVAC, CabinTempSpec.ITEM_WRITE, CabinTempSpec.toRaw(target))
    }

    fun setFan(level: Int) {
        val target = level.coerceIn(FanSpec.MIN, FanSpec.MAX)
        if (target != state.value.fanLevel) write(HVAC, FanSpec.ITEM_WRITE, target)
    }

    private fun write(module: Int, id: Int, value: Int) {
        scope.launch(Dispatchers.IO) {
            // One bus call at a time: two writes racing each other (or a poll) can be lost.
            mutex.withLock {
                try {
                    bus.sendItemValue(module, id, value)
                } catch (e: LinkageError) {
                    // Vendor class mismatch on the device; the next poll shows the real state.
                }
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
