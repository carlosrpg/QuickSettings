package com.carlosrpg.cherry.quicksettings.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.WindowInsets
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.app
import com.carlosrpg.cherry.quicksettings.settings.QsConfig
import com.carlosrpg.cherry.quicksettings.settings.SettingsActivity
import com.carlosrpg.cherry.quicksettings.settings.Side
import com.carlosrpg.cherry.quicksettings.ui.DrawerActions
import com.carlosrpg.cherry.quicksettings.ui.DrawerMode
import com.carlosrpg.cherry.quicksettings.ui.DrawerRoot
import com.carlosrpg.cherry.quicksettings.ui.TopBar
import com.carlosrpg.cherry.quicksettings.voice.VoiceInput
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * Hosts the drawer over every app: a narrow edge window (hint + swipe zone) while closed and
 * a Compose window while semi-open or open. Car and media data are polled only while the
 * drawer is visible.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner {
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private lateinit var wm: WindowManager
    private lateinit var voice: VoiceInput
    private lateinit var actions: DrawerActions
    private val handler = Handler(Looper.getMainLooper())

    private var edgeView: EdgeTouchView? = null
    private var drawerView: ComposeView? = null
    private var topBarView: ComposeView? = null
    private var topZoneView: TopSwipeView? = null
    private lateinit var fullScreen: FullScreenDetector
    private var polls = 0
    /** The car and media states the top bar last drew, and how many checks it has lagged. */
    private var drawnCar: Any? = null
    private var drawnMedia: Any? = null
    private var lagChecks = 0
    /** The open drawer came down from the top bar, so it slides on the vertical axis. */
    private var fromTop by mutableStateOf(false)
    /** A full-screen app (Android Auto, video) has hidden the status bar: the top bar steps aside. */
    private var fullScreenApp by mutableStateOf(false)
    private var mode by mutableStateOf<DrawerMode?>(null)
    private var closing by mutableStateOf(false)
    private var config by mutableStateOf(QsConfig())
    private var lastSpeed = 0

    private val autoClose = Runnable { close() }

    override fun onCreate() {
        savedState.performRestore(null)
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification())
        wm = getSystemService(WindowManager::class.java)
        voice = VoiceInput(this)
        actions = DrawerActions(
            context = this,
            car = app.car,
            media = app.media,
            voice = voice,
            toSemi = { show(DrawerMode.SEMI) },
            toOpen = { show(DrawerMode.OPEN) },
            close = ::close,
            openSettings = {
                close()
                startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
        config = app.settings.config.value
        fullScreen = FullScreenDetector(this)
        addEdge()
        updateTopBar()
        lifecycleScope.launch {
            app.settings.config.collectLatest {
                config = it
                if (it.clusterMediaSync) app.media.start() else app.clusterMedia.reset()
                releaseFeedsIfIdle()
                updateEdge()
                updateTopBar()
                drawerView?.let { view -> wm.updateViewLayout(view, drawerParams()) }
            }
        }
        lifecycleScope.launch {
            // The cluster's media card, for an app that plays on its own (setting, off by default).
            while (isActive) {
                if (config.clusterMediaSync) {
                    val change = withContext(Dispatchers.IO) { app.clusterMedia.tick(app.media.state.value) }
                    change?.let { Log.i(TAG_CLUSTER, it) }
                }
                delay(CLUSTER_SYNC_MS)
            }
        }
        lifecycleScope.launch {
            app.car.state.collectLatest { car ->
                val speed = car.speedKmh ?: 0
                // Fully open covers the screen: put it away when the car starts moving.
                if (mode == DrawerMode.OPEN && lastSpeed == 0 && speed > 0) close()
                lastSpeed = speed
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        voice.cancel()
        removeDrawer()
        edgeView?.let(wm::removeView)
        edgeView = null
        topBarView?.let(wm::removeView)
        topBarView = null
        topZoneView?.let(wm::removeView)
        topZoneView = null
        app.car.stopPolling()
        app.media.stop()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateEdge()
        drawerView?.let { wm.updateViewLayout(it, drawerParams()) }
        topBarView?.let { wm.updateViewLayout(it, topBarParams()) }
        topZoneView?.let { wm.updateViewLayout(it, topZoneParams()) }
    }

    // --- edge window -------------------------------------------------------------------

    private fun addEdge() {
        val view = EdgeTouchView(this, config.side, config.tapToOpen, onOpen = { show(DrawerMode.SEMI) }, onTap = ::relayTap)
        edgeView = view
        wm.addView(view, edgeParams())
    }

    private fun updateEdge() {
        val view = edgeView ?: return
        view.side = config.side
        view.tapOpens = config.tapToOpen
        view.visibility = if (mode == null) View.VISIBLE else View.GONE
        wm.updateViewLayout(view, edgeParams())
        view.invalidate()
    }

    private fun edgeParams(touchable: Boolean = true) = WindowManager.LayoutParams(
        // Tap to open: a handle-sized target, so it covers little of the app underneath.
        // Swipe only: a long, narrow zone.
        px(if (config.tapToOpen) EDGE_TAP_WIDTH_DP else EDGE_WIDTH_DP),
        if (config.tapToOpen) px(EDGE_TAP_HEIGHT_DP) else (usable().height() * EDGE_HEIGHT_FRACTION).toInt(),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        val area = usable()
        gravity = Gravity.TOP or if (config.side == Side.LEFT) Gravity.START else Gravity.END
        x = if (config.side == Side.LEFT) area.left else screenWidth() - area.right
        y = area.top + ((area.height() - height) * config.edgePosition.coerceIn(0f, 1f)).toInt()
        title = "QuickSettings edge"
    }

    /** Lets a tap in the zone reach the app below by replaying it while the zone is untouchable. */
    private fun relayTap(x: Float, y: Float) {
        val view = edgeView ?: return
        relay(view, x, y) { touchable -> edgeParams(touchable) }
    }

    private fun relay(view: View, x: Float, y: Float, params: (touchable: Boolean) -> WindowManager.LayoutParams) {
        val relay = TapRelayService.instance ?: return
        wm.updateViewLayout(view, params(false))
        handler.postDelayed({
            relay.tap(x, y) { handler.post { if (view.isAttachedToWindow) wm.updateViewLayout(view, params(true)) } }
        }, RELAY_DELAY_MS)
    }

    // --- top bar window ----------------------------------------------------------------

    /** Adds or removes the info strip in the status bar's area to follow the setting. */
    private fun updateTopBar() {
        val view = topBarView
        when {
            config.topBar && view == null -> {
                topBarView = ComposeView(this).apply {
                    setViewTreeLifecycleOwner(this@OverlayService)
                    setViewTreeSavedStateRegistryOwner(this@OverlayService)
                    setContent {
                        val car by app.car.state.collectAsState()
                        val media by app.media.state.collectAsState()
                        if (!fullScreenApp) TopBar(car, media, TOP_BAR_START_DP.dp, TOP_BAR_END_DP.dp)
                        SideEffect {
                            drawnCar = car
                            drawnMedia = media
                        }
                    }
                }.also { wm.addView(it, topBarParams()) }
                app.car.startPolling()
                app.media.start()
                handler.post(topBarTick)
            }
            config.topBar && view != null -> wm.updateViewLayout(view, topBarParams())
            !config.topBar && view != null -> {
                handler.removeCallbacks(topBarTick)
                wm.removeView(view)
                topBarView = null
                releaseFeedsIfIdle()
            }
        }
        updateTopZone()
    }

    /**
     * Once a second while the top bar is up: follow full-screen apps, and every few seconds
     * check that car and media data are still arriving, restarting a feed that has gone quiet
     * (nothing else restarts it while the drawer stays closed).
     */
    private val topBarTick = object : Runnable {
        override fun run() {
            val full = fullScreen.poll()
            if (full != fullScreenApp) {
                fullScreenApp = full
                updateTopZone()
            }
            if (++polls % WATCHDOG_EVERY == 0) watchdog()
            handler.postDelayed(this, TOP_BAR_POLL_MS)
        }
    }

    private fun watchdog() {
        val now = SystemClock.elapsedRealtime()
        if (!app.car.isConnected()) {
            Log.w(TAG, "car service not connected: car values cannot update")
        } else if (now - app.car.lastReadAt > STALE_MS) {
            Log.w(TAG, "car data stale for ${now - app.car.lastReadAt} ms: restarting its polling")
            app.car.restartPolling()
        }
        if (now - app.media.lastRefreshAt > STALE_MS) {
            Log.w(TAG, "media data stale for ${now - app.media.lastRefreshAt} ms: restarting it")
            app.media.restart()
        }
        // Fresh data the strip has not drawn on two checks in a row: the window stopped
        // recomposing. Build it again.
        val behind = drawnCar !== app.car.state.value || drawnMedia !== app.media.state.value
        lagChecks = if (behind) lagChecks + 1 else 0
        if (lagChecks >= 2) {
            Log.w(TAG, "top bar not redrawing new data: recreating its window")
            lagChecks = 0
            recreateTopBar()
        }
    }

    private fun recreateTopBar() {
        handler.removeCallbacks(topBarTick)
        topBarView?.let(wm::removeView)
        topBarView = null
        updateTopBar()
    }

    // --- top pull-down zone ---------------------------------------------------------------

    /** The swipe-down zone under the top bar: up while the bar shows and the drawer is closed. */
    private fun updateTopZone() {
        val wanted = topBarView != null && !fullScreenApp && mode == null
        val view = topZoneView
        when {
            wanted && view == null -> {
                topZoneView = TopSwipeView(this, onOpen = ::openFromTop, onTap = ::relayTopTap)
                    .also { wm.addView(it, topZoneParams()) }
            }
            wanted && view != null -> wm.updateViewLayout(view, topZoneParams())
            !wanted && view != null -> {
                wm.removeView(view)
                topZoneView = null
            }
        }
    }

    private fun openFromTop() {
        fromTop = true
        show(DrawerMode.OPEN)
    }

    private fun relayTopTap(x: Float, y: Float) {
        val view = topZoneView ?: return
        relay(view, x, y) { touchable -> topZoneParams(touchable) }
    }

    /** Centred under the top bar, narrower than the screen so the app keeps its corners. */
    private fun topZoneParams(touchable: Boolean = true) = WindowManager.LayoutParams(
        (screenWidth() * TOP_ZONE_WIDTH_FRACTION).toInt(),
        px(TOP_ZONE_HEIGHT_DP),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            (if (touchable) 0 else WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE),
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = usable().top.takeIf { it > 0 } ?: px(TOP_BAR_FALLBACK_DP)
        title = "QuickSettings top zone"
    }

    /**
     * Exactly the status bar's strip. An app overlay is a lower layer than the status bar, so
     * the car's clock and icons draw over this window and the car keeps those touches. It is
     * untouchable, so when a full-screen app hides the status bar the app gets the whole strip.
     */
    private fun topBarParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        usable().top.takeIf { it > 0 } ?: px(TOP_BAR_FALLBACK_DP),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
        title = "QuickSettings top bar"
    }

    // --- drawer window -----------------------------------------------------------------

    private fun show(target: DrawerMode) {
        handler.removeCallbacks(finishCloseFallback)
        closing = false
        val wasClosed = mode == null
        mode = target
        if (drawerView == null) {
            val view = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@OverlayService)
                setViewTreeSavedStateRegistryOwner(this@OverlayService)
                setContent {
                    val m = mode ?: return@setContent
                    DrawerRoot(
                        mode = m,
                        config = config,
                        actions = actions,
                        portrait = isPortrait(),
                        maxSemiHeight = (resources.configuration.screenHeightDp * SEMI_MAX_FRACTION).dp,
                        closing = closing,
                        fromTop = fromTop && m == DrawerMode.OPEN,
                        onClosed = ::finishClose,
                        onTouch = ::scheduleAutoClose,
                    )
                }
            }
            drawerView = view
            wm.addView(view, drawerParams())
        } else {
            wm.updateViewLayout(drawerView, drawerParams())
        }
        updateEdge()
        updateTopZone()
        if (wasClosed) {
            app.car.startPolling()
            app.media.start()
        }
        scheduleAutoClose()
    }

    /** Slides the drawer back into the edge; [finishClose] removes the window when that ends. */
    private fun close() {
        handler.removeCallbacks(autoClose)
        voice.cancel()
        if (drawerView == null) return finishClose()
        if (closing) return
        closing = true
        // Animations follow the system animator scale; give the fallback the same stretch.
        val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        handler.postDelayed(finishCloseFallback, (CLOSE_FALLBACK_MS * maxOf(scale, 1f)).toLong())
    }

    private val finishCloseFallback = Runnable { finishClose() }

    private fun finishClose() {
        handler.removeCallbacks(finishCloseFallback)
        if (mode == null && drawerView == null) return
        closing = false
        mode = null
        fromTop = false
        removeDrawer()
        updateEdge()
        updateTopZone()
        releaseFeedsIfIdle()
    }

    /**
     * Stops reading the car and media once nothing shows them: not the drawer, not the top bar.
     * Media keeps going while the cluster sync is on, since that follows it in the background.
     */
    private fun releaseFeedsIfIdle() {
        if (mode != null || topBarView != null) return
        app.car.stopPolling()
        if (!config.clusterMediaSync) app.media.stop()
    }

    private fun removeDrawer() {
        drawerView?.let(wm::removeView)
        drawerView = null
    }

    private fun scheduleAutoClose() {
        handler.removeCallbacks(autoClose)
        val delay = if (mode == DrawerMode.OPEN) OPEN_IDLE_MS else SEMI_IDLE_MS
        handler.postDelayed(autoClose, delay)
    }

    private fun drawerParams(): WindowManager.LayoutParams {
        val open = mode == DrawerMode.OPEN
        val margin = px(DRAWER_MARGIN_DP)
        val area = usable()
        return WindowManager.LayoutParams(
            // Flush with the edge (the near margin is padding inside the content), so the
            // slide in and out runs all the way to the screen edge instead of being clipped.
            if (open) area.width() - margin else WindowManager.LayoutParams.WRAP_CONTENT,
            if (open) area.height() - 2 * margin else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Not focusable and not touch-modal: touches outside the drawer still reach the app.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            val sideInset = if (config.side == Side.LEFT) area.left else screenWidth() - area.right
            gravity = (if (open) Gravity.TOP else Gravity.CENTER_VERTICAL) or
                if (config.side == Side.LEFT) Gravity.START else Gravity.END
            x = sideInset
            // Semi-open centres on the area between the system bars.
            y = if (open) area.top + margin else (area.top - (screenHeight() - area.bottom)) / 2
            title = "QuickSettings drawer"
        }
    }

    // --- helpers -----------------------------------------------------------------------

    private fun px(dp: Int) = (dp * resources.displayMetrics.density).toInt()
    private fun screenWidth() = wm.currentWindowMetrics.bounds.width()
    private fun screenHeight() = wm.currentWindowMetrics.bounds.height()

    /** Screen area between the status bar, the car's dock and any cutout, in screen pixels. */
    private fun usable(): Rect {
        val metrics = wm.currentWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
        )
        val b = metrics.bounds
        return Rect(b.left + insets.left, b.top + insets.top, b.right - insets.right, b.bottom - insets.bottom)
    }
    private fun isPortrait() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.overlay_channel), NotificationManager.IMPORTANCE_MIN)
        )
        val settings = PendingIntent.getActivity(
            this, 0, Intent(this, SettingsActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_drawer)
            .setContentTitle(getString(R.string.overlay_running))
            .setContentIntent(settings)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1
        private const val EDGE_WIDTH_DP = 36
        private const val EDGE_HEIGHT_FRACTION = 0.5f
        private const val EDGE_TAP_WIDTH_DP = 48
        private const val EDGE_TAP_HEIGHT_DP = 144
        private const val DRAWER_MARGIN_DP = 16
        // Clear of the car's voice avatar (left) and of its clock and status icons (right).
        private const val TOP_BAR_START_DP = 230
        private const val TOP_BAR_END_DP = 290
        private const val TOP_BAR_FALLBACK_DP = 100
        private const val TOP_BAR_POLL_MS = 1_000L
        private const val CLUSTER_SYNC_MS = 2_000L
        private const val TAG_CLUSTER = "QsClusterSync"
        private const val WATCHDOG_EVERY = 5
        private const val STALE_MS = 10_000L
        private const val TOP_ZONE_HEIGHT_DP = 24
        private const val TOP_ZONE_WIDTH_FRACTION = 0.6f
        private const val TAG = "QsTopBar"
        private const val SEMI_MAX_FRACTION = 0.8f
        private const val SEMI_IDLE_MS = 8_000L
        private const val OPEN_IDLE_MS = 20_000L
        private const val RELAY_DELAY_MS = 60L
        // The slide-out takes 220 ms; past this the window goes regardless.
        private const val CLOSE_FALLBACK_MS = 600L

        fun canDraw(context: Context) = Settings.canDrawOverlays(context)

        fun start(context: Context) {
            if (canDraw(context)) context.startForegroundService(Intent(context, OverlayService::class.java))
        }
    }
}
