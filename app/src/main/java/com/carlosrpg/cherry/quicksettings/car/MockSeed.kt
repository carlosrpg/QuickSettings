package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.mock.MockCarBus

/** A plausible parked PHEV for the emulator. Writes go through the mock's command→status map. */
fun seededMockBus(): MockCarBus = MockCarBus().apply {
    sendItemValue(CarItems.NEW_ENERGY, CarItems.BATTERY_SOC, 72)
    sendItemValue(CarItems.NEW_ENERGY, CarItems.CHARGE_LIMIT, 90)
    sendItemValue(CarItems.NEW_ENERGY, CarItems.REGEN, CarItems.Regen.MEDIUM.command)
    sendItemValue(CarItems.NEW_ENERGY, CarItems.BATTERY_MODE, CarItems.BatteryMode.SMART.command)
    sendItemValue(CarItems.NEW_ENERGY, CarItems.HOLD_TARGET, 50)
    sendItemValues(CarItems.NEW_ENERGY, CarItems.ELECTRIC_RANGE, intArrayOf(0, 58))
    sendItemValues(CarItems.READONLY, CarItems.FUEL_RANGE, intArrayOf(0x02, 0x2A)) // 554 km
    sendItemValues(CarItems.READONLY, CarItems.FUEL_PERCENT, intArrayOf(45))
    sendItemValue(CarItems.HVAC, CarItems.OUTSIDE_TEMP, (14 + 55) * 2)
    sendItemValue(CarItems.CAR_SETTING, CarItems.AEB, 1)
    sendItemValue(CarItems.CAR_SETTING, CarItems.AUTO_HOLD, 1)
    sendItemValue(CarItems.CAR_SETTING, CarItems.LANE_KEEP, 0)
}
