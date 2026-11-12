package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.carlosrpg.cherry.quicksettings.car.CarItems
import kotlin.math.abs

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

/** What the guide beside the drawer shows during a hold-and-drag. */
sealed interface Hud {
    data class Volume(val value: Int, val max: Int) : Hud
    /** [direction] -1 = previous, 1 = next; [armed] once past the threshold. */
    data class Track(val direction: Int, val armed: Boolean) : Hud
    data class Mode(val current: CarItems.BatteryMode?, val target: CarItems.BatteryMode, val holdTarget: Int?) : Hud
    data class Regen(val current: CarItems.Regen?, val target: CarItems.Regen) : Hud
}

/**
 * A quick tap calls [onTap]. Holding for [holdMs] starts a drag: [onDrag] gets the total offset
 * from the press point, [onRelease] the final one when the finger lifts, [onCancel] if the
 * gesture is taken away. Nothing happens on hold alone.
 */
fun Modifier.tapOrHoldDrag(
    holdMs: Long = 350,
    onTap: () -> Unit,
    onHoldStart: () -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onRelease: (Offset) -> Unit = {},
    onCancel: () -> Unit = {},
): Modifier = composed {
    // The pointer loop outlives recompositions; always call the latest callbacks.
    val tap by rememberUpdatedState(onTap)
    val holdStart by rememberUpdatedState(onHoldStart)
    val drag by rememberUpdatedState(onDrag)
    val release by rememberUpdatedState(onRelease)
    val cancel by rememberUpdatedState(onCancel)
    pointerInput(holdMs) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var ended = false
            val up = withTimeoutOrNull(holdMs) { waitForUpOrCancellation().also { ended = true } }
            if (ended) {
                if (up != null && (up.position - down.position).getDistance() < viewConfiguration.touchSlop) tap()
                return@awaitEachGesture
            }
            holdStart()
            var total = Offset.Zero
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    cancel()
                    break
                }
                total = change.position - down.position
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
