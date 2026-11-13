package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.AssistIds
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.ClimatePanelSpec
import app.jconfig.carbus.carinfo.ClimateSwitch
import app.jconfig.carbus.carinfo.FcwLevel
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
import kotlinx.coroutines.withContext

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
    val collisionWarning: FcwLevel? = null,
    val acOn: Boolean? = null,
    val recirculation: Boolean? = null,
    val sync: Boolean? = null,
)

/**
 * The driver-assist switches the drawer can flip, with the gates the car's own UI applies
 * before it writes: all need the ignition on; emergency braking also needs the car below
 * 10 km/h; [confirmOff] ones ask before switching off.
 */
enum class Assist(val item: Int, val confirmOff: Boolean, val maxSpeedKmh: Int? = null) {
    AEB(AssistIds.ITEM_AEB, confirmOff = true, maxSpeedKmh = AssistIds.AEB_MAX_SPEED_KMH),
    AUTO_HOLD(AssistIds.ITEM_AUTO_HOLD, confirmOff = false),
    LANE(AssistIds.ITEM_LANE_DEPARTURE_PREVENTION, confirmOff = true),
}

/** Why a toggle was not sent. */
enum class AssistBlock { IGNITION_OFF, TOO_FAST, UNAVAILABLE }

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

    /** Whether the car's own service is bound. Until it is, every value stays empty. */
    fun isConnected(): Boolean = bus.isServiceConnected()

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
        fun climate(switch: ClimateSwitch) = switch.isOn(int(HVAC, switch.item))
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
            laneKeep = assist(AssistIds.ITEM_LANE_DEPARTURE_PREVENTION),
            collisionWarning = FcwLevel.of(bus.getItemValues(AssistIds.MODULE, FcwLevel.ITEM)),
            acOn = climate(ClimateSwitch.AC),
            recirculation = climate(ClimateSwitch.RECIRCULATION),
            sync = climate(ClimateSwitch.SYNC),
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

    /** Flips a climate panel button. The car's own panel applies no gate to these. */
    fun toggleClimate(switch: ClimateSwitch) {
        val on = when (switch) {
            ClimateSwitch.AC -> state.value.acOn
            ClimateSwitch.RECIRCULATION -> state.value.recirculation
            ClimateSwitch.SYNC -> state.value.sync
        } ?: return
        write(HVAC, switch.item, switch.command(!on))
    }

    /**
     * Moves forward collision warning to its next state: Off, Near, Standard, Far, Off. No
     * confirmation, by the owner's choice. The car's own gates still apply: ignition on, and
     * the switch itself (to or from Off) only below 10 km/h; the sensitivity can change at any
     * speed. A blocked step is skipped and its reason returned, so from Far at speed the
     * cycle goes on to Near instead of Off.
     */
    suspend fun cycleCollisionWarning(): AssistBlock? {
        var sent = false
        val blocked = withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!bus.isServiceConnected()) return@withLock AssistBlock.UNAVAILABLE
                if (!AssistIds.ignitionOn(bus.getItemValueOrNull(READONLY, AssistIds.ITEM_POWER_MODE))) {
                    return@withLock AssistBlock.IGNITION_OFF
                }
                val current = FcwLevel.of(bus.getItemValues(AssistIds.MODULE, FcwLevel.ITEM))
                    ?: return@withLock AssistBlock.UNAVAILABLE
                val speed = bus.getItemValueOrNull(READONLY, TelemetryIds.ITEM_CAR_SPEED)
                val slow = speed != null && speed < AssistIds.AEB_MAX_SPEED_KMH
                if (current == FcwLevel.OFF && !slow) return@withLock AssistBlock.TOO_FAST
                var next = FcwLevel.entries[(current.ordinal + 1) % FcwLevel.entries.size]
                var hint: AssistBlock? = null
                if (next == FcwLevel.OFF && !slow) {
                    next = FcwLevel.NEAR
                    hint = AssistBlock.TOO_FAST
                }
                try {
                    bus.sendItemValues(AssistIds.MODULE, FcwLevel.ITEM, next.command(current))
                    sent = true
                } catch (e: LinkageError) {
                    return@withLock AssistBlock.UNAVAILABLE
                }
                hint
            }
        }
        if (sent) {
            delay(WRITE_SETTLE_MS)
            refresh()
        }
        return blocked
    }

    /**
     * Flips [assist] from a tap. Every gate is checked against a fresh read, not the last poll,
     * and a failed read blocks the write. Returns why nothing was sent, or null when it was.
     */
    suspend fun toggleAssist(assist: Assist): AssistBlock? {
        val blocked = withContext(Dispatchers.IO) {
            mutex.withLock {
                if (!bus.isServiceConnected()) return@withLock AssistBlock.UNAVAILABLE
                if (!AssistIds.ignitionOn(bus.getItemValueOrNull(READONLY, AssistIds.ITEM_POWER_MODE))) {
                    return@withLock AssistBlock.IGNITION_OFF
                }
                if (assist.maxSpeedKmh != null) {
                    val speed = bus.getItemValueOrNull(READONLY, TelemetryIds.ITEM_CAR_SPEED)
                        ?: return@withLock AssistBlock.UNAVAILABLE
                    if (speed >= assist.maxSpeedKmh) return@withLock AssistBlock.TOO_FAST
                }
                val on = AssistIds.isOn(assist.item, bus.getItemValueOrNull(AssistIds.MODULE, assist.item))
                    ?: return@withLock AssistBlock.UNAVAILABLE
                try {
                    bus.sendItemValue(AssistIds.MODULE, assist.item, AssistIds.command(!on))
                } catch (e: LinkageError) {
                    return@withLock AssistBlock.UNAVAILABLE
                }
                null
            }
        }
        if (blocked == null) {
            delay(WRITE_SETTLE_MS)
            refresh()
        }
        return blocked
    }

    private fun write(module: Int, id: Int, value: Int) {
        scope.launch(Dispatchers.IO) {
            // One bus call at a time: two writes racing each other (or a poll) can be lost.
            var watchPanel = false
            mutex.withLock {
                try {
                    watchPanel = module == HVAC && !climatePanelShowing()
                    bus.sendItemValue(module, id, value)
                } catch (e: LinkageError) {
                    // Vendor class mismatch on the device; the next poll shows the real state.
                }
            }
            if (watchPanel) keepClimatePanelDown()
            delay(WRITE_SETTLE_MS)
            refresh()
        }
    }

    private fun climatePanelShowing() =
        ClimatePanelSpec.isShowing(bus.getItemValueOrNull(HVAC, ClimatePanelSpec.ITEM_DISPLAY_ACTIVE))

    /**
     * The car answers a climate write by asking its own climate panel to open, full screen.
     * Watch for that request for a moment and send the panel's close request, as the stock
     * settings app does. Only called when the panel was not already up, so one the driver
     * opened is left alone.
     */
    private suspend fun keepClimatePanelDown() {
        repeat(PANEL_WATCH_POLLS) {
            delay(PANEL_WATCH_POLL_MS)
            mutex.withLock {
                try {
                    if (climatePanelShowing()) {
                        bus.sendItemValue(HVAC, ClimatePanelSpec.ITEM_DISPLAY_ACTIVE, ClimatePanelSpec.COMMAND_CLOSE)
                    }
                } catch (e: LinkageError) {
                    // As above.
                }
            }
        }
    }

    companion object {
        const val POLL_MS = 1_500L
        const val WRITE_SETTLE_MS = 400L
        private const val PANEL_WATCH_POLLS = 8
        private const val PANEL_WATCH_POLL_MS = 150L
    }
}
