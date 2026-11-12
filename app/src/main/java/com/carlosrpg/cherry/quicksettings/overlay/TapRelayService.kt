package com.carlosrpg.cherry.quicksettings.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

/**
 * Replays a tap that landed in the swipe zone onto the app underneath, so the zone doesn't
 * swallow taps. Optional: without it, a tap inside the narrow zone is lost.
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
