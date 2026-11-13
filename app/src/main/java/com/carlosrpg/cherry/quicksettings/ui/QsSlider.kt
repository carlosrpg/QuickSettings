package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The styleguide's slider: a plain rounded track the full width of its parent, filled in [accent]
 * up to a round white thumb. Touch anywhere to jump, drag to adjust; [onChange] fires while
 * moving and [onFinished] once on release. Snaps to [step] within [range].
 */
@Composable
fun QsSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    description: String,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Qs.Route,
    step: Float = 0f,
    enabled: Boolean = true,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(Qs.TouchMin)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(value, range)
                if (enabled) setProgress { change(it.coerceIn(range)); finished(); true }
            }
            .then(if (!enabled) Modifier else Modifier.pointerInput(range, step) {
                fun at(x: Float) {
                    val raw = range.start + (x / size.width).coerceIn(0f, 1f) * span
                    change(if (step > 0f) (range.start + ((raw - range.start) / step).roundToInt() * step).coerceIn(range) else raw)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    at(down.position.x)
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        c.consume()
                        if (!c.pressed) break
                        at(c.position.x)
                    }
                    finished()
                }
            }),
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumb = 28.dp
        val track = if (enabled) accent else Qs.Muted
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x1FFFFFFF))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(track))
        }
        Box(
            Modifier.offset(x = (maxWidth - thumb) * fraction).size(thumb)
                .shadow(4.dp, CircleShape).clip(CircleShape)
                .background(if (enabled) Color.White else Qs.Muted)
        )
    }
}
