package com.carlosrpg.cherry.quicksettings.overlay

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * Whether a full-screen app (Android Auto, a video) is up, polled from three independent
 * signals because an overlay window is not told reliably when the status bar hides:
 *
 *  - [navBarHidden]: the car's SystemUI copies the navigation bar's window state into the
 *    global setting [KEY_NAV_STATE] (0 showing, 1 hiding, 2 hidden). Readable by any app.
 *  - [foreground]: the last resumed activity from usage stats, matched against
 *    [FULL_SCREEN_ACTIVITIES]. Needs usage access, granted over adb; skipped without it.
 *  - [statusBarListed]: whether the status bar is in the accessibility window list (it
 *    drops out while hidden). Needs the tap relay service; skipped without it.
 *
 * Any one of them saying "full screen" is enough. Every change of any signal is logged under
 * [TAG], so the car's log shows which of them actually works on this firmware.
 */
class FullScreenDetector(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val appOps = context.getSystemService(AppOpsManager::class.java)

    /** Last resumed activity as "package/class", and how far usage events were read. */
    private var foreground: String? = null
    private var readUntil = 0L
    private var lastSignals: Signals? = null

    data class Signals(val navBarHidden: Boolean?, val foreground: String?, val statusBarListed: Boolean?) {
        val fullScreen: Boolean
            get() = navBarHidden == true || foreground in FULL_SCREEN_ACTIVITIES || statusBarListed == false
    }

    fun poll(): Boolean {
        val signals = Signals(navBarHidden(), readForeground(), TapRelayService.instance?.statusBarListed())
        if (signals != lastSignals) {
            val line = "nav=${signals.navBarHidden} fg=${signals.foreground} bar=${signals.statusBarListed} -> full screen ${signals.fullScreen}"
            Log.i(TAG, line)
            lastSignals = signals
            last = line
        }
        return signals.fullScreen
    }

    private fun navBarHidden(): Boolean? {
        val state = Settings.Global.getInt(context.contentResolver, KEY_NAV_STATE, -1)
        return if (state < 0) null else state != 0
    }

    private fun readForeground(): String? {
        if (!hasUsageAccess()) return null
        val now = System.currentTimeMillis()
        // First read looks back a while for the current app; later reads only take what is new.
        val from = if (readUntil == 0L) now - FIRST_LOOKBACK_MS else readUntil
        val events = usage.queryEvents(from, now) ?: return foreground
        val event = UsageEvents.Event()
        while (events.getNextEvent(event)) {
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                foreground = "${event.packageName}/${event.className}"
            }
        }
        readUntil = now
        return foreground
    }

    private fun hasUsageAccess(): Boolean = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
    ) == AppOpsManager.MODE_ALLOWED

    companion object {
        /** The latest signals, for the debug screen. */
        @Volatile var last: String = "not polled (top bar off)"
            private set
        private const val TAG = "QsFullScreen"
        const val KEY_NAV_STATE = "systemui_status_show_enable"
        private const val FIRST_LOOKBACK_MS = 60 * 60 * 1000L

        /** Activities that take the whole screen and hide the car's bars. */
        val FULL_SCREEN_ACTIVITIES = setOf(
            "com.google.android.projection.sink/com.desaysv.auto.projection.MainActivity",
        )
    }
}
