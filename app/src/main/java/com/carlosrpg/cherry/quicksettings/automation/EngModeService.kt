package com.carlosrpg.cherry.quicksettings.automation

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.carlosrpg.cherry.quicksettings.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns on the head unit's Wi-Fi hotspot or adb by driving its hidden engineering app (EngMode):
 * open it, tap through to the switch, tap "Open", back out. Behind a full-screen cover, so the
 * menus never show. Wi-Fi is what wireless Android Auto and CarPlay need, and the car turns it
 * off again on its own.
 *
 * Runs when asked from settings, and on its own when the owner switched it on there: adb once
 * after each boot; Wi-Fi after boot, when it goes off, and when Android Auto or CarPlay comes to
 * the front, each time only while it is off and at most once a minute.
 */
class EngModeService : AccessibilityService() {

    enum class Flow { WIFI, ADB }

    private enum class Step { IDLE, CLICK_WIFI, CLICK_ENCRYPT, FILL_PW, CONFIRM_PW, CLICK_ADB, CLICK_OPEN, CLOSE }

    private val handler = Handler(Looper.getMainLooper())
    private var flow = Flow.WIFI
    private var step = Step.IDLE
    private var runDeadline = 0L
    private var stepDeadline = 0L
    private var closeAttempts = 0
    private var missTicks = 0
    private var scrollForward = true
    private var overlay: View? = null
    private var password = ""
    private var returnPkg: String? = null
    private var queued: Flow? = null
    private var lastWifiRun: Long? = null

