package com.carlosrpg.cherry.quicksettings.ui

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.RegenLevel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sign

enum class Axis { HORIZONTAL, VERTICAL }

/**
 * Turns a drag into steps along one axis. The axis locks once the finger has moved [lockPx];
 * after that only that axis counts, so a slightly diagonal drag never does both things.
 */
class DragSteps(private val lockPx: Float, private val stepPx: Float) {
    var axis: Axis? = null
        private set

    /** Steps along the locked axis for a total drag offset; 0 until the axis locks. */
    fun steps(total: Offset): Int {
        if (axis == null && total.getDistance() >= lockPx) {
            axis = if (abs(total.x) > abs(total.y)) Axis.HORIZONTAL else Axis.VERTICAL
        }
        val distance = when (axis) {
            Axis.HORIZONTAL -> total.x
            Axis.VERTICAL -> total.y
            null -> 0f
        }
        return (distance / stepPx).toInt()
    }
}

/** What the guide beside the drawer shows during a press-and-drag. */
sealed interface Hud {
    /**
     * The media control card. Both directions work in one gesture: [volume] follows vertical
     * travel and is applied live; a track skip only arms ([trackProgress] reaches 1) and is
     * applied on release. Directions are -1, 0 or 1.
     */
    data class Media(
        val volume: Int,
        val max: Int,
        val adjustable: Boolean,
        val volumeDirection: Int,
        val trackDirection: Int,
        val trackProgress: Float,
        /** Debug builds on fixed-volume systems: the number is the expected volume, not applied. */
        val simulated: Boolean = false,
    ) : Hud {
        val trackArmed: Boolean get() = trackDirection != 0 && trackProgress >= 1f
    }
    data class Mode(val current: BatteryMode?, val target: BatteryMode, val holdTarget: Int?) : Hud
    data class Regen(val current: RegenLevel?, val target: RegenLevel) : Hud
    /** Both axes at once: the marker follows the finger across the quadrant. */
    data class Climate(val temp: Int, val fan: Int) : Hud
}

/** Track skips only count within this angle of horizontal; every steeper drag adjusts volume. */
const val TRACK_MAX_ANGLE_DEG = 20.0

/**
 * The media card's state for a drag of [total] from the press point. The drag's angle picks
 * the control: within [TRACK_MAX_ANGLE_DEG] of horizontal it fills toward a track skip that arms
 * at [trackThresholdPx]; anything steeper moves the volume one step per [volumeStepPx] of
 * vertical travel (never when the volume isn't adjustable). In the track zone the volume stays
 * where [previous] left it, so a sideways drag never changes or undoes it.
 */
fun mediaGuide(
    total: Offset,
    startVolume: Int,
    max: Int,
    adjustable: Boolean,
    volumeStepPx: Float,
    trackThresholdPx: Float,
    simulated: Boolean = false,
    previous: Hud.Media? = null,
): Hud.Media {
    val angle = Math.toDegrees(atan2(abs(total.y).toDouble(), abs(total.x).toDouble()))
    val trackZone = total.getDistance() > 0f && angle <= TRACK_MAX_ANGLE_DEG
    val steps = if (adjustable && !trackZone) (-total.y / volumeStepPx).toInt() else 0
    val volume = if (trackZone) previous?.volume ?: startVolume else (startVolume + steps).coerceIn(0, max)
    return Hud.Media(
        volume = volume,
        max = max,
        adjustable = adjustable,
        volumeDirection = if (trackZone) 0 else steps.sign,
        trackDirection = if (trackZone) total.x.sign.toInt() else 0,
        trackProgress = if (trackZone) (abs(total.x) / trackThresholdPx).coerceIn(0f, 1f) else 0f,
        simulated = simulated,
    )
}

/** A tile that only reacts to a tap; drags pass through so the tile list can still scroll. */
fun Modifier.tap(onTap: () -> Unit): Modifier = composed {
    val tap by rememberUpdatedState(onTap)
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) tap()
        }
    }
}

/**
 * A quick tap calls [onTap]. Moving past touch slop, or holding still for [holdMs], starts a
 * drag: [onDragStart] once, then [onDrag] with the total offset from the press point,
 * [onRelease] with the final one when the finger lifts, or [onCancel] if the gesture is lost.
 * Once a drag starts its events are consumed, so a scrolling parent can't take them.
 */
fun Modifier.tapOrDrag(
    holdMs: Long = 350,
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onRelease: (Offset) -> Unit,
    onCancel: () -> Unit,
): Modifier = composed {
    // The pointer loop outlives recompositions; always call the latest callbacks.
    val tap by rememberUpdatedState(onTap)
    val start by rememberUpdatedState(onDragStart)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    pointerInput(holdMs) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pressedAt = SystemClock.uptimeMillis()
            val slop = viewConfiguration.touchSlop
            // Phase 1: tap, drag past slop, or hold.
            while (true) {
                val left = holdMs - (SystemClock.uptimeMillis() - pressedAt)
                if (left <= 0) break
                val event = withTimeoutOrNull(left) { awaitPointerEvent() } ?: break
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                if (change.isConsumed) return@awaitEachGesture
                val moved = (change.position - down.position).getDistance()
                if (!change.pressed) {
                    if (moved < slop) tap()
                    return@awaitEachGesture
                }
                if (moved > slop) {
                    change.consume()
                    break
                }
            }
            // Phase 2: the drag.
            start()
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    cancel()
                    break
                }
                val total = change.position - down.position
                change.consume()
                if (!change.pressed) {
                    release(total)
                    break
                }
                drag(total)
            }
        }
    }
}
