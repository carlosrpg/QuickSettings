package com.carlosrpg.cherry.quicksettings.nav

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.carlosrpg.cherry.quicksettings.util.AppInfo
import com.carlosrpg.cherry.quicksettings.util.AppInfo.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the navigation app is currently announcing. */
data class NavState(
    val packageName: String,
    val appLabel: String?,
    /** Usually the distance to the next manoeuvre ("350 m"). */
    val title: String?,
    /** Usually the instruction or street ("Turn right onto Rua Augusta"). */
    val text: String?,
    /** Often ETA / remaining time, when the app provides it. */
    val subText: String?,
    /** The app's manoeuvre arrow when it sends one as the large icon. */
    val maneuver: Bitmap?,
)

object NavStore {
    private val _state = MutableStateFlow<NavState?>(null)
    val state: StateFlow<NavState?> = _state.asStateFlow()

    /** Last nav app seen; used to open it or send it a destination when no route is active. */
    @Volatile var lastNavPackage: String? = null
        private set

    internal fun publish(nav: NavState?) {
        nav?.let { lastNavPackage = it.packageName }
        _state.value = nav
    }

    internal fun rememberApp(pkg: String) {
        lastNavPackage = pkg
    }

    /** Nav app to talk to: the last one seen, else the first installed known one. */
    fun navPackage(context: Context): String? = state.value?.packageName ?: lastNavPackage
        ?: KNOWN_NAV_PACKAGES.firstOrNull { context.packageManager.getLaunchIntentForPackage(it) != null }

    fun openApp(context: Context): Boolean {
        val intent = navPackage(context)?.let { context.packageManager.getLaunchIntentForPackage(it) }
            ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Sends a spoken destination to the nav app as a `geo:` search. */
    fun searchDestination(context: Context, query: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        navPackage(context)?.let { pkg ->
            val targeted = Intent(intent).setPackage(pkg)
            if (targeted.resolveActivity(context.packageManager) != null) {
                context.startActivity(targeted)
                return true
            }
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(intent)
        return true
    }

    val KNOWN_NAV_PACKAGES = listOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.autonavi.amapauto",
        "com.autonavi.minimap",
        "com.sygic.aura",
        "net.osmand",
        "net.osmand.plus",
        "ru.yandex.yandexnavi",
        "com.here.app.maps",
        "com.tomtom.gplay.navapp",
    )
}

/**
 * Notification access serves two purposes: reading the navigation app's turn-by-turn
 * notification, and (through its component) seeing other apps' media sessions.
 */
class QsNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        val navs = activeNotifications.orEmpty().filter(::isNavApp)
        navs.lastOrNull()?.let { NavStore.rememberApp(it.packageName) }
        NavStore.publish(navs.lastOrNull(::hasRoute)?.let(::parse))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!isNavApp(sbn)) return
        NavStore.rememberApp(sbn.packageName)
        when {
            hasRoute(sbn) -> NavStore.publish(parse(sbn))
            // The same app going back to an idle status means the route ended.
            NavStore.state.value?.packageName == sbn.packageName -> NavStore.publish(null)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val current = NavStore.state.value ?: return
        if (current.packageName == sbn.packageName && isNavApp(sbn)) NavStore.publish(null)
    }

    override fun onListenerDisconnected() = NavStore.publish(null)

    /** A notification from a navigation app: its category says so, or it's a known app's ongoing one. */
    private fun isNavApp(sbn: StatusBarNotification): Boolean =
        sbn.notification.category == Notification.CATEGORY_NAVIGATION ||
            (sbn.packageName in NavStore.KNOWN_NAV_PACKAGES && sbn.isOngoing)

    // Idle status notifications ("Driving with Google Maps", Waze's "Running. Tap to open.")
    // carry no manoeuvre arrow. A route has the arrow, or is a navigation-category notification
    // with an instruction in it.
    private fun hasRoute(sbn: StatusBarNotification): Boolean =
        sbn.notification.getLargeIcon() != null ||
            (sbn.notification.category == Notification.CATEGORY_NAVIGATION &&
                !sbn.notification.extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank())

    private fun parse(sbn: StatusBarNotification): NavState {
        val extras = sbn.notification.extras
        return NavState(
            packageName = sbn.packageName,
            appLabel = AppInfo.label(this, sbn.packageName),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            maneuver = sbn.notification.getLargeIcon()?.loadDrawable(this)?.toBitmap(MANEUVER_PX),
        )
    }

    private companion object {
        const val MANEUVER_PX = 128
    }
}
