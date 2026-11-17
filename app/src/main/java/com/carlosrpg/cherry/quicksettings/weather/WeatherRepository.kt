package com.carlosrpg.cherry.quicksettings.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import com.carlosrpg.cherry.quicksettings.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

enum class WeatherKind { CLEAR, PARTLY_CLOUDY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, THUNDER }

/** WMO weather interpretation code, as Open-Meteo reports it. */
fun weatherKind(code: Int): WeatherKind = when (code) {
    0, 1 -> WeatherKind.CLEAR
    2 -> WeatherKind.PARTLY_CLOUDY
    45, 48 -> WeatherKind.FOG
    in 51..57 -> WeatherKind.DRIZZLE
    in 61..67, in 80..82 -> WeatherKind.RAIN
    in 71..77, 85, 86 -> WeatherKind.SNOW
    in 95..99 -> WeatherKind.THUNDER
    else -> WeatherKind.CLOUDY
}

data class DayForecast(val date: LocalDate, val kind: WeatherKind, val maxC: Int, val minC: Int)

data class Forecast(
    val tempC: Int,
    val kind: WeatherKind,
    val isDay: Boolean,
    val days: List<DayForecast>,
    /** [SystemClock.elapsedRealtime] when it was fetched. */
    val fetchedAt: Long,
    /** The town or city, when the device can name it. */
    val place: String? = null,
)

enum class WeatherProblem { NO_PERMISSION, NO_LOCATION, UNAVAILABLE }

/** The last forecast, kept through later failures; [problem] only matters while there is none. */
data class WeatherState(val forecast: Forecast? = null, val problem: WeatherProblem? = null)

/** Parses an Open-Meteo `/v1/forecast` reply with current and daily weather. */
fun parseForecast(json: String, fetchedAt: Long): Forecast {
    val root = JSONObject(json)
    val current = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val dates = daily.getJSONArray("time")
    val codes = daily.getJSONArray("weather_code")
    val max = daily.getJSONArray("temperature_2m_max")
    val min = daily.getJSONArray("temperature_2m_min")
    val days = (0 until dates.length()).map { i ->
        DayForecast(
            LocalDate.parse(dates.getString(i)), weatherKind(codes.getInt(i)),
            max.getDouble(i).roundToInt(), min.getDouble(i).roundToInt(),
        )
    }
    return Forecast(
        current.getDouble("temperature_2m").roundToInt(), weatherKind(current.getInt("weather_code")),
        current.getInt("is_day") == 1, days, fetchedAt,
    )
}

/**
 * Current weather and the week ahead for where the car is, from Open-Meteo (free, no key). Only
 * the rounded coordinates leave the car. Fetched when the widget shows, at most every
 * [MAX_AGE_MS].
 */
class WeatherRepository(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(WeatherState())
    val state: StateFlow<WeatherState> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        val last = _state.value.forecast
        if (job?.isActive == true || (last != null && SystemClock.elapsedRealtime() - last.fetchedAt < MAX_AGE_MS)) return
        job = scope.launch {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                _state.value = _state.value.copy(problem = WeatherProblem.NO_PERMISSION)
                return@launch
            }
            val where = location()?.let { it.latitude to it.longitude }
                // The emulator usually has no fix; a fixed city keeps the widget testable there.
                ?: if (BuildConfig.USE_MOCK) -23.55 to -46.63 else null
            if (where == null) {
                _state.value = _state.value.copy(problem = WeatherProblem.NO_LOCATION)
                return@launch
            }
            val forecast = withContext(Dispatchers.IO) {
                runCatching { fetch(where.first, where.second) }.onFailure { Log.w(TAG, "Forecast failed", it) }
                    .getOrNull()?.copy(place = placeName(where.first, where.second))
            }
            _state.value = if (forecast != null) WeatherState(forecast) else _state.value.copy(problem = WeatherProblem.UNAVAILABLE)
        }
    }

    // Callers check ACCESS_FINE_LOCATION first.
    @SuppressLint("MissingPermission")
    private suspend fun location(): Location? {
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true).filter { it != LocationManager.PASSIVE_PROVIDER }
        providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }?.let { return it }
        val provider = providers.firstOrNull { it == LocationManager.GPS_PROVIDER } ?: providers.firstOrNull() ?: return null
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching { lm.getCurrentLocation(provider, signal, context.mainExecutor) { cont.resume(it) } }
                    .onFailure { cont.resume(null) }
            }
        }
    }

    /** The platform's reverse geocoder, on the device; null where it has no backend. */
    @Suppress("DEPRECATION") // the callback form is API 33+; this runs on the IO dispatcher
    private fun placeName(lat: Double, lon: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        Geocoder(context).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
    }.getOrNull()

    private fun fetch(lat: Double, lon: Double): Forecast {
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f" +
                    "&current=temperature_2m,weather_code,is_day" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7",
                lat, lon,
            )
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        val body = try {
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        return parseForecast(body, SystemClock.elapsedRealtime())
    }

    companion object {
        private const val MAX_AGE_MS = 30 * 60_000L
        private const val LOCATION_TIMEOUT_MS = 15_000L
        private const val TIMEOUT_MS = 10_000
        // Open-Meteo sometimes takes 15 s to answer; the connection itself is quick.
        private const val READ_TIMEOUT_MS = 30_000
        private const val TAG = "QsWeather"
    }
}
