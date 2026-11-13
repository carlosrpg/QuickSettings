package com.carlosrpg.cherry.quicksettings

import android.app.Application
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.CarInfoProxy
import app.jconfig.carbus.media.CarMedia
import app.jconfig.carbus.media.VdbusCarMedia
import app.jconfig.carbus.mock.MockCarMedia
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.car.seededMockBus
import com.carlosrpg.cherry.quicksettings.media.MediaRepository
import com.carlosrpg.cherry.quicksettings.overlay.OverlayService
import com.carlosrpg.cherry.quicksettings.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/** Process-wide singletons shared by the overlay service and the settings screen. */
class QuickSettingsApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var car: CarRepository private set
    lateinit var media: MediaRepository private set
    lateinit var settings: SettingsStore private set

    override fun onCreate() {
        super.onCreate()
        val bus: CarBus = if (BuildConfig.USE_MOCK) seededMockBus() else CarInfoProxy
        bus.init(this) // must run on the main thread or the proxy never connects
        car = CarRepository(bus, scope)
        // The head unit's own sources (Bluetooth, CarPlay, Android Auto, ...) never publish an
        // Android media session; they come from the vendor media service.
        val carMedia: CarMedia = if (BuildConfig.USE_MOCK) MockCarMedia() else VdbusCarMedia.create(this)
        carMedia.bind()
        media = MediaRepository(this, scope, carMedia)
        settings = SettingsStore(this)
        // Always on: whatever brought the process up (boot, the system re-binding the
        // notification listener after the process was killed, an app update) also brings the
        // drawer back. No-op until the overlay permission is granted.
        OverlayService.start(this)
    }
}

val android.content.Context.app: QuickSettingsApp
    get() = applicationContext as QuickSettingsApp
