package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.FanSpec

/** Sizes for one rendering of the quadrant; the plot is inset from the left and bottom for the axes. */
data class QuadrantStyle(
    val size: Dp,
    val axisInset: Dp,
    val topRightInset: Dp,
    val marker: Dp,
    val markerRing: Dp,
    val number: TextUnit,
    val axisIcon: Dp,
    val mark: Dp,
) {
    companion object {
        val Tile = QuadrantStyle(Qs.TileSize, 20.dp, 10.dp, 16.dp, 4.dp, 11.sp, 11.dp, 44.dp)
        val Card = QuadrantStyle(140.dp, 26.dp, 12.dp, 20.dp, 5.dp, 14.sp, 14.dp, 60.dp)
        val Guide = QuadrantStyle(186.dp, 32.dp, 14.dp, 26.dp, 6.dp, 17.sp, 17.dp, 80.dp)

        /** Proportions of [Guide], for a quadrant that fills whatever space it gets. */
        fun scaled(size: Dp) = QuadrantStyle(
            size = size,
            axisInset = size * 0.17f,
            topRightInset = size * 0.075f,
            marker = size * 0.14f,
            markerRing = size * 0.032f,
            number = (size.value * 0.09f).sp,
            axisIcon = size * 0.09f,
            mark = size * 0.42f,
        )
    }
}

private fun tempFraction(c: Int) = ((c - CabinTempSpec.MIN) / (CabinTempSpec.MAX - CabinTempSpec.MIN).toFloat()).coerceIn(0f, 1f)
private fun fanFraction(f: Int) = (f / FanSpec.MAX.toFloat()).coerceIn(0f, 1f)

/**
 * Temperature (up = warmer) × fan (right = more). Colour follows temperature, intensity follows
 * fan. The setting is a white marker with a ring and a glow in the temperature's colour, drawn
 * over everything; the values sit in the middle of their axes.
 */
@Composable
fun ClimateQuadrant(temp: Int?, fan: Int?, style: QuadrantStyle, modifier: Modifier = Modifier, acOn: Boolean? = null) {
    val inset = style.axisInset
    val far = style.topRightInset
    val plot = style.size - inset - far
    // Night glass with the temperature scale as a soft wash: warm at the top, cool at the bottom.
    Box(modifier.size(style.size).background(QuadrantGlass).background(QuadrantWash)) {
        // "A/C" across the plot says what this controls; it lights up while the compressor runs.
        CenteredAt(x = inset + plot / 2, y = far + plot / 2) {
            Text(
                "A/C", color = if (acOn == true) Qs.Energy.copy(alpha = 0.45f) else Color(0x33FFFFFF),
                fontSize = (style.mark.value * 0.5f).sp, fontWeight = FontWeight.Bold, maxLines = 1,
            )
        }
        // Axes.
        Box(Modifier.offset(x = inset, y = far).width(1.dp).height(plot).background(Color(0x73FFFFFF)))
        Box(Modifier.offset(x = inset, y = far + plot).width(plot).height(1.dp).background(Color(0x73FFFFFF)))
        // Axis marks: thermometer at the top of the temperature axis, wind at the end of the fan axis.
        QsIcon(Icons.Thermometer, Qs.Text, style.axisIcon, modifier = Modifier.offset(x = (inset - style.axisIcon) / 2, y = 3.dp))
        QsIcon(Icons.Wind, Qs.Text, style.axisIcon, modifier = Modifier.offset(x = style.size - style.axisIcon - 3.dp, y = far + plot + (inset - style.axisIcon) / 2))
        // Values in the middle of their axes.
        CenteredAt(x = inset / 2, y = far + plot / 2) { AxisNumber(temp?.let { "$it°" } ?: "--", style.number) }
        CenteredAt(x = inset + plot / 2, y = far + plot + inset / 2) { AxisNumber(fan?.toString() ?: "--", style.number) }
        if (temp != null && fan != null) {
            val x = inset + plot * fanFraction(fan)
            val y = far + plot * (1f - tempFraction(temp))
            // Guides from the marker back to each axis.
            Box(Modifier.offset(x = inset, y = y).width(x - inset).height(1.dp).background(Color(0x59FFFFFF)))
            Box(Modifier.offset(x = x, y = y).width(1.dp).height(far + plot - y).background(Color(0x59FFFFFF)))
            // Marker last, on top of everything.
            val glow = Qs.tempColor(temp)
            val halo = style.marker * 2.2f
            Box(
                Modifier.offset(x = x - halo / 2, y = y - halo / 2).size(halo).drawBehind {
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.85f), Color.Transparent)), radius = size.minDimension / 2)
                }
            )
            Box(
                Modifier.offset(x = x - style.marker / 2, y = y - style.marker / 2).size(style.marker)
                    .clip(CircleShape).background(Color.White).border(style.markerRing, Color(0x40FFFFFF), CircleShape)
            )
        }
    }
}

private val QuadrantGlass = Color(0xFF161C26)
private val QuadrantWash = Brush.verticalGradient(
    listOf(Qs.Warm.copy(alpha = 0.34f), Color(0x339A7BD1), Qs.Route.copy(alpha = 0.34f))
)

@Composable
private fun AxisNumber(text: String, size: TextUnit) =
    Text(text, color = Qs.Text, fontSize = size, fontWeight = FontWeight.Bold, maxLines = 1)

/** Places [content] so its centre lands on (x, y) of the parent. */
@Composable
private fun CenteredAt(x: Dp, y: Dp, content: @Composable () -> Unit) {
    Layout(content, Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) { measurables, _ ->
        val p = measurables.first().measure(Constraints())
        layout(0, 0) { p.place(x.roundToPx() - p.width / 2, y.roundToPx() - p.height / 2) }
    }
}