    private val wifiChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)
            if (state == WifiManager.WIFI_STATE_DISABLED) handler.postDelayed({ autoWifi("wifi went off") }, WIFI_OFF_GRACE_MS)
        }
    }

    override fun onServiceConnected() {
        instance = this
        _running.value = true
        registerReceiver(wifiChanges, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))
        consumeBoot()
    }

    override fun onDestroy() {
        instance = null
        _running.value = false
        runCatching { unregisterReceiver(wifiChanges) }
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in PROJECTION_PKGS) autoWifi("projection $pkg", returnTo = pkg)
    }

    /** After a boot (armed by the boot receiver): adb first if asked for, then Wi-Fi. */
    fun consumeBoot() {
        val store = AutomationStore(this)
        if (!store.takeBootPending()) return
        handler.postDelayed({
            if (store.adbOnStartup) start(Flow.ADB, auto = true)
            autoWifi("startup")
        }, BOOT_DELAY_MS)
    }

    /** Settings buttons: run now, under the cover, whatever the state. */
    fun runNow(which: Flow) = start(which, auto = false)

    private fun autoWifi(reason: String, returnTo: String? = null) {
        if (!AutomationStore(this).autoWifi || wifiOn(this)) return
        val now = SystemClock.elapsedRealtime()
        if (lastWifiRun?.let { now - it < COOLDOWN_MS } == true) return
        Log.i(TAG, "Wi-Fi off ($reason): opening it")
        if (step != Step.IDLE) {
            queued = Flow.WIFI
            return
        }
        lastWifiRun = now
        start(Flow.WIFI, auto = true, returnTo = returnTo)
    }

    private fun start(which: Flow, auto: Boolean, returnTo: String? = null) {
        if (step != Step.IDLE) {
            if (auto) queued = which else toast(getString(R.string.auto_err_busy))
            return
        }
        val launch = launchIntentFor(packageManager) ?: return toast(getString(R.string.auto_err_no_engmode))
        if (which == Flow.ADB) {
            password = SecurePassword.load(AutomationStore.prefs(this))?.takeIf { it.isNotEmpty() }
                ?: return toast(getString(R.string.auto_err_no_password))
        }
        Log.i(TAG, "Running $which in ${launch.component}")
        flow = which
        returnPkg = returnTo
        _active.value = which
        showOverlay()
        try {
            // CLEAR_TASK so EngMode always starts on its main screen.
            startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (e: Exception) {
            finishRun()
            return toast(getString(R.string.auto_err_launch, e.message))
        }
        closeAttempts = 0
        runDeadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        advance(if (which == Flow.WIFI) Step.CLICK_WIFI else Step.CLICK_ENCRYPT)
        handler.postDelayed(::tick, POLL_MS)
    }

    private fun advance(next: Step) {
        step = next
        missTicks = 0
        scrollForward = true
        stepDeadline = SystemClock.uptimeMillis() + STEP_TIMEOUT_MS
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        if (step != Step.CLOSE && (now > runDeadline || now > stepDeadline)) onStepTimeout()
        val root = engModeRoot()
        when (step) {
            Step.IDLE -> return
            Step.CLICK_WIFI -> if (root != null && clickText(root, "WIFI", exact = false)) advance(Step.CLICK_OPEN)
            Step.CLICK_ENCRYPT -> if (root != null && clickOrScroll(root, "Encryption") { it.contains("encryption", true) }) advance(Step.FILL_PW)
            Step.FILL_PW -> if (root != null && fillPassword(root)) advance(Step.CONFIRM_PW)
            Step.CONFIRM_PW -> if (root != null && (clickText(root, "Confirm", exact = true) || clickText(root, "OK", exact = true))) advance(Step.CLICK_ADB)
            // "ADB" or "ADB Switch"; never "Close ADB", "Open ADB", "Current State ADB"...
            Step.CLICK_ADB -> if (root != null && clickOrScroll(root, "ADB") { ADB_ITEM.matches(it) }) advance(Step.CLICK_OPEN)
            Step.CLICK_OPEN -> if (root != null && clickOrScroll(root, "Open") { it.equals("Open", true) }) advance(Step.CLOSE)
            Step.CLOSE -> {
                // BACK until EngMode is no longer on screen (screen 2 -> screen 1 -> exit, plus dialogs).
                if (root == null || closeAttempts >= MAX_BACKS) {
                    Log.i(TAG, "Done ($flow)")
                    finishRun()
                    return
                }
                closeAttempts++
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
        handler.postDelayed(::tick, POLL_MS)
    }

    /** The password dialog is skippable (EngMode already unlocked); any other stalled step fails the run. */
    private fun onStepTimeout() {
        when (step) {
            Step.FILL_PW, Step.CONFIRM_PW -> {
                advance(Step.CLICK_ADB)
                return
            }
            else -> Unit
        }
        val what = when (step) {
            Step.CLICK_WIFI -> "WIFI"
            Step.CLICK_ENCRYPT -> "Encryption"
            Step.CLICK_ADB -> "ADB"
            Step.CLICK_OPEN -> "Open"
            else -> "?"
        }
        toast(if (engModeRoot() == null) getString(R.string.auto_err_not_shown) else getString(R.string.auto_err_button, what))
        step = Step.CLOSE
        stepDeadline = SystemClock.uptimeMillis() + STEP_TIMEOUT_MS
    }

    private fun finishRun() {
        step = Step.IDLE
        password = ""
        _active.value = null
        val back = returnPkg?.let { packageManager.getLaunchIntentForPackage(it) }
        returnPkg = null
        if (back != null && runCatching { startActivity(back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) {
            // Keep the cover up while the projection app redraws, so the launcher never flashes.
            handler.postDelayed(::hideOverlay, RETURN_OVERLAY_MS)
        } else {
            hideOverlay()
        }
        queued?.let { next ->
            queued = null
            handler.postDelayed({ if (next == Flow.WIFI) autoWifi("queued") else start(next, auto = true) }, QUEUE_GAP_MS)
        }
    }

    private fun engModeRoot(): AccessibilityNodeInfo? =
        windows.firstNotNullOfOrNull { w -> w.root?.takeIf { it.packageName == ENGMODE_PKG } }

    /** Clicks the first node whose text matches, through its nearest clickable ancestor. */
    private fun clickText(root: AccessibilityNodeInfo, text: String, exact: Boolean): Boolean {
        val node = root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { !exact || it.text?.toString()?.trim().equals(text, ignoreCase = true) } ?: return false
        return clickNode(node)
    }

    /** EngMode's list only renders visible rows: scroll it while the item is not on screen yet. */
    private fun clickOrScroll(root: AccessibilityNodeInfo, query: String, match: (String) -> Boolean): Boolean {
        val node = root.findAccessibilityNodeInfosByText(query).firstOrNull { n ->
            n.text?.toString()?.trim()?.let(match) == true
        }
        if (node != null) {
            missTicks = 0
            return clickNode(node)
        }
        if (++missTicks % SCROLL_EVERY_TICKS == 0) {
            val list = firstMatching(root) { it.isScrollable } ?: return false
            val action = if (scrollForward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (!list.performAction(action)) scrollForward = !scrollForward
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        val ok = target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        Log.i(TAG, "click '${node.text}' -> $ok")
        return ok
    }

    /** Types the password into the dialog's first editable field. Never logged. */
    private fun fillPassword(root: AccessibilityNodeInfo): Boolean {
        val field = firstMatching(root) { it.isEditable } ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, password) }
        return field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun firstMatching(node: AccessibilityNodeInfo?, test: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (node == null) return null
        if (test(node)) return node
        for (i in 0 until node.childCount) firstMatching(node.getChild(i), test)?.let { return it }
        return null
    }

    /** Full-screen cover in the app's style. Clicks still reach EngMode underneath. */
    private fun showOverlay() {
        if (overlay != null) return
        val dp = resources.displayMetrics.density
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(0x0B, 0x10, 0x18))
            val icon = if (flow == Flow.ADB) AdbAnimationView(context) else WifiAnimationView(context)
            addView(icon, LinearLayout.LayoutParams((140 * dp).toInt(), (140 * dp).toInt()))
            addView(TextView(context).apply {
                setText(if (flow == Flow.ADB) R.string.auto_enabling_adb else R.string.auto_opening_wifi)
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(0xEE, 0xF2, 0xF7))
                textSize = 24f
                setPadding(0, (20 * dp).toInt(), 0, 0)
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable and not touchable: EngMode must stay the active window.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        getSystemService(WindowManager::class.java).addView(view, params)
        overlay = view
    }

    private fun hideOverlay() {
        overlay?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        overlay = null
    }

    private fun toast(message: String) {
        Log.w(TAG, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val TAG = "QsEngMode"

        /** The head unit's engineering app. */
        const val ENGMODE_PKG = "com.desaysv.engmode"

        /** Android Auto receiver and CarPlay app on this head unit. */
        val PROJECTION_PKGS = setOf("com.google.android.projection.sink", "com.desaysv.vehicle.carplayapp")

        private const val COOLDOWN_MS = 60_000L
        private const val POLL_MS = 300L
        private const val TIMEOUT_MS = 45_000L
        private const val STEP_TIMEOUT_MS = 12_000L
        private const val BOOT_DELAY_MS = 8_000L
        private const val WIFI_OFF_GRACE_MS = 3_000L
        private const val RETURN_OVERLAY_MS = 800L
        private const val QUEUE_GAP_MS = 1_500L
        private const val MAX_BACKS = 6
        private const val SCROLL_EVERY_TICKS = 3
        private val ADB_ITEM = Regex("""ADB(\s*Switch)?""", RegexOption.IGNORE_CASE)

        @Volatile var instance: EngModeService? = null
            private set

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _active = MutableStateFlow<Flow?>(null)

        /** The run in progress, if any. */
        val active: StateFlow<Flow?> = _active.asStateFlow()

        fun wifiOn(context: Context): Boolean =
            context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled == true

        fun adbOn(context: Context): Boolean =
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1

        /** EngMode has a launcher activity on this head unit; fall back to its first open one. */
        fun launchIntentFor(pm: PackageManager): Intent? {
            pm.getLaunchIntentForPackage(ENGMODE_PKG)?.let { return it }
            val activities = runCatching { pm.getPackageInfo(ENGMODE_PKG, PackageManager.GET_ACTIVITIES).activities }.getOrNull()
            val activity = activities?.firstOrNull { it.exported && it.enabled && it.permission == null } ?: return null
            return Intent().setClassName(ENGMODE_PKG, activity.name)
        }
    }
}
