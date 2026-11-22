package com.carlosrpg.cherry.quicksettings.overlay

import android.content.ContentResolver
import android.provider.Settings

/**
 * The head unit's system gestures: the window manager detects 2 to 5 finger taps, long presses,
 * swipes and pinches and broadcasts each one to every app as [ACTION] with an int `gesture`
 * extra (fingers × 100 + kind). Detection quirks worth knowing when a swipe "does nothing":
 * the pinch check runs first and fires when the fingers' spread changes by about 20 px, before
 * a swipe has travelled its 50 px, and fingers resting still for the long-press time make a long
 * press; either one ends detection for that touch.
 */
object CarGesture {
    const val ACTION = "android.intent.action.car_system_gesture_mode"
    const val TWO_FINGER_SWIPE_DOWN = 202

    private val KINDS = mapOf(
        0 to "swipe left", 1 to "swipe right", 2 to "swipe down", 3 to "swipe up",
        4 to "pinch", 5 to "long press", 6 to "tap",
    )

    /** "two-finger swipe down (202)". */
    fun name(code: Int): String {
        val fingers = when (code / 100) { 2 -> "two"; 3 -> "three"; 4 -> "four"; 5 -> "five"; else -> null }
        val kind = KINDS[code % 100]
        return if (fingers == null || kind == null) "unknown ($code)" else "$fingers-finger $kind ($code)"
    }

    /** The car's detector settings, for the log: whether it runs and how long a long press is. */
    fun describeSettings(resolver: ContentResolver): String {
        fun read(key: String) = runCatching { Settings.System.getString(resolver, key) }.getOrNull() ?: "unset"
        return "detector enabled=${read("car_system_gestures_detector_enabled")}, long press=${read("car_system_gestures_longpress_duration")} ms"
    }
}
