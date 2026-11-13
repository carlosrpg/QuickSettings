package com.carlosrpg.cherry.quicksettings.car

import app.jconfig.carbus.carinfo.AssistIds
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.NewEnergyIds
import app.jconfig.carbus.carinfo.OutsideTempSpec
import app.jconfig.carbus.carinfo.RegenLevel
import app.jconfig.carbus.carinfo.ClimateSwitch
import app.jconfig.carbus.carinfo.FcwLevel
import app.jconfig.carbus.carinfo.TelemetryIds
import app.jconfig.carbus.carinfo.VDEventCarInfo
import app.jconfig.carbus.mock.MockCarBus

/** A plausible parked PHEV for the emulator. Writes go through the mock's command→status map. */
fun seededMockBus(): MockCarBus = MockCarBus().apply {
    val energy = VDEventCarInfo.MODULE_NEW_ENERGY
    val readonly = VDEventCarInfo.MODULE_READONLY_INFO
    sendItemValue(energy, TelemetryIds.ITEM_BATTERY_SOC, 72)
    sendItemValue(energy, NewEnergyIds.ITEM_CHARGING_STOP_SOC, 90)
    sendItemValue(energy, NewEnergyIds.ITEM_ENERGY_RECYCLE_LEVEL, RegenLevel.MEDIUM.command)
    sendItemValue(energy, NewEnergyIds.ITEM_SOC_MANAGER_TYPE, BatteryMode.SMART.command)
    sendItemValue(energy, NewEnergyIds.ITEM_SOC_TARGET, 50)
    sendItemValues(energy, NewEnergyIds.ITEM_ELECTRIC_RANGE, intArrayOf(0, 58))
    sendItemValues(readonly, TelemetryIds.ITEM_RANGE_KM, intArrayOf(0x02, 0x2A)) // 554 km
    sendItemValues(readonly, TelemetryIds.ITEM_FUEL_PERCENT, intArrayOf(0x01, 0xC2)) // 45.0 %
    sendItemValue(VDEventCarInfo.MODULE_HVAC, ClimateSwitch.AC.item, ClimateSwitch.AC.command(true))
    sendItemValue(VDEventCarInfo.MODULE_HVAC, OutsideTempSpec.ITEM_ID, (14 + OutsideTempSpec.RAW_OFFSET) * 2)
    sendItemValue(AssistIds.MODULE, AssistIds.ITEM_AEB, 1)
    sendItemValue(AssistIds.MODULE, AssistIds.ITEM_AUTO_HOLD, 1)
    sendItemValue(AssistIds.MODULE, AssistIds.ITEM_LANE_DEPARTURE_PREVENTION, 0)
    sendItemValue(readonly, AssistIds.ITEM_POWER_MODE, 2) // ignition on
    sendItemValues(AssistIds.MODULE, FcwLevel.ITEM, FcwLevel.STANDARD.command(null))
    sendItemValue(VDEventCarInfo.MODULE_HVAC, ClimateSwitch.SYNC.item, ClimateSwitch.SYNC.command(false))
}
