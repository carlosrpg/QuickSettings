package com.carlosrpg.cherry.quicksettings

import android.app.Application
import app.jconfig.carbus.carinfo.CarBus
import app.jconfig.carbus.carinfo.CarInfoProxy
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.car.seededMockBus
import com.carlosrpg.cherry.quicksettings.media.MediaRepository
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
        media = MediaRepository(this, scope)
        settings = SettingsStore(this)
    }
}

val android.content.Context.app: QuickSettingsApp
    get() = applicationContext as QuickSettingsApp
