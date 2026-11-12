package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosrpg.cherry.quicksettings.R
import kotlin.math.cos
import kotlin.math.sin

/** Total range in km, or null when neither range is known. */
fun totalRange(evKm: Int?, fuelKm: Int?): Int? = listOfNotNull(evKm, fuelKm).takeIf { it.isNotEmpty() }?.sum()

private const val HALF_GAP_DEG = 7f

/**
 * Two half rings that mirror each other: the left half fills from the bottom up with
 * [evFraction] in [evColor], the right half with [fuelFraction] in fuel yellow. [limitFraction]
 * puts a tick on the EV half (the charge limit). [center] draws inside the ring.
 */
@Composable
fun MirrorRing(
    evFraction: Float?,
    fuelFraction: Float?,
    evColor: Color,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    limitFraction: Float? = null,
    center: @Composable () -> Unit,
) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arc = Size(this.size.width - w, this.size.height - w)
            val topLeft = Offset(inset, inset)
            val span = 180f - 2 * HALF_GAP_DEG
            val track = Color(0x1FFFFFFF)
            // Compose angles: 90° is the bottom. Left half sweeps clockwise up through 9 o'clock,
            // the right half counter-clockwise up through 3 o'clock.
            val leftStart = 90f + HALF_GAP_DEG
            val rightStart = 90f - HALF_GAP_DEG
            drawArc(track, leftStart, span, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            drawArc(track, rightStart, -span, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            evFraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let {
                drawArc(evColor, leftStart, span * it, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            fuelFraction?.coerceIn(0f, 1f)?.takeIf { it > 0f }?.let {
                drawArc(Qs.Fuel, rightStart, -span * it, false, topLeft, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            limitFraction?.coerceIn(0f, 1f)?.let {
                val angle = Math.toRadians((leftStart + span * it).toDouble())
                val r = arc.width / 2
                val c = Offset(this.size.width / 2, this.size.height / 2)
                fun at(radius: Float) = c + Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
                drawLine(Qs.Text, at(r - w * 0.9f), at(r + w * 0.9f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        center()
    }
}

@Composable
private fun TotalInside(total: Int?, number: TextUnit, caption: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(total?.toString() ?: "--", color = Qs.Text, fontSize = number, lineHeight = number, fontWeight = FontWeight.Bold, maxLines = 1)
        Text("km", color = Qs.Muted, fontSize = caption, lineHeight = caption, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun Corner(text: String, color: Color, size: TextUnit, modifier: Modifier) =
    Text(text, modifier, color = color, fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold, maxLines = 1)

/** The semi-open tile version: ring in the middle, the four values in the corners, total inside. */
@Composable
fun MirrorRangeTile(soc: Int?, evKm: Int?, fuelPercent: Int?, fuelKm: Int?, evColor: Color) {
    Box(Modifier.fillMaxSize().padding(horizontal = 7.dp, vertical = 6.dp)) {
        MirrorRing(soc?.div(100f), fuelPercent?.div(100f), evColor, 58.dp, 5.dp, Modifier.align(Alignment.Center)) {
            TotalInside(totalRange(evKm, fuelKm), 14.sp, 8.sp)
        }
        Corner(soc?.let { "$it%" } ?: "--", evColor, 10.sp, Modifier.align(Alignment.TopStart))
        Corner(evKm?.toString() ?: "--", Qs.TextSoft, 10.sp, Modifier.align(Alignment.BottomStart))
        Corner(fuelPercent?.let { "$it%" } ?: "--", Qs.Fuel, 10.sp, Modifier.align(Alignment.TopEnd))
        Corner(fuelKm?.toString() ?: "--", Qs.TextSoft, 10.sp, Modifier.align(Alignment.BottomEnd))
    }
}

/**
 * The open-card version: the same ring, larger, with each side's % above and km below, named
 * and iconned, mirrored around it; the charge limit is a tick on the EV half.
 */
@Composable
fun MirrorRangeLarge(
    soc: Int?,
    evKm: Int?,
    fuelPercent: Int?,
    fuelKm: Int?,
    chargeLimit: Int?,
    evColor: Color,
    ring: Dp,
    modifier: Modifier = Modifier,
) {
    val scale = ring.value / 160f
    val percentSize = (26 * scale).coerceIn(16f, 34f).sp
    val kmSize = (20 * scale).coerceIn(14f, 26f).sp
    val nameSize = (13 * scale).coerceIn(11f, 15f).sp
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ring * 0.12f)) {
        Side(
            stringResource(R.string.range_electric), Icons.Battery, evColor, soc, evKm,
            chargeLimit?.let { stringResource(R.string.range_limit, it) }, ring, percentSize, kmSize, nameSize, Alignment.End,
        )
        MirrorRing(soc?.div(100f), fuelPercent?.div(100f), evColor, ring, ring * 0.075f, limitFraction = chargeLimit?.div(100f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TotalInside(totalRange(evKm, fuelKm), (34 * scale).coerceIn(20f, 44f).sp, nameSize)
                Text(stringResource(R.string.range_total_caption), color = Qs.Muted, fontSize = nameSize, maxLines = 1)
            }
        }
        Side(
            stringResource(R.string.range_fuel), Icons.Fuel, Qs.Fuel, fuelPercent, fuelKm,
            null, ring, percentSize, kmSize, nameSize, Alignment.Start,
        )
    }
}

@Composable
private fun Side(
    name: String,
    icon: String,
    color: Color,
    percent: Int?,
    km: Int?,
    note: String?,
    ring: Dp,
    percentSize: TextUnit,
    kmSize: TextUnit,
    nameSize: TextUnit,
    align: Alignment.Horizontal,
) {
    val textAlign = if (align == Alignment.End) TextAlign.End else TextAlign.Start
    Column(Modifier.height(ring).width(ring * 0.78f), horizontalAlignment = align, verticalArrangement = Arrangement.SpaceBetween) {
        // Top: name and %, level with the top of the ring.
        Column(horizontalAlignment = align) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (align == Alignment.Start) QsIcon(icon, color, 16.dp)
                Text(name, color = color, fontSize = nameSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (align == Alignment.End) QsIcon(icon, color, 16.dp)
            }
            Text(percent?.let { "$it%" } ?: "--", color = color, fontSize = percentSize, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = textAlign)
        }
        // Bottom: range, level with the bottom of the ring.
        Column(horizontalAlignment = align) {
            Text(km?.let { "$it km" } ?: "-- km", color = Qs.Text, fontSize = kmSize, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = textAlign)
            note?.let { Text(it, color = Qs.Muted, fontSize = nameSize, maxLines = 1, textAlign = textAlign) }
        }
    }
}
