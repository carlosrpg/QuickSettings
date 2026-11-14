package com.carlosrpg.cherry.quicksettings.media

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** The last [capacity] events, each stamped with the wall-clock time, for the debug screen. */
class EventRing(private val capacity: Int = 20) {
    private val events = ArrayDeque<String>()
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun add(event: String) {
        if (events.size == capacity) events.removeFirst()
        events.addLast("${clock.format(Date())} $event")
    }

    @Synchronized
    fun snapshot(): List<String> = events.toList()
}
