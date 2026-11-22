package com.carlosrpg.cherry.quicksettings

import android.os.Looper
import android.os.Handler
import android.app.Application
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.CarInfoProxy
import app.jconfig.carbus.cluster.ClusterMedia
import app.jconfig.carbus.cluster.ClusterNavigation
import app.jconfig.carbus.media.CarMedia
import app.jconfig.carbus.media.VdbusCarMedia
import app.jconfig.carbus.mock.MockCarMedia
import com.carlosrpg.cherry.quicksettings.automation.AutomationStore
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.cluster.ClusterProjection
import com.carlosrpg.cherry.quicksettings.car.seededMockBus
import com.carlosrpg.cherry.quicksettings.media.CarClusterMediaSink
import com.carlosrpg.cherry.quicksettings.media.ClusterMediaSync
import com.carlosrpg.cherry.quicksettings.media.LogClusterMediaSink
import com.carlosrpg.cherry.quicksettings.media.MediaRepository
import com.carlosrpg.cherry.quicksettings.overlay.OverlayService
import com.carlosrpg.cherry.quicksettings.phone.PhoneRegistry
import com.carlosrpg.cherry.quicksettings.settings.SettingsStore
import com.carlosrpg.cherry.quicksettings.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Process-wide singletons shared by the overlay service and the settings screen. */
class QuickSettingsApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var car: CarRepository private set
    lateinit var media: MediaRepository private set
    lateinit var settings: SettingsStore private set
    lateinit var cluster: ClusterProjection private set
    /** Writes an app's track to the instrument cluster when the setting is on; logs it off the car. */
    lateinit var clusterMedia: ClusterMediaSync private set
    val weather by lazy { WeatherRepository(this, scope) }
    /** The phones allowed to control the car, and the pairing in progress. */
    val phones by lazy {
        val prefs = getSharedPreferences("phones", MODE_PRIVATE)
        PhoneRegistry(load = { prefs.getString("registry", null) }, save = { prefs.edit().putString("registry", it).apply() })
    }

    /**
     * Asks the car's climate app to hide its panel now, as the car's own settings app does; the
     * close request on HVAC item 9 that follows keeps it from coming back. Best effort: the
     * service may refuse a third-party app.
     */
    private fun hideClimatePanel() {
        try {
            startService(
                android.content.Intent().setClassName("com.desaysv.svhvac", "com.desaysv.svhvac.service.HvacService")
                    .putExtra("type", "com.desaysv.svhvac.CLOSE_PANEL")
            )
        } catch (e: Exception) {
            // Not exported to us, or the app is missing (emulator).
        }
    }

    override fun onCreate() {
        super.onCreate()
        val bus: CarBus = if (BuildConfig.USE_MOCK) seededMockBus() else CarInfoProxy
        bus.init(this) // must run on the main thread or the proxy never connects
        car = CarRepository(bus, scope, hideClimatePanel = ::hideClimatePanel)
        // The head unit's own sources (Bluetooth, CarPlay, Android Auto, ...) never publish an
        // Android media session; they come from the vendor media service.
        val carMedia: CarMedia = if (BuildConfig.USE_MOCK) MockCarMedia() else VdbusCarMedia.create(this)
        carMedia.bind()
        media = MediaRepository(this, scope, carMedia)
        settings = SettingsStore(this)
        // The car-side signals for showing an app on the instrument cluster; none off the car.
        val clusterSignals = if (BuildConfig.USE_MOCK) null else ClusterNavigation.create(this).also { it.bind() }
        cluster = ClusterProjection(this, clusterSignals)
        val clusterSink = if (BuildConfig.USE_MOCK) LogClusterMediaSink() else CarClusterMediaSink(ClusterMedia.create(this).also { it.bind() })
        clusterMedia = ClusterMediaSync(clusterSink)
        // Always on: whatever brought the process up (boot, the system re-binding the
        // notification listener after the process was killed, an app update) also brings the
        // drawer back. No-op until the overlay permission is granted.
        OverlayService.start(this)
        // A force-stop takes the app's accessibility services (automation, tap pass-through) off
        // the list. Give the system time to bind them as usual (boot, an ordinary start) before
        // forcing a rebind, which would otherwise cut off a service that was just connecting and
        // its after-boot tasks.
        Handler(Looper.getMainLooper()).postDelayed({ AutomationStore.enableService(this) }, SERVICE_BIND_GRACE_MS)
    }
}

val android.content.Context.app: QuickSettingsApp
    get() = applicationContext as QuickSettingsApp

/** How long the system gets to bind the automation service itself after the app starts. */
private const val SERVICE_BIND_GRACE_MS = 5_000L
