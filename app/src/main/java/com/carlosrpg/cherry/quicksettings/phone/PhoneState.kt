package com.carlosrpg.cherry.quicksettings.phone

import android.graphics.Bitmap
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.media.VolumeChannel
import com.carlosrpg.cherry.quicksettings.media.VolumeLevel
import com.carlosrpg.cherry.quicksettings.nav.NavState
import com.carlosrpg.cherry.quicksettings.settings.QsConfig
import com.carlosrpg.cherry.quicksettings.weather.WeatherState
import java.io.ByteArrayOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bitmaps the state refers to (album art, app icons, the turn arrow), served at `/img/<key>`.
 * A key names one bitmap object, so the page's cache keeps an image until it changes.
 */
class ImageCache {
    private val bitmaps = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > MAX
    }
    private val pngs = HashMap<String, ByteArray>()

    @Synchronized
    fun path(bitmap: Bitmap?): String? {
        bitmap ?: return null
        val key = Integer.toHexString(System.identityHashCode(bitmap)) + Integer.toHexString(bitmap.generationId)
        bitmaps[key] = bitmap
        return "/img/$key"
    }

    @Synchronized
    fun png(key: String): ByteArray? {
        val bitmap = bitmaps[key] ?: return null
        pngs.keys.retainAll(bitmaps.keys)
        return pngs.getOrPut(key) {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
    }

    private companion object {
        const val MAX = 32
    }
}

/** What goes into one state document, gathered by the car app. */
class PhoneSnapshot(
    val connected: Boolean,
    val car: CarState,
    val applyingDriver: Int?,
    val applyingPassenger: Int?,
    val applyingFan: Int?,
    val config: QsConfig,
    val media: MediaState,
    /** Wall-clock ms for [MediaState.positionAt], or 0. */
    val positionAtMs: Long,
    val volumes: Map<VolumeChannel, VolumeLevel>,
    val nav: NavState?,
    val navAppLabel: String?,
    val navOnCluster: Boolean,
    val weather: WeatherState,
)

/** The state document of docs/companion-api.md (without `path`, `asOfMs`, `permissions`). */
fun phoneStateJson(s: PhoneSnapshot, images: ImageCache): JSONObject {
    val c = s.car
    fun JSONObject.opt(name: String, value: Any?) = put(name, value ?: JSONObject.NULL)
    val climate = JSONObject()
        .opt("on", c.climateOn).opt("ac", c.acOn).opt("auto", c.autoClimate).opt("recirculation", c.recirculation)
        .opt("sync", c.sync).opt("driverTempC", c.cabinTempC).opt("passengerTempC", c.passengerTempC)
        .opt("fanLevel", c.fanLevel).opt("outsideTempC", c.outsideTempC)
        .put("applying", JSONObject().opt("driverTempC", s.applyingDriver).opt("passengerTempC", s.applyingPassenger).opt("fanLevel", s.applyingFan))
    val energy = JSONObject()
        .opt("batterySoc", c.batterySoc).opt("fuelPercent", c.fuelPercent).opt("electricRangeKm", c.electricRangeKm)
        .opt("fuelRangeKm", c.fuelRangeKm).opt("chargeLimit", c.chargeLimit).opt("regen", c.regen?.name)
        .opt("batteryMode", c.batteryMode?.name).opt("holdTarget", c.holdTarget)
    val assist = JSONObject().opt("aeb", c.aeb).opt("autoHold", c.autoHold).opt("laneKeep", c.laneKeep)
        .opt("collisionWarning", c.collisionWarning?.name)
    val m = s.media
    val volume = JSONObject()
    s.volumes.forEach { (channel, level) ->
        volume.put(channel.name.lowercase(), JSONObject().put("value", level.value).put("max", level.max))
    }
    val media = JSONObject()
        .put("source", if (m.packageName != null) "app" else if (m.appLabel != null) "car" else JSONObject.NULL)
        .opt("appLabel", m.appLabel).opt("appIcon", images.path(m.appIcon)).opt("title", m.title).opt("artist", m.artist)
        .opt("art", images.path(m.art)).put("playing", m.playing).put("positionMs", m.positionMs)
        .put("positionAtMs", s.positionAtMs).put("durationMs", m.durationMs).put("muted", m.muted)
        .put("volumeKnown", m.volumeKnown).put("volume", volume)
        .put("recent", JSONArray().apply {
            m.recent.forEach { put(JSONObject().put("packageName", it.packageName).put("label", it.label).opt("icon", images.path(it.icon))) }
        })
    val nav = s.nav?.let { n ->
        JSONObject().opt("appLabel", s.navAppLabel ?: n.appLabel).opt("title", n.title).opt("text", n.text)
            .opt("subText", n.subText).opt("maneuver", images.path(n.maneuver))
    }
    val forecast = s.weather.forecast
    val weather = forecast?.let { f ->
        JSONObject().put("tempC", f.tempC).put("kind", f.kind.name).put("isDay", f.isDay).opt("place", f.place)
            .put("daily", JSONArray().apply {
                f.days.forEach { put(JSONObject().put("date", it.date.toString()).put("minC", it.minC).put("maxC", it.maxC).put("kind", it.kind.name)) }
            })
    }
    return JSONObject()
        .put("connected", s.connected)
        .put("layout", JSONObject().put("open", JSONArray(s.config.open.map { it.name })).put("side", s.config.side.name))
        .put("car", JSONObject().opt("speedKmh", c.speedKmh).put("climate", climate).put("energy", energy).put("assist", assist))
        .put("media", media)
        .opt("nav", nav)
        .put("cluster", JSONObject().put("navOnCluster", s.navOnCluster))
        .opt("weather", weather)
}
