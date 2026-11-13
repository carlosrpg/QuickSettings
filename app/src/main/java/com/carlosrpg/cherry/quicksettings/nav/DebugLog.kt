package com.carlosrpg.cherry.quicksettings.nav

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the notification listener has been handed lately, for the settings debug screen. One
 * line per notification (an app updating the same one, as a nav app does every second, only
 * refreshes its line), newest last.
 */
object DebugLog {
    private const val MAX_LINES = 14
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val lines = LinkedHashMap<String, String>()

    @Synchronized
    fun add(key: String, line: String) {
        lines.remove(key)
        lines[key] = "${clock.format(Date())} $line"
        while (lines.size > MAX_LINES) lines.remove(lines.keys.first())
    }

    @Synchronized
    fun snapshot(): List<String> = lines.values.toList()
}
