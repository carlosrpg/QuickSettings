package com.carlosrpg.cherry.quicksettings.nav

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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

    /**
     * Shape of the last notification seen from a navigation app, for the settings status
     * panel: when directions don't show, this says what the app actually posted.
     */
    @Volatile var lastSeen: String? = null
        internal set

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
        instance = this
        DebugLog.add("listener", "listener connected, ${activeNotifications.orEmpty().size} active")
        val navs = activeNotifications.orEmpty().filter(::isNavApp)
        navs.lastOrNull()?.let { NavStore.rememberApp(it.packageName) }
        NavStore.publish(navs.mapNotNull(::route).lastOrNull())
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        DebugLog.add(sbn.key, "+ " + describe(sbn))
        if (!isNavApp(sbn)) return
        NavStore.rememberApp(sbn.packageName)
        val route = route(sbn)
        when {
            route != null -> NavStore.publish(route)
            // The same app going back to an idle status means the route ended.
            NavStore.state.value?.packageName == sbn.packageName -> NavStore.publish(null)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        DebugLog.add(sbn.key, "- ${sbn.packageName}")
        val current = NavStore.state.value ?: return
        if (current.packageName == sbn.packageName && isNavApp(sbn)) NavStore.publish(null)
    }

    override fun onListenerDisconnected() {
        instance = null
        DebugLog.add("listener", "listener disconnected")
        NavStore.publish(null)
    }

    /** One line per notification currently posted, as the system reports them to this listener. */
    fun describeActive(): List<String> = try {
        activeNotifications.orEmpty().map(::describe)
    } catch (e: SecurityException) {
        listOf("not allowed: ${e.message}")
    }

    private fun describe(sbn: StatusBarNotification): String {
        val n = sbn.notification
        fun short(key: String) = n.extras.getCharSequence(key)?.toString()?.take(DEBUG_TEXT_CHARS)
        @Suppress("DEPRECATION")
        val ownLayout = n.contentView != null || n.bigContentView != null || n.headsUpContentView != null
        return "${sbn.packageName} cat=${n.category} ongoing=${sbn.isOngoing} icon=${n.getLargeIcon() != null} " +
            "layout=$ownLayout nav=${isNavApp(sbn)} \"${short(Notification.EXTRA_TITLE)}\" / \"${short(Notification.EXTRA_TEXT)}\""
    }

    /** A notification from a navigation app: its category says so, or it's a known app's ongoing one. */
    private fun isNavApp(sbn: StatusBarNotification): Boolean =
        sbn.notification.category == Notification.CATEGORY_NAVIGATION ||
            (sbn.packageName in NavStore.KNOWN_NAV_PACKAGES && sbn.isOngoing)

    /**
     * The route a nav app's notification describes, or null for an idle status one ("Driving
     * with Google Maps", Waze's "Running. Tap to open."). Two shapes are understood:
     *  - the standard one: title / text / large icon. A route has the manoeuvre arrow as its
     *    large icon, or is a navigation-category notification with an instruction in it;
     *  - a layout of the app's own (Waze draws its directions that way and leaves the standard
     *    fields empty): the texts and the biggest image are read from the layout itself.
     */
    private fun route(sbn: StatusBarNotification): NavState? {
        val n = sbn.notification
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val arrow = n.getLargeIcon()
        val custom = if (arrow == null) customContent(n) else null
        NavStore.lastSeen = "${sbn.packageName} cat=${n.category} title=${!title.isNullOrBlank()} " +
            "text=${!text.isNullOrBlank()} icon=${arrow != null} layout=${custom?.first?.size ?: "no"}"
        val standard = arrow != null || (n.category == Notification.CATEGORY_NAVIGATION && !text.isNullOrBlank())
        return when {
            standard -> NavState(
                packageName = sbn.packageName,
                appLabel = AppInfo.label(this, sbn.packageName),
                title = title,
                text = text,
                subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                maneuver = arrow?.loadDrawable(this)?.toBitmap(MANEUVER_PX),
            )
            // An instruction needs at least two texts (distance and street); one alone is a status line.
            custom != null && custom.first.size >= 2 -> NavState(
                packageName = sbn.packageName,
                appLabel = AppInfo.label(this, sbn.packageName),
                title = custom.first[0],
                text = custom.first[1],
                subText = custom.first.drop(2).joinToString(" \u00b7 ").takeIf { it.isNotBlank() },
                maneuver = custom.second,
            )
            else -> null
        }
    }

    /** The visible texts, top to bottom, and the biggest image of a notification's own layout. */
    private fun customContent(n: Notification): Pair<List<String>, Bitmap?>? {
        @Suppress("DEPRECATION")
        val views = n.bigContentView ?: n.contentView ?: n.headsUpContentView ?: return null
        return try {
            val texts = mutableListOf<String>()
            var image: Bitmap? = null
            var imageArea = 0
            fun walk(view: View) {
                if (view.visibility != View.VISIBLE) return
                when (view) {
                    is ViewGroup -> for (i in 0 until view.childCount) walk(view.getChildAt(i))
                    is TextView -> view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(texts::add)
                    is ImageView -> view.drawable?.let { drawable ->
                        val area = drawable.intrinsicWidth * drawable.intrinsicHeight
                        if (area > imageArea) {
                            imageArea = area
                            image = drawable.toBitmap(MANEUVER_PX)
                        }
                    }
                }
            }
            walk(views.apply(applicationContext, FrameLayout(this)))
            texts to image
        } catch (e: Exception) {
            // The layout belongs to another app and may not inflate here.
            null
        }
    }

    companion object {
        private const val MANEUVER_PX = 128
        private const val DEBUG_TEXT_CHARS = 28

        /** The connected listener, or null while the system has not bound it. */
        @Volatile var instance: QsNotificationListener? = null
            private set
    }
}
