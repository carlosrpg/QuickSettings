package com.carlosrpg.cherry.quicksettings.automation

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.carlosrpg.cherry.quicksettings.overlay.TapRelayService

/** The owner's choices for the EngMode automation, and turning its service on. */
class AutomationStore(private val context: Context) {
    private val prefs = prefs(context)

    /** Open Wi-Fi [wifiDelaySeconds] after startup, [wifiDevice] connecting, or Android Auto / CarPlay, while that device is connected and Wi-Fi is off. */
    var autoWifi: Boolean
        get() = prefs.getBoolean(KEY_AUTO_WIFI, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_WIFI, value).apply()

    /** Address of the paired Bluetooth device whose connection opens Wi-Fi; null = none chosen. */
    var wifiDevice: String?
        get() = prefs.getString(KEY_WIFI_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_WIFI_DEVICE, value).apply()

    /** From a Wi-Fi trigger to its one attempt (the phone and the car settle first); 0 = at once. */
    var wifiDelaySeconds: Int
        get() = prefs.getInt(KEY_WIFI_DELAY, 10)
        set(value) = prefs.edit().putInt(KEY_WIFI_DELAY, value).apply()

    /** Enable adb once after every boot. */
    var adbOnStartup: Boolean
        get() = prefs.getBoolean(KEY_ADB_ON_STARTUP, false)
        set(value) = prefs.edit().putBoolean(KEY_ADB_ON_STARTUP, value).apply()

    /** EngMode's encryption password, needed to reach its adb switch. Stored encrypted. */
    var password: String
        get() = SecurePassword.load(prefs).orEmpty()
        set(value) = SecurePassword.save(prefs, value)

    /** Set by the boot receiver, taken once by the service after it connects. */
    fun armBoot() = prefs.edit().putBoolean(KEY_BOOT_PENDING, true).apply()

    val bootPending: Boolean get() = prefs.getBoolean(KEY_BOOT_PENDING, false)

    fun takeBootPending(): Boolean {
        if (!prefs.getBoolean(KEY_BOOT_PENDING, false)) return false
        prefs.edit().putBoolean(KEY_BOOT_PENDING, false).apply()
        return true
    }

    companion object {
        private const val KEY_AUTO_WIFI = "auto_wifi"
        private const val KEY_WIFI_DEVICE = "wifi_bt_device"
        private const val KEY_WIFI_DELAY = "wifi_delay_seconds"
        private const val KEY_ADB_ON_STARTUP = "adb_on_startup"
        private const val KEY_BOOT_PENDING = "boot_pending"
        private const val RELIST_STEP_MS = 1_000L

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("automation", Context.MODE_PRIVATE)

        /** Whether the one-time adb grant that lets this app switch its own service on is in place. */
        fun canEnableItself(context: Context): Boolean =
            context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

        /**
         * Switches the app's accessibility services on, the automation and the tap pass-through
         * (the top bar's pull-down zone needs it), keeping every other service listed. A
         * force-stop (or a memory cleaner) takes both off the list and Android never brings them
         * back, so the app calls this on every start. Android 11 only rebinds a service it saw
         * crash after it went from listed to unlisted, so each unbound one is unlisted and listed
         * again; a bound one is left alone.
         */
        fun enableService(context: Context): Boolean {
            if (!canEnableItself(context)) return false
            val missing = listOfNotNull(
                ComponentName(context, EngModeService::class.java).takeIf { EngModeService.instance == null },
                ComponentName(context, TapRelayService::class.java).takeIf { TapRelayService.instance == null },
            )
            if (missing.isEmpty()) return true
            val resolver = context.contentResolver
            val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            val others = Settings.Secure.getString(resolver, key).orEmpty().split(':')
                .filter { it.isNotBlank() && ComponentName.unflattenFromString(it) !in missing }
            val with = (others + missing.map { it.flattenToShortString() }).joinToString(":")
            val without = others.joinToString(":")
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Settings.Secure.putString(resolver, key, with)
            val main = Handler(Looper.getMainLooper())
            main.postDelayed({ Settings.Secure.putString(resolver, key, without) }, RELIST_STEP_MS)
            main.postDelayed({ Settings.Secure.putString(resolver, key, with) }, 2 * RELIST_STEP_MS)
            return true
        }
    }
}
