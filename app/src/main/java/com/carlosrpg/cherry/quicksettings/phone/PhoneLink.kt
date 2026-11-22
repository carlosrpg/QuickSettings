package com.carlosrpg.cherry.quicksettings.phone

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.carlosrpg.cherry.quicksettings.BuildConfig
import android.os.SystemClock
import com.carlosrpg.cherry.quicksettings.app
import com.carlosrpg.cherry.quicksettings.media.VolumeChannel
import com.carlosrpg.cherry.quicksettings.nav.NavStore
import com.carlosrpg.cherry.quicksettings.ui.DrawerActions
import com.carlosrpg.cherry.quicksettings.util.AppInfo
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Phone control on the car's Wi-Fi: the [PhoneServer] with the drawer's state and actions behind
 * it. Lives in the overlay service, next to the drawer it mirrors; runs while the setting is on.
 */
class PhoneLink(private val context: Context, drawer: DrawerActions, onWatch: () -> Unit) {
    private val app = context.app
    private val images = ImageCache()

    private val commands = PhoneCommands(app.car, object : PhoneActions {
        override fun playPause() = app.media.playPause()
        override fun next() { app.media.next() }
        override fun previous() { app.media.previous() }
        override fun recentPackages() = app.media.state.value.recent.map { it.packageName }
        override fun playRecent(pkg: String) = app.media.playApp(pkg)
        override fun mediaSearch(query: String) = app.media.playFromSearch(query)
        override fun setVolume(channel: VolumeChannel, value: Int) = app.media.moveLevel(channel, value)
        override fun toggleMute() = app.media.toggleMute()
        override fun navSearch(query: String) = NavStore.searchDestination(context, query)
        override fun toggleNavOnCluster() = drawer.toggleNavOnCluster()
        override fun bringToFront(nav: Boolean) = if (nav) NavStore.openApp(context) else app.media.openApp()
    })

    private val backend = object : PhoneBackend {
        override fun state(): JSONObject {
            val media = app.media.state.value
            val positionAtMs = if (media.positionAt > 0) System.currentTimeMillis() - (SystemClock.elapsedRealtime() - media.positionAt) else 0
            return phoneStateJson(
                PhoneSnapshot(
                    connected = app.car.isConnected(),
                    car = app.car.state.value,
                    applyingDriver = app.car.cabinTempTarget.value,
                    applyingPassenger = app.car.passengerTempTarget.value,
                    applyingFan = app.car.fanTarget.value,
                    config = app.settings.config.value,
                    media = media,
                    positionAtMs = positionAtMs,
                    volumes = VolumeChannel.entries.associateWith { app.media.level(it) },
                    nav = NavStore.state.value,
                    navAppLabel = NavStore.navPackage(context)?.let { AppInfo.label(context, it) },
                    navOnCluster = app.cluster.onCluster.value != null,
                    weather = app.weather.state.value,
                ),
                images,
            )
        }

        override suspend fun command(action: String, args: JSONObject, remote: Boolean) = commands.run(action, args, remote)

        override fun image(key: String) = images.png(key)
    }

    val api = PhoneApi(
        registry = app.phones,
        backend = backend,
        asset = { name -> runCatching { context.assets.open(name).use { it.readBytes() } }.getOrNull() },
        commandContext = Dispatchers.Main.immediate,
        onWatch = onWatch,
        remoteAssist = { app.settings.config.value.remoteAssist },
    )

    private var server: PhoneServer? = null
    private var relay: RelayClient? = null
    private var relayWatch: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networkBack = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { relay?.wake() }
    }

    fun setEnabled(on: Boolean) {
        if (on && server == null) {
            server = PhoneServer(PhoneApi.PORT, { api.handle(it) }).takeIf { it.start() }
        } else if (!on) {
            server?.stop()
            server = null
        }
        _running.value = server != null
        setRelay(on && relayConfigured)
    }

    private fun setRelay(on: Boolean) {
        if (on && relay == null) {
            val client = RelayClient(BuildConfig.RELAY_URL, BuildConfig.RELAY_KEY, app.phones.carId) { api.handle(it, remote = true) }
            relay = client
            client.start()
            relayWatch = scope.launch { client.connected.collect { _relayConnected.value = it } }
            runCatching { connectivity?.registerDefaultNetworkCallback(networkBack) }
        } else if (!on && relay != null) {
            relay?.stop()
            relay = null
            relayWatch?.cancel()
            _relayConnected.value = false
            runCatching { connectivity?.unregisterNetworkCallback(networkBack) }
        }
    }

    companion object {
        /** This build carries a relay address and key (local.properties on the owner's machine). */
        val relayConfigured = BuildConfig.RELAY_URL.isNotEmpty() && BuildConfig.RELAY_KEY.isNotEmpty()

        private val _relayConnected = MutableStateFlow(false)
        /** The car's connection to the relay is up: phones can reach it over the internet. */
        val relayConnected: StateFlow<Boolean> = _relayConnected.asStateFlow()

        private val _running = MutableStateFlow(false)
        /** The server has its port open; the settings screen shows the address only then. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /**
         * The car's addresses on local networks (its Wi-Fi as a client, or its own hotspot),
         * Wi-Fi interfaces first.
         */
        fun lanAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
                .flatMap { nic -> nic.inetAddresses.toList().filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress } }
                .map { it.hostAddress.orEmpty() }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
