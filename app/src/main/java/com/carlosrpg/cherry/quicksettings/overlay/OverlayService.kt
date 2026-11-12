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
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
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
import com.carlosrpg.cherry.quicksettings.voice.VoiceInput
import kotlinx.coroutines.flow.collectLatest
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
        addEdge()
        lifecycleScope.launch {
            app.settings.config.collectLatest {
                config = it
                updateEdge()
                drawerView?.let { view -> wm.updateViewLayout(view, drawerParams()) }
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
        app.car.stopPolling()
        app.media.stop()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateEdge()
        drawerView?.let { wm.updateViewLayout(it, drawerParams()) }
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
        val relay = TapRelayService.instance ?: return
        val view = edgeView ?: return
        wm.updateViewLayout(view, edgeParams(touchable = false))
        handler.postDelayed({
            relay.tap(x, y) { handler.post { edgeView?.let { wm.updateViewLayout(it, edgeParams()) } } }
        }, RELAY_DELAY_MS)
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
        removeDrawer()
        updateEdge()
        app.car.stopPolling()
        app.media.stop()
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
