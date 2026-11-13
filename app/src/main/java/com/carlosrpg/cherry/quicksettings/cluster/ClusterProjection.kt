package com.carlosrpg.cherry.quicksettings.cluster

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import app.jconfig.carbus.cluster.ClusterNavigation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shows an app on the instrument cluster and brings it back. The cluster is a second Android
 * display: an app is "sent" there by starting its activity on that display, after waking the
 * display and telling the car to hand its navigation area over ([signals]); it comes back by
 * starting it on the centre display again. Nothing here runs on its own: both are button taps.
 *
 * Every step, its order and its values follow an app known to do this on this head unit; do
 * not simplify them without a car to test on.
 */
class ClusterProjection(private val context: Context, private val signals: ClusterNavigation?) {
    private val displays = context.getSystemService(DisplayManager::class.java)

    private val _onCluster = MutableStateFlow<String?>(null)

    /** Package this app last sent to the cluster and has not brought back. */
    val onCluster: StateFlow<String?> = _onCluster.asStateFlow()

    /**
     * The cluster's display. On the car it is display 2, else another 1920x720 one. Failing
     * both, any other display the system lists for apps is taken: that is the cluster on the
     * Automotive emulator (an "HDMI" display 1), which is how this can be tried off the car.
     * Null when the default display is the only one.
     */
    fun clusterDisplayId(): Int? {
        val others = displays.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        return (others.firstOrNull { it.displayId == CLUSTER_DISPLAY_ID }
            ?: others.firstOrNull { it.mode.physicalWidth == CLUSTER_WIDTH && it.mode.physicalHeight == CLUSTER_HEIGHT }
            ?: others.firstOrNull())
            ?.displayId
    }

    /** Every display the system lists for this app, as "id:WxH", for the debug screen. */
    fun describeDisplays(): String =
        displays.displays.joinToString(" ") { "${it.displayId}:${it.mode.physicalWidth}x${it.mode.physicalHeight}" }

    /** Sends [packageName] to the cluster. False when there is no cluster display or no such app. */
    fun sendToCluster(packageName: String): Boolean {
        val display = clusterDisplayId() ?: return false
        val launch = launchIntentFor(packageName) ?: return false
        return try {
            // A blank activity first: the cluster only shows its Android surface once something is on it.
            context.startActivity(
                Intent(context, ClusterWakeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                ActivityOptions.makeBasic().setLaunchDisplayId(display).toBundle(),
            )
            announceHandover()
            signals?.toCluster()
            context.startActivity(launch.setFlags(LAUNCH_FLAGS), fullscreenOn(display))
            _onCluster.value = packageName
            true
        } catch (e: RuntimeException) {
            // The system refused the display, or the app has no activity to start.
            false
        }
    }

    /** Brings the app this class sent to the cluster back to the centre display. */
    fun bringBack(): Boolean {
        val packageName = _onCluster.value ?: return false
        val launch = launchIntentFor(packageName)
        return try {
            signals?.toCenter()
            if (launch != null) context.startActivity(launch.setFlags(LAUNCH_FLAGS), fullscreenOn(Display.DEFAULT_DISPLAY))
            _onCluster.value = null
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    /** The stock navigation apps hold the cluster's navigation area; these ask them to let go. */
    private fun announceHandover() {
        context.sendBroadcast(Intent("AUTONAVI_STANDARD_BROADCAST_SEND").putExtra("KEY_TYPE", 10019).putExtra("EXTRA_STATE", 3))
        context.sendBroadcast(Intent("turbodog.navigation.system.message").putExtra("CODE", 207).putExtra("DATA", 0))
        context.sendBroadcast(Intent("lion.navigation.system.message").putExtra("CODE", 207))
        context.sendBroadcast(Intent("turbodog.navigation.system.message").putExtra("CODE", 206))
    }

    /** Full screen on [displayId], with any freeform or split-window placement cleared. */
    private fun fullscreenOn(displayId: Int): Bundle {
        val options = ActivityOptions.makeBasic().apply {
            launchDisplayId = displayId
            setLaunchBounds(null)
        }
        return (options.toBundle() ?: Bundle()).apply {
            putInt("android:windowingMode", WINDOWING_MODE_FULLSCREEN)
            putInt("android.activity.windowingMode", WINDOWING_MODE_FULLSCREEN)
            remove("android:launchBounds")
            remove("android.activity.launchBounds")
            putInt("android:freeformWindowSize", 0)
            putInt("android:multiWindowMode", 0)
            putBoolean("android:overrideTransition", false)
        }
    }

    /** The app's launcher activity, looked up directly when the package manager offers no launch intent. */
    private fun launchIntentFor(packageName: String): Intent? {
        val packages = context.packageManager
        packages.getLaunchIntentForPackage(packageName)?.let { return it }
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName)
        val resolved = packages.resolveActivity(query, 0) ?: return null
        return Intent(query).setClassName(packageName, resolved.activityInfo.name)
    }

    private companion object {
        const val CLUSTER_DISPLAY_ID = 2
        const val CLUSTER_WIDTH = 1920
        const val CLUSTER_HEIGHT = 720
        const val WINDOWING_MODE_FULLSCREEN = 1
        const val LAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
    }
}

/**
 * A transparent, untouchable, near-zero-brightness window shown for three seconds on the cluster
 * display. Starting it wakes the cluster's Android surface so the app that follows is visible.
 * The cluster keeps re-showing its system bars, so they are hidden again every 200 ms.
 */
class ClusterWakeActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())

    private val keepImmersive = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            hideSystemBars()
            handler.postDelayed(this, IMMERSIVE_REAPPLY_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        )
        window.attributes = window.attributes.apply { screenBrightness = DIM_BRIGHTNESS }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        handler.post(keepImmersive)
        handler.postDelayed(::finish, AUTO_FINISH_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        window.attributes = window.attributes.apply {
            screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_FULLSCREEN
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val DIM_BRIGHTNESS = 0.01f
        const val IMMERSIVE_REAPPLY_MS = 200L
        const val AUTO_FINISH_MS = 3_000L
    }
}
