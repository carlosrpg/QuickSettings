package com.carlosrpg.cherry.quicksettings.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Replays a tap that landed in the swipe zone onto the app underneath, so the zone doesn't
 * swallow taps. Optional: without it, a tap inside the narrow zone is lost.
 *
 * Also answers whether the car's status bar is in the window list, which it leaves while a
 * full-screen app (Android Auto, video) hides it; [FullScreenDetector] polls that.
 */
class TapRelayService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    /**
     * Whether the status bar is in the window list: a system window flush with the top, as wide
     * as the screen and much shorter than it. The top bar itself is untouchable, so it never
     * shows up here. Null when the list is empty (no window access granted yet).
     */
    fun statusBarListed(): Boolean? {
        val list = try { windows } catch (e: RuntimeException) { return null }
        if (list.isEmpty()) return null
        val screen = resources.displayMetrics
        val bounds = Rect()
        return list.any { window ->
            window.getBoundsInScreen(bounds)
            window.type == AccessibilityWindowInfo.TYPE_SYSTEM && bounds.top == 0 && bounds.left == 0 &&
                bounds.width() >= screen.widthPixels && bounds.height() in 1 until screen.heightPixels / 4
        }
    }
    override fun onInterrupt() = Unit

    fun tap(x: Float, y: Float, done: () -> Unit) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_MS))
            .build()
        val callback = object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = done()
            override fun onCancelled(gestureDescription: GestureDescription?) = done()
        }
        if (!dispatchGesture(gesture, callback, null)) done()
    }

    companion object {
        private const val TAP_MS = 40L

        @Volatile var instance: TapRelayService? = null
            private set
    }
}
