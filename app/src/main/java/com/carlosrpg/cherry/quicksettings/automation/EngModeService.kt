package com.carlosrpg.cherry.quicksettings.automation

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
 * after each boot; Wi-Fi on startup, when the Bluetooth device chosen in settings connects, or
 * when Android Auto or CarPlay comes to the front. Each trigger does nothing while Wi-Fi is on or
 * the chosen device is not connected; otherwise one run [WIFI_DELAY_MS] later, if Wi-Fi is still
 * off then (Android Auto and CarPlay at most once a minute).
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
    private var queued: Flow? = null
    private var returnPkg: String? = null
    private var lastProjectionRun: Long? = null
    private var lastAutoWifiRun: Long? = null
    private var queuedReturn: String? = null

    /** The chosen device connecting or dropping. A drop cancels a pending attempt, so a link that flaps never stacks runs. */
    private val btChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (!device.address.equals(AutomationStore(context).wifiDevice, ignoreCase = true)) return
            when (intent.action) {
                // The broadcast itself proves the link: no need for the hidden isConnected().
                BluetoothDevice.ACTION_ACL_CONNECTED -> armWifi("${device.address} connected", connected = true)
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.i(TAG, "${device.address} disconnected: Wi-Fi attempt cancelled")
                    handler.removeCallbacks(wifiAttempt)
                    wifiReturnTo = null
                }
            }
        }
    }

    private var wifiReturnTo: String? = null
    private var wifiArmedFor: String? = null
    private val wifiAttempt = Runnable {
        // The owner picked another phone (or none) in settings while this was pending.
        if (AutomationStore(this).wifiDevice != wifiArmedFor) {
            Log.i(TAG, "chosen device changed: Wi-Fi attempt dropped")
        } else autoWifi("after ${WIFI_DELAY_MS / 1000} s", returnTo = wifiReturnTo.also { wifiReturnTo = null })
    }

    override fun onServiceConnected() {
        instance = this
        _running.value = true
        registerReceiver(btChanges, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        // The phone often links up with the car before this service binds (after a boot or a
        // restart of the app): the broadcast is gone by then, so look at the link itself.
        armWifi("startup", connected = chosenDeviceConnected())
        consumeBoot()
    }

    override fun onDestroy() {
        instance = null
        _running.value = false
        runCatching { unregisterReceiver(btChanges) }
        handler.removeCallbacksAndMessages(null)
        hideOverlay()
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in PROJECTION_PKGS) return
        // Android Auto comes to the front several times while it connects: the cooldown keeps
        // that to one attempt, and the delay lets it settle so it doesn't push EngMode away mid-run.
        val now = SystemClock.elapsedRealtime()
        if (lastProjectionRun?.let { now - it < PROJECTION_COOLDOWN_MS } == true) return
        if (armWifi("projection $pkg", connected = chosenDeviceConnected(), returnTo = pkg)) lastProjectionRun = now
    }

    /**
     * A Wi-Fi trigger: nothing while Wi-Fi is on or the chosen device is not connected; otherwise
     * one attempt [WIFI_DELAY_MS] from now, replacing any pending one. True when armed.
     */
    private fun armWifi(reason: String, connected: Boolean, returnTo: String? = null): Boolean {
        val skip = when {
            !AutomationStore(this).autoWifi -> "automatic Wi-Fi is off in settings"
            wifiOn(this) -> "Wi-Fi already on"
            !connected -> "the chosen device is not connected"
            else -> null
        }
        if (skip != null) {
            Log.i(TAG, "$reason: $skip")
            return false
        }
        Log.i(TAG, "$reason: Wi-Fi attempt in ${WIFI_DELAY_MS / 1000} s")
        // A Bluetooth reconnect while an Android Auto attempt waits keeps the way back to it.
        wifiReturnTo = returnTo ?: wifiReturnTo
        wifiArmedFor = AutomationStore(this).wifiDevice
        handler.removeCallbacks(wifiAttempt)
        handler.postDelayed(wifiAttempt, WIFI_DELAY_MS)
        return true
    }

    /** Whether the device chosen in settings has a Bluetooth link with the car right now. */
    // BLUETOOTH (install-time on Android 11, the car's version) covers this; lint asks for
    // Android 12's BLUETOOTH_CONNECT. A SecurityException still lands in runCatching.
    @SuppressLint("MissingPermission")
    private fun chosenDeviceConnected(): Boolean {
        val address = AutomationStore(this).wifiDevice ?: return false
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        val device = runCatching { adapter.bondedDevices }.getOrNull()
            ?.firstOrNull { it.address.equals(address, ignoreCase = true) } ?: return false
        return isConnected(device)
    }

    /** After a boot (armed by the boot receiver): adb, if asked for. */
    fun consumeBoot() {
        val store = AutomationStore(this)
        if (!store.bootPending) return
        handler.postDelayed({
            // Cleared only now: if the service was rebound before this ran, the new instance
            // still finds the flag and runs the after-boot tasks.
            if (!store.takeBootPending()) return@postDelayed
            if (store.adbOnStartup) start(Flow.ADB, auto = true)
        }, BOOT_DELAY_MS)
    }

    /** Settings buttons: run now, under the cover, whatever the state. */
    fun runNow(which: Flow) = start(which, auto = false)

    /** One automatic Wi-Fi attempt, unless Wi-Fi is already on. Waits for a run in progress (adb). */
    private fun autoWifi(reason: String, returnTo: String? = null) {
        if (!AutomationStore(this).autoWifi) return
        if (wifiOn(this)) {
            Log.i(TAG, "Wi-Fi already on ($reason): nothing to do")
            return
        }
        // At most one automatic run a minute, whatever the trigger: a phone that keeps reconnecting
        // (or a switch that doesn't stick) never keeps covering the screen.
        val now = SystemClock.elapsedRealtime()
        if (lastAutoWifiRun?.let { now - it < WIFI_COOLDOWN_MS } == true) {
            Log.i(TAG, "Wi-Fi off ($reason): last automatic run under a minute ago, skipping")
            return
        }
        if (step != Step.IDLE) {
            Log.i(TAG, "Wi-Fi off ($reason): queued behind the run in progress")
            queued = Flow.WIFI
            queuedReturn = returnTo
            return
        }
        Log.i(TAG, "Wi-Fi off ($reason): opening it")
        lastAutoWifiRun = now
        start(Flow.WIFI, auto = true, returnTo = returnTo)
    }

    private fun start(which: Flow, auto: Boolean, returnTo: String? = null) {
        if (step != Step.IDLE) {
            if (auto) queued = which else toast(getString(R.string.auto_err_busy))
            return
        }
        // Automatic Wi-Fi runs never open EngMode while Wi-Fi is on (or turning on).
        if (auto && which == Flow.WIFI && wifiOn(this)) {
            Log.i(TAG, "Wi-Fi already on: not running")
            return
        }
        val launch = launchIntentFor(packageManager) ?: return toast(getString(R.string.auto_err_no_engmode))
        launchIntent = launch
        relaunches = 0
        lostTicks = 0
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

    private var launchIntent: Intent? = null
    private var relaunches = 0
    private var lostTicks = 0

    /**
     * Something else (Android Auto connecting, typically) took the screen from EngMode mid-run:
     * open EngMode again and start this run over, instead of waiting for the step to time out.
     */
    private fun reopenIfLost(root: AccessibilityNodeInfo?) {
        if (root != null || step == Step.CLOSE || step == Step.IDLE) {
            lostTicks = 0
            return
        }
        if (++lostTicks < LOST_TICKS || relaunches >= MAX_RELAUNCHES) return
        if (flow == Flow.WIFI && wifiOn(this)) {
            Log.i(TAG, "Wi-Fi came on meanwhile: not reopening EngMode")
            finishRun()
            return
        }
        val launch = launchIntent ?: return
        relaunches++
        lostTicks = 0
        Log.i(TAG, "EngMode lost the screen at $step: reopening it ($relaunches/$MAX_RELAUNCHES)")
        if (runCatching { startActivity(Intent(launch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }.isSuccess) {
            runDeadline = SystemClock.uptimeMillis() + TIMEOUT_MS
            advance(if (flow == Flow.WIFI) Step.CLICK_WIFI else Step.CLICK_ENCRYPT)
        }
    }

    private fun tick() {
        val now = SystemClock.uptimeMillis()
        if (step != Step.CLOSE && (now > runDeadline || now > stepDeadline)) onStepTimeout()
        val root = engModeRoot()
        reopenIfLost(root)
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
            handler.postDelayed(hideOverlayLater, RETURN_OVERLAY_MS)
        } else {
            hideOverlay()
        }
        queued?.let { next ->
            queued = null
            handler.postDelayed({ if (next == Flow.WIFI) autoWifi("queued", returnTo = queuedReturn.also { queuedReturn = null }) else start(next, auto = true) }, QUEUE_GAP_MS)
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

    private val hideOverlayLater = Runnable { hideOverlay() }

    /**
     * Full-screen cover in the app's style. It swallows touches, so a tap during a run never
     * lands on the hidden menu; the service's own clicks are accessibility actions, not touches.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showOverlay() {
        // A run started just after the last one: drop the old cover and its pending removal,
        // which would otherwise take this run's cover away mid-run.
        handler.removeCallbacks(hideOverlayLater)
        hideOverlay()
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
            setOnTouchListener { _, _ -> true }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable, so EngMode stays the active window (Back goes to it); touchable, so
            // stray taps stop here.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

        /** From a Wi-Fi trigger to its one attempt (the phone and the car settle first). */
        const val WIFI_DELAY_MS = 10_000L
        private const val PROJECTION_COOLDOWN_MS = 60_000L
        private const val WIFI_COOLDOWN_MS = 60_000L
        private const val RETURN_OVERLAY_MS = 800L

        /** Android Auto receiver and CarPlay app on this head unit. */
        val PROJECTION_PKGS = setOf("com.google.android.projection.sink", "com.desaysv.vehicle.carplayapp")
        private const val POLL_MS = 300L
        private const val TIMEOUT_MS = 45_000L
        private const val STEP_TIMEOUT_MS = 12_000L
        private const val BOOT_DELAY_MS = 8_000L
        private const val QUEUE_GAP_MS = 1_500L
        private const val MAX_BACKS = 6
        /** About 4 s without EngMode on screen during a step before it is reopened. */
        private const val LOST_TICKS = 13
        private const val MAX_RELAUNCHES = 2
        private const val SCROLL_EVERY_TICKS = 3
        private val ADB_ITEM = Regex("""ADB(\s*Switch)?""", RegexOption.IGNORE_CASE)

        @Volatile var instance: EngModeService? = null
            private set

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _active = MutableStateFlow<Flow?>(null)

        /** The run in progress, if any. */
        val active: StateFlow<Flow?> = _active.asStateFlow()

        /**
         * The Wi-Fi client (EngMode's "WIFI" switch) on, or turning on. Not the hotspot: wireless
         * Android Auto opens that on its own over its Bluetooth handshake, and counting it made
         * every automatic run stop at "already on" while the client stayed closed.
         */
        fun wifiOn(context: Context): Boolean {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return false
            return wifi.wifiState == WifiManager.WIFI_STATE_ENABLED || wifi.wifiState == WifiManager.WIFI_STATE_ENABLING
        }

        /** A paired Bluetooth device, for the settings picker. */
        data class PairedDevice(val address: String, val name: String, val connected: Boolean)

        /** The car's paired devices, connected ones first; empty when Bluetooth is off or unreadable. */
        // BLUETOOTH (install-time on Android 11, the car's version) covers this; lint asks for
        // Android 12's BLUETOOTH_CONNECT. A SecurityException still lands in runCatching.
        @SuppressLint("MissingPermission")
        fun pairedDevices(): List<PairedDevice> {
            val bonded = runCatching {
                BluetoothAdapter.getDefaultAdapter()?.bondedDevices.orEmpty()
                    .map { PairedDevice(it.address, it.name?.takeIf(String::isNotBlank) ?: it.address, isConnected(it)) }
            }.getOrDefault(emptyList())
            return bonded.sortedWith(compareByDescending<PairedDevice> { it.connected }.thenBy { it.name.lowercase() })
        }

        /** `BluetoothDevice.isConnected()`: hidden on Android 11, read by reflection; false where blocked. */
        fun isConnected(device: BluetoothDevice): Boolean =
            runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)

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
