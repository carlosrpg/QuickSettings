package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.AssistIds
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.ClimatePanelSpec
import app.jconfig.carbus.carinfo.ClimateSwitch
import com.carlosrpg.cherry.quicksettings.nav.DebugLog
import app.jconfig.carbus.carinfo.FcwLevel
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.NewEnergyIds
import app.jconfig.carbus.carinfo.OutsideTempSpec
import app.jconfig.carbus.carinfo.RangeCodec
import app.jconfig.carbus.carinfo.RegenLevel
import app.jconfig.carbus.carinfo.TelemetryIds
import app.jconfig.carbus.carinfo.VDEventCarInfo
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** One snapshot of the car. `null` = not read or not available. */
data class CarState(
    val speedKmh: Int? = null,
    val cabinTempC: Int? = null,
    /** The passenger zone's set temperature; it differs from [cabinTempC] only while [sync] is off. */
    val passengerTempC: Int? = null,
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
    val climateOn: Boolean? = null,
    val acOn: Boolean? = null,
    val autoClimate: Boolean? = null,
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
 * (stock settings, voice, physical buttons), so [refresh] runs on the overlay's heartbeat and
 * after each write. Writes happen only from explicit user actions. Ids and encodings all come from
 * jconfig-carbus.
 */
class CarRepository(
    private val bus: CarBus,
    private val scope: CoroutineScope,
    /** Hides the car's climate panel at once, before the close request below reaches the car. */
    private val hideClimatePanel: () -> Unit = {},
) {
    private val _state = MutableStateFlow(CarState())
    val state: StateFlow<CarState> = _state.asStateFlow()

    private val mutex = Mutex()
    /** A read that timed out may still be blocked in the car's service: wait on it, don't stack more. */
    private var inFlight: Deferred<CarState>? = null

    /** Whether the car's own service is bound. Until it is, every value stays empty. */
    fun isConnected(): Boolean = bus.isServiceConnected()

    /** [SystemClock.elapsedRealtime] of the last complete read; 0 before the first. */
    @Volatile var lastReadAt = 0L
        private set

    /** Raw payloads of the range and fuel items, for the debug screen. */
    fun debugRanges(): String = listOf(
        "fuel%" to bus.getItemValues(READONLY, TelemetryIds.ITEM_FUEL_PERCENT),
        "fuelKm" to bus.getItemValues(READONLY, TelemetryIds.ITEM_RANGE_KM),
        "evKm" to bus.getItemValues(ENERGY, NewEnergyIds.ITEM_ELECTRIC_RANGE),
    ).joinToString("  ") { (name, raw) -> "$name=${raw.toList()}" }

    /**
     * Reads the car. [full] reads every item; otherwise only what changes quickly or what the
     * drawer's controls follow (speed, climate) is read and the rest is kept from the last full
     * read. The heartbeat makes every few beats full; every write ends with a full read.
     */
    suspend fun refresh(full: Boolean = true) = mutex.withLock {
        // Before the vendor proxy connects every read returns 0, and 0 is a valid status for
        // several items (regen 0 = high, battery mode 0 = initial): show nothing rather than that.
        if (!bus.isServiceConnected()) return@withLock
        // A binder call that never returns cannot be cancelled, so the read runs on its own
        // and is abandoned after a while instead of holding the loop (and the lock) for good.
        val previous = _state.value
        val pending = inFlight?.takeIf { it.isActive } ?: scope.async(Dispatchers.IO) { read(previous, full) }.also { inFlight = it }
        val next = withTimeoutOrNull(READ_TIMEOUT_MS) { pending.await() }
        if (next == null) {
            Log.w(TAG, "car read timed out")
            return@withLock
        }
        _state.value = next
        lastReadAt = SystemClock.elapsedRealtime()
    }

    internal fun read(previous: CarState, full: Boolean = true): CarState {
        fun int(module: Int, id: Int): Int? = bus.getItemValueOrNull(module, id)
        fun percent(module: Int, id: Int): Int? = int(module, id)?.takeIf { it in 0..100 }
        fun assist(id: Int) = AssistIds.isOn(id, int(AssistIds.MODULE, id))
        fun climate(switch: ClimateSwitch) = switch.isOn(int(HVAC, switch.item))
        val fast = previous.copy(
            speedKmh = int(READONLY, TelemetryIds.ITEM_CAR_SPEED),
            cabinTempC = int(HVAC, CabinTempSpec.ITEM_READ)
                ?.let(CabinTempSpec::toCelsius)?.takeIf(CabinTempSpec::isValid),
            passengerTempC = int(HVAC, CabinTempSpec.ITEM_PASSENGER_READ)
                ?.let(CabinTempSpec::toCelsius)?.takeIf(CabinTempSpec::isValid),
            fanLevel = int(HVAC, FanSpec.ITEM_READ)?.takeIf { it in 0..FanSpec.MAX },
            climateOn = climate(ClimateSwitch.POWER),
            acOn = climate(ClimateSwitch.AC),
            autoClimate = climate(ClimateSwitch.AUTO),
            recirculation = climate(ClimateSwitch.RECIRCULATION),
            sync = climate(ClimateSwitch.SYNC),
        )
        if (!full) return fast
        return fast.copy(
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
        (temperature.pending() ?: state.value.cabinTempC)?.let { setCabinTemp(it + delta) }
    }

    fun stepFan(delta: Int) {
        (fan.pending() ?: state.value.fanLevel)?.let { setFan(it + delta) }
    }

    fun setCabinTemp(celsius: Int) = temperature.moveTo(celsius.coerceIn(CabinTempSpec.MIN, CabinTempSpec.MAX))

    fun setFan(level: Int) = fan.moveTo(level.coerceIn(FanSpec.MIN, FanSpec.MAX))

    /** The passenger zone, stepped like the driver's. Only offered while the zones are split. */
    fun setPassengerTemp(celsius: Int) = passengerTemperature.moveTo(celsius.coerceIn(CabinTempSpec.MIN, CabinTempSpec.MAX))

    /**
     * The temperature and fan level being applied, null once the car has been stepped there and
     * read back. Controls show these meanwhile, so they don't snap back to the old reading.
     */
    val cabinTempTarget: StateFlow<Int?> get() = temperature.applying
    val fanTarget: StateFlow<Int?> get() = fan.applying
    val passengerTempTarget: StateFlow<Int?> get() = passengerTemperature.applying

    private val temperature = ClimateStepper(CabinTempSpec.ITEM_WRITE, { it.cabinTempC }, CabinTempSpec::toRaw)
    private val passengerTemperature = ClimateStepper(CabinTempSpec.ITEM_PASSENGER_WRITE, { it.passengerTempC }, CabinTempSpec::toRaw)
    // The fan's set item, not the one it is read from: a write there raises the car's panel.
    private val fan = ClimateStepper(FanSpec.ITEM_WRITE, { it.fanLevel }, { it })

    /**
     * Takes one climate value (temperature or fan) to a target the way the car's own controls
     * and the other apps that change it quietly do: one step per command. A jump of several
     * steps in one command is what makes the car raise its own full-screen climate panel.
     *
     * Debounced: a new target only starts moving once it has held still for
     * [CLIMATE_DEBOUNCE_MS], and a target that changes while the value is still moving is
     * simply followed, so a series of taps or a drag never queues up stale steps. Temperature
     * and fan each have their own stepper and move side by side.
     */
    private inner class ClimateStepper(
        private val item: Int,
        private val read: (CarState) -> Int?,
        private val raw: (Int) -> Int,
    ) {
        private val target = MutableStateFlow<Int?>(null)
        private var job: Job? = null

        /** The value being moved to, while a move is under way. */
        fun pending(): Int? = target.value.takeIf { job?.isActive == true }

        val applying = MutableStateFlow<Int?>(null)

        /** Counts [moveTo] calls, so a target set while a move is finishing is not dropped. */
        @Volatile private var requests = 0

        fun moveTo(value: Int) {
            if (job?.isActive != true && value == read(state.value)) return
            requests++
            target.value = value
            applying.value = value
            if (job?.isActive != true) job = scope.launch(Dispatchers.IO) { run() }
        }

        private suspend fun run() {
            try {
                // A tap that lands while the last move settles or reads back (moveTo then sees
                // this job still active and starts none) gets another pass here.
                do {
                    val seen = requests
                    apply()
                } while (requests != seen)
            } finally {
                applying.value = null
            }
        }

        private suspend fun apply() {
            var current: Int
            do {
                // Debounce: wait until the target has held still.
                var held: Int?
                do {
                    held = target.value
                    delay(CLIMATE_DEBOUNCE_MS)
                } while (target.value != held)
                // Each batch starts from the car's own reading, so a turn of the car's knob or a
                // step the car refused since the last batch is not overwritten from a stale value.
                refresh()
                current = read(state.value) ?: return
                val panelBefore = mutex.withLock { panelItem() }
                // One step at a time, following the target if it moves meanwhile.
                while (true) {
                    val goal = target.value ?: break
                    if (goal == current) break
                    current += if (goal > current) 1 else -1
                    DebugLog.add("climate-$item-${System.currentTimeMillis()}", "hvac $item=${raw(current)}  panel=$panelBefore")
                    send(HVAC, item, raw(current))
                    delay(CLIMATE_STEP_MS)
                }
                watchClimatePanel(item, panelBefore)
                // Settle inside the loop, so a target set meanwhile is still followed.
                delay(WRITE_SETTLE_MS)
            } while (target.value != current)
            refresh()
        }
    }

    /** Flips a climate panel button. The car's own panel applies no gate to these. */
    fun toggleClimate(switch: ClimateSwitch) {
        val on = when (switch) {
            ClimateSwitch.POWER -> state.value.climateOn
            ClimateSwitch.AC -> state.value.acOn
            ClimateSwitch.AUTO -> state.value.autoClimate
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
            val panelBefore = if (module == HVAC) mutex.withLock { panelItem() } else null
            send(module, id, value)
            if (module == HVAC) {
                DebugLog.add("climate-$id-${System.currentTimeMillis()}", "hvac $id=$value  panel=$panelBefore")
                watchClimatePanel(id, panelBefore)
            }
            delay(WRITE_SETTLE_MS)
            refresh()
        }
    }

    /** One bus call at a time: two writes racing each other (or a poll) can be lost. */
    private suspend fun send(module: Int, id: Int, value: Int) = mutex.withLock {
        try {
            bus.sendItemValue(module, id, value)
        } catch (e: LinkageError) {
            // Vendor class mismatch on the device; the next poll shows the real state.
        }
    }

    /**
     * After a climate write, watches the car's "show climate panel" item for about two seconds.
     * Every change of it goes to the debug log against the write that preceded it, so a test on
     * the car shows which command made the car raise its panel; a panel our write raised is
     * closed again, as the stock settings app does, and one the driver had open is left alone.
     */
    private suspend fun watchClimatePanel(id: Int, before: Int?) {
        var last = before
        val start = System.currentTimeMillis()
        repeat(PANEL_WATCH_POLLS) {
            delay(PANEL_WATCH_POLL_MS)
            mutex.withLock {
                val now = panelItem()
                if (now != last) {
                    DebugLog.add("climate-$id-${System.currentTimeMillis()}", "  after $id: panel $last->$now at +${System.currentTimeMillis() - start}ms")
                    last = now
                }
                if (!ClimatePanelSpec.isShowing(before) && ClimatePanelSpec.isShowing(now)) {
                    hideClimatePanel()
                    try {
                        bus.sendItemValue(HVAC, ClimatePanelSpec.ITEM_DISPLAY_ACTIVE, ClimatePanelSpec.COMMAND_CLOSE)
                    } catch (e: LinkageError) {
                        // Vendor class mismatch on the device.
                    }
                }
            }
        }
    }

    private fun panelItem(): Int? = bus.getItemValueOrNull(HVAC, ClimatePanelSpec.ITEM_DISPLAY_ACTIVE)

    companion object {
        private const val READ_TIMEOUT_MS = 5_000L
        private const val TAG = "QsCar"
        const val WRITE_SETTLE_MS = 400L

        /** A climate target must hold still this long before it starts moving. */
        const val CLIMATE_DEBOUNCE_MS = 300L

        /** Gap between two single-step climate commands. */
        const val CLIMATE_STEP_MS = 100L
        // About two seconds: long enough to see the car react, short enough between two writes.
        private const val PANEL_WATCH_POLLS = 30
        // Short polls: the sooner the panel is seen, the shorter its flash.
        private const val PANEL_WATCH_POLL_MS = 70L

        /** How long the car's panel item is watched after a climate write. */
        const val CLIMATE_WRITE_MS = PANEL_WATCH_POLLS * PANEL_WATCH_POLL_MS
    }
}
