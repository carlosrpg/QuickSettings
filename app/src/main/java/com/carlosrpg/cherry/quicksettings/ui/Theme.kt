package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Night Glass tokens from the approved styleguide. */
object Qs {
    // Android 11 has no window blur, so the "glass" is denser than the styleguide's 56%
    // to keep tiles legible over busy apps.
    val Glass = Color(0xD90E121A)       // #0E121A at 85%
    val GlassOpen = Color(0xE60E121A)   // 90% when fully open
    val Tile = Color(0x0FFFFFFF)        // white 6%
    val Hairline = Color(0x1AFFFFFF)    // white 10%
    val Button = Color(0x12FFFFFF)
    val Text = Color(0xFFEEF2F7)
    val TextSoft = Color(0xFFC6D0DC)
    val Muted = Color(0xFF9AA6B6)
    val Dim = Color(0xFF6B7686)

    val Cool = Color(0xFF2F6BFF)
    val Mid = Color(0xFF8A7BBF)
    val Warm = Color(0xFFFF7A2E)
    val Energy = Color(0xFF3DDC97)
    val EnergyLow = Color(0xFFFFB547)
    val EnergyCritical = Color(0xFFFF5A5F)
    val Fuel = Color(0xFFF2B84B)
    val Route = Color(0xFF4DA3FF)
    val Mode = Color(0xFF2FD3B5)

    val DrawerRadius = 32.dp
    val TileRadius = 22.dp
    val ButtonRadius = 16.dp
    val TileSize = 96.dp
    val TouchMin = 56.dp

    val Label = 12.sp
    val Body = 15.sp

    fun chargeColor(soc: Int?): Color = when {
        soc == null -> Muted
        soc < 15 -> EnergyCritical
        soc < 30 -> EnergyLow
        else -> Energy
    }

    /** Temperature on the 16..30 °C diverging scale. */
    fun tempColor(celsius: Int?): Color {
        val t = (((celsius ?: 23) - 16) / 14f).coerceIn(0f, 1f)
        return if (t < 0.5f) lerp(Cool, Mid, t * 2) else lerp(Mid, Warm, (t - 0.5f) * 2)
    }

    val TempGradient = Brush.verticalGradient(listOf(Warm, Mid, Cool))
}

/** Icons drawn from 24×24 SVG path data, in the styleguide's 2 px round stroke. */
object Icons {
    const val Play = "M8 5v14l11-7z"
    const val Pause = "M7 5h4v14H7zM13 5h4v14h-4z"
    const val Prev = "M6 5h2v14H6zM19 5v14L9 12z"
    const val Next = "M16 5h2v14h-2zM5 5v14l10-7z"
    const val Volume = "M4 9h4l5-4v14l-5-4H4zM16 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11"
    const val Mute = "M4 9h4l5-4v14l-5-4H4zM16 9l5 6M21 9l-5 6"
    const val Mic = "M12 3a3 3 0 0 0-3 3v6a3 3 0 0 0 6 0V6a3 3 0 0 0-3-3zM5 11a7 7 0 0 0 14 0M12 18v3"
    const val Plus = "M12 5v14M5 12h14"
    const val Minus = "M5 12h14"
    const val ChevronLeft = "M15 6l-6 6 6 6"
    const val ChevronRight = "M9 6l6 6-6 6"
    const val DoubleRight = "M6 6l6 6-6 6M12 6l6 6-6 6"
    const val DoubleLeft = "M12 6l-6 6 6 6M18 6l-6 6 6 6"
    const val Close = "M6 6l12 12M18 6L6 18"
    const val Tune = "M4 7h9M17 7h3M4 17h3M11 17h9M17 7a2 2 0 1 1-4 0a2 2 0 1 1 4 0M11 17a2 2 0 1 1-4 0a2 2 0 1 1 4 0"
    const val OpenApp = "M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"
    const val TurnRight = "M7 20v-6a4 4 0 0 1 4-4h7M14 6l4 4-4 4"
    const val NavArrow = "M12 2l7 19-7-4-7 4z"
    const val Pin = "M12 21s-7-6.5-7-11a7 7 0 0 1 14 0c0 4.5-7 11-7 11zM14.5 10a2.5 2.5 0 1 1-5 0a2.5 2.5 0 1 1 5 0"
    const val Battery = "M4 7h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2zM21 10v4"
    const val BatteryBolt = "$Battery M11 9l-2 3h3l-2 3"
    const val BatteryLock = "$Battery M7 12h6"
    const val Fuel = "M4 21V5a2 2 0 0 1 2-2h6a2 2 0 0 1 2 2v16M3 21h12M14 9h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3M7 8h4"
    const val Wind = "M3 8h10a3 3 0 1 0-3-3M3 12h15a3 3 0 1 1-3 3M3 16h7"
    const val Thermometer = "M14 14.76V3.5a2 2 0 0 0-4 0v11.26a4 4 0 1 0 4 0zM12 10v5.5"
    /** Air-conditioner unit blowing air: the climate mark behind the quadrant (after SVG Repo's CC0 "airconditioning-o"). */
    const val AirConditioner = "M5 3h14a3 3 0 0 1 3 3v3a3 3 0 0 1-3 3H5a3 3 0 0 1-3-3V6a3 3 0 0 1 3-3zM5.5 8.5h13M12 14v7M8 14c0 3-1.3 5.3-3.6 7M16 14c0 3 1.3 5.3 3.6 7"
    const val Brake = "M17 12a5 5 0 1 1-10 0a5 5 0 1 1 10 0M5 7a8 8 0 0 0 0 10M19 7a8 8 0 0 1 0 10M12 10v2M12 14h.01"
    const val AutoHold = "M19 12a7 7 0 1 1-14 0a7 7 0 1 1 14 0M9.5 15l2.5-6 2.5 6M10.3 13h3.4"
    const val Lane = "M7 20L10 4M17 20L14 4M12 13v3"
    const val Drag = "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01"
    const val ChevronUp = "M6 15l6-6 6 6"
    const val ChevronDown = "M6 9l6 6 6-6"
}

@Composable
fun QsIcon(
    path: String,
    tint: Color,
    size: Dp = 24.dp,
    filled: Boolean = false,
    strokeWidth: Float = 2f,
    modifier: Modifier = Modifier,
    viewport: Float = 24f,
) {
    val parsed = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / viewport
        scale(k, k, pivot = Offset.Zero) {
            drawPath(
                parsed, tint,
                style = if (filled) Fill else Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
