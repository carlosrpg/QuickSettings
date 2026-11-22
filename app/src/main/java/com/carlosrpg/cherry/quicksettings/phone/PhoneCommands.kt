package com.carlosrpg.cherry.quicksettings.phone

import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.ClimateSwitch
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.NewEnergyIds
import app.jconfig.carbus.carinfo.RegenLevel
import com.carlosrpg.cherry.quicksettings.car.Assist
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.media.VolumeChannel
import org.json.JSONObject

/** The drawer's non-car actions, as the phones reach them (media, navigation, apps). */
interface PhoneActions {
    fun playPause()
    fun next()
    fun previous()
    /** Packages the media card offers as recent apps right now. */
    fun recentPackages(): List<String>
    fun playRecent(pkg: String): Boolean
    fun mediaSearch(query: String): Boolean
    fun setVolume(channel: VolumeChannel, value: Int)
    fun toggleMute()
    fun navSearch(query: String): Boolean
    fun toggleNavOnCluster()
    fun bringToFront(nav: Boolean): Boolean
}

/**
 * The command table of docs/companion-api.md. Each action is the call the drawer makes for the
 * same control, with the drawer's gates: a phone can do nothing the drawer can't, and assist
 * switches keep the car's rules (checked by [CarRepository] against a fresh read) plus the
 * drawer's second tap, here `confirm: true`, before a safety function goes off.
 */
class PhoneCommands(private val car: CarRepository, private val actions: PhoneActions) {

    suspend fun run(action: String, args: JSONObject, remote: Boolean): JSONObject {
        val blocked: String? = try {
            dispatch(action, args, remote)
        } catch (e: BadArgs) {
            "BAD_ARGS"
        }
        return if (blocked == null) JSONObject().put("ok", true) else JSONObject().put("ok", false).put("blocked", blocked)
    }

    private class BadArgs : Exception()

    private fun JSONObject.int(name: String, range: IntRange): Int {
        if (!has(name)) throw BadArgs()
        return optInt(name, Int.MIN_VALUE).takeIf { it in range } ?: throw BadArgs()
    }

    private inline fun <reified E : Enum<E>> JSONObject.enum(name: String): E =
        enumValues<E>().firstOrNull { it.name == optString(name) } ?: throw BadArgs()

    private fun JSONObject.text(name: String): String = optString(name).trim().take(MAX_TEXT).ifEmpty { throw BadArgs() }

    /** Returns why nothing was sent, or null. */
    private suspend fun dispatch(action: String, args: JSONObject, remote: Boolean): String? {
        if (action.startsWith("climate.") || action.startsWith("energy.") || action.startsWith("assist.")) {
            if (!car.isConnected()) return "NOT_CONNECTED"
        }
        when (action) {
            "climate.setDriverTemp" -> car.setCabinTemp(args.int("c", CabinTempSpec.MIN..CabinTempSpec.MAX))
            "climate.setPassengerTemp" -> {
                val c = args.int("c", CabinTempSpec.MIN..CabinTempSpec.MAX)
                // The drawer offers the passenger slider only while the zones are split.
                if (car.state.value.sync != false) return "NOT_SPLIT"
                car.setPassengerTemp(c)
            }
            "climate.setFan" -> car.setFan(args.int("level", FanSpec.MIN..FanSpec.MAX))
            "climate.toggle" -> car.toggleClimate(args.enum<ClimateSwitch>("switch"))
            "energy.setRegen" -> car.setRegen(args.enum<RegenLevel>("level"))
            "energy.setBatteryMode" -> car.setBatteryMode(args.enum<BatteryMode>("mode"))
            "energy.setHoldTarget" -> car.setHoldTarget(args.int("percent", NewEnergyIds.SOC_TARGET_MIN..NewEnergyIds.SOC_TARGET_MAX))
            "assist.toggle" -> {
                val assist = args.enum<Assist>("assist")
                if (remote) return "REMOTE_DISABLED"
                val on = when (assist) {
                    Assist.AEB -> car.state.value.aeb
                    Assist.AUTO_HOLD -> car.state.value.autoHold
                    Assist.LANE -> car.state.value.laneKeep
                }
                if (on == true && assist.confirmOff && !args.optBoolean("confirm")) return "NEEDS_CONFIRM"
                return car.toggleAssist(assist)?.name
            }
            "assist.cycleCollisionWarning" -> {
                if (remote) return "REMOTE_DISABLED"
                return car.cycleCollisionWarning()?.name
            }
            "media.playPause" -> actions.playPause()
            "media.next" -> actions.next()
            "media.previous" -> actions.previous()
            "media.playRecent" -> {
                val pkg = args.text("packageName")
                if (pkg !in actions.recentPackages()) throw BadArgs()
                if (!actions.playRecent(pkg)) return "UNAVAILABLE"
            }
            "media.search" -> if (!actions.mediaSearch(args.text("query"))) return "UNAVAILABLE"
            "media.setVolume" -> actions.setVolume(args.enum<VolumeChannel>("channel"), args.int("value", 0..MAX_VOLUME))
            "media.toggleMute" -> actions.toggleMute()
            "nav.search" -> if (!actions.navSearch(args.text("query"))) return "UNAVAILABLE"
            "nav.toggleCluster" -> actions.toggleNavOnCluster()
            "app.bringToFront" -> {
                val nav = when (args.optString("which")) {
                    "MEDIA" -> false
                    "NAV" -> true
                    else -> throw BadArgs()
                }
                if (!actions.bringToFront(nav)) return "UNAVAILABLE"
            }
            else -> return "UNKNOWN_ACTION"
        }
        return null
    }

    companion object {
        private const val MAX_TEXT = 200
        // Clamped again by the media repository to the stream's own maximum.
        private const val MAX_VOLUME = 100
    }
}
