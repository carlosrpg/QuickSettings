package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.VDEventCarInfo

/**
 * CarInfo items the widgets use, with the encodings the head unit's own settings app uses
 * (see jconfig-carbus `docs/carinfo-items.md`). Command codes written often differ from the
 * status codes read back.
 */
object CarItems {
    const val NEW_ENERGY = VDEventCarInfo.MODULE_NEW_ENERGY
    const val READONLY = VDEventCarInfo.MODULE_READONLY_INFO
    const val HVAC = VDEventCarInfo.MODULE_HVAC
    const val CAR_SETTING = VDEventCarInfo.MODULE_CAR_SETTING

    // NEW_ENERGY
    const val BATTERY_SOC = 10
    const val REGEN = 37
    const val ELECTRIC_RANGE = 42
    const val BATTERY_MODE = 55
    const val CHARGE_LIMIT = 61
    const val HOLD_TARGET = 92

    // READONLY_INFO
    const val SPEED = 0
    const val FUEL_PERCENT = 68
    const val FUEL_RANGE = 72

    // HVAC
    const val CABIN_TEMP_READ = 10
    const val CABIN_TEMP_WRITE = 76
    const val FAN_READ = 12
    const val FAN_WRITE = 77
    const val OUTSIDE_TEMP = 19

    // CAR_SETTING
    const val AEB = 37
    const val LANE_KEEP = 107
    const val AUTO_HOLD = 153

    const val HOLD_MIN = 30
    const val HOLD_MAX = 80
    const val UNAVAILABLE = 255

    /** Regeneration level, NEW_ENERGY 37. */
    enum class Regen(val command: Int) {
        LOW(1), MEDIUM(2), HIGH(3);

        companion object {
            /** Status 0 = high, 1 = medium, anything else = low; 255 or no read = unknown. */
            fun fromStatus(status: Int?): Regen? = when (status) {
                null, UNAVAILABLE -> null
                0 -> HIGH
                1 -> MEDIUM
                else -> LOW
            }
        }
    }

    /** Battery management mode, NEW_ENERGY 55. Only [FORCED] uses [HOLD_TARGET]. */
    enum class BatteryMode(val command: Int) {
        INITIAL(1), SMART(2), FORCED(4);

        companion object {
            /** Status 0 or 255 = initial, 1 = smart, 3 = forced; 2 is ignored by the stock UI. */
            fun fromStatus(status: Int?): BatteryMode? = when (status) {
                0, UNAVAILABLE -> INITIAL
                1 -> SMART
                3 -> FORCED
                else -> null
            }
        }
    }

    /** Two-byte big-endian value as the range items report it; null when too short. */
    fun be16(values: IntArray): Int? =
        if (values.size < 2) null else ((values[0] and 0xFF) shl 8) or (values[1] and 0xFF)

    /** Fuel range in km, READONLY 72; `0xF000` and 0 mean unavailable. */
    fun fuelRangeKm(values: IntArray): Int? = be16(values)?.takeIf { it != 0xF000 && it != 0 }

    /** Electric range in km, NEW_ENERGY 42; 1023 and above mean unavailable. */
    fun electricRangeKm(values: IntArray): Int? = be16(values)?.takeIf { it < 1023 }

    /**
     * Fuel level %, READONLY 68. shortcut: the payload width is unconfirmed (no stock app reads
     * it), so the first byte is taken when it is a plausible percentage; check against the
     * cluster on the car and fix here.
     */
    fun fuelPercent(values: IntArray, single: Int?): Int? =
        (values.firstOrNull() ?: single)?.takeIf { it in 0..100 }

    /** Plausible percentage or null (a wrong or missing id reads 0 or garbage). */
    fun percent(value: Int?): Int? = value?.takeIf { it in 0..100 }
}
