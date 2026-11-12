package com.carlosrpg.cherry.quicksettings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.RegenLevel
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.nav.NavState

val ArtPlaceholder = Brush.radialGradient(listOf(Color(0xFFFF9A6B), Color(0xFFE2557A), Color(0xFF5B3FD1)))

@Composable
fun TileFrame(
    description: String,
    modifier: Modifier = Modifier,
    background: Brush? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Qs.TileRadius)
    Box(
        modifier
            .size(Qs.TileSize)
            .clip(shape)
            .then(if (background != null) Modifier.background(background) else Modifier.background(Qs.Tile))
            .border(1.dp, Color(0x14FFFFFF), shape)
            .semantics { contentDescription = description },
        content = content,
    )
}

/** Thin bar along the bottom edge (media progress, nav approach). */
@Composable
fun BoxScope.EdgeProgress(fraction: Float, color: Color, track: Color = Color(0x40FFFFFF)) {
    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(track)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(color))
    }
}

@Composable
fun MediaTile(media: MediaState, modifier: Modifier = Modifier) {
    TileFrame(stringResource(R.string.desc_media), modifier, background = if (media.art == null) ArtPlaceholder else null) {
        media.art?.let {
            Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color(0x8C000000))))
        Box(
            Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 12.dp).size(30.dp)
                .clip(CircleShape).background(Color(0xF0FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            QsIcon(if (media.playing) Icons.Pause else Icons.Play, Color(0xFF0B1018), 14.dp, filled = true)
        }
        if (media.playing) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 16.dp).height(16.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                listOf(10.dp, 16.dp, 7.dp).forEach { h ->
                    Box(Modifier.width(3.dp).height(h).clip(RoundedCornerShape(2.dp)).background(Color.White))
                }
            }
        }
        if (media.durationMs > 0) EdgeProgress(media.positionMs / media.durationMs.toFloat(), Color.White)
    }
}

@Composable
fun ClimateTile(car: CarState, modifier: Modifier = Modifier) {
    val temp = car.cabinTempC
    val fan = car.fanLevel
    val description = stringResource(R.string.desc_climate, temp?.toString() ?: "--", fan?.toString() ?: "--")
    TileFrame(description, modifier, background = Qs.TempGradient) {
        // Low fan dims the colour; full fan shows it at full strength.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xC70A0E14), Color(0x0D0A0E14)))))
        // Climate mark behind the plot, so the square reads as "climate" before any number.
        QsIcon(Icons.ClimateFan, Color(0x38FFFFFF), 54.dp, strokeWidth = 1.6f, modifier = Modifier.align(Alignment.Center).offset(x = 5.dp, y = (-3).dp))
        val plotLeft = 20.dp
        val plotBottom = 16.dp
        // Axis marks: temperature up the left, wind along the bottom.
        QsIcon(Icons.Thermometer, Qs.Text, 12.dp, modifier = Modifier.offset(x = 4.dp, y = 3.dp))
        QsIcon(Icons.Wind, Qs.Text, 12.dp, modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-2).dp))
        Box(Modifier.offset(x = plotLeft).padding(top = 10.dp, bottom = plotBottom).width(1.dp).fillMaxHeight().background(Color(0x66FFFFFF)))
        Box(Modifier.align(Alignment.BottomStart).padding(start = plotLeft, end = 10.dp, bottom = plotBottom).fillMaxWidth().height(1.dp).background(Color(0x66FFFFFF)))
        if (temp != null && fan != null) {
            val plotW = Qs.TileSize - plotLeft - 10.dp
            val plotH = Qs.TileSize - 10.dp - plotBottom
            val x = plotLeft + plotW * (fan / 10f).coerceIn(0f, 1f)
            val y = 10.dp + plotH * (1f - ((temp - 16) / 14f).coerceIn(0f, 1f))
            Box(
                Modifier.offset(x = x - 8.dp, y = y - 8.dp).size(16.dp).clip(CircleShape)
                    .background(Color.White).border(4.dp, Color(0x40FFFFFF), CircleShape)
            )
            // Numbers ride their axes but stay clear of the axis icons.
            SmallNumber("$temp", Modifier.offset(x = 3.dp, y = maxOf(y - 7.dp, 17.dp)))
            SmallNumber("$fan", Modifier.align(Alignment.BottomStart).offset(x = minOf(x - 3.dp, 70.dp), y = (-1).dp))
        }
    }
}

@Composable
private fun SmallNumber(text: String, modifier: Modifier) =
    Text(text, modifier, color = Qs.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)

fun BatteryMode?.icon(): String = when (this) {
    BatteryMode.SMART -> Icons.BatteryBolt
    BatteryMode.FORCED -> Icons.BatteryLock
    else -> Icons.Battery
}

@StringRes
fun BatteryMode.labelRes(): Int = when (this) {
    BatteryMode.INITIAL -> R.string.mode_initial
    BatteryMode.SMART -> R.string.mode_smart
    BatteryMode.FORCED -> R.string.mode_forced
}

@StringRes
fun RegenLevel.labelRes(): Int = when (this) {
    RegenLevel.LOW -> R.string.regen_low
    RegenLevel.MEDIUM -> R.string.regen_medium
    RegenLevel.HIGH -> R.string.regen_high
}

@Composable
fun BatteryMode.label(): String = stringResource(labelRes())

@Composable
fun RegenLevel.label(): String = stringResource(labelRes())

@Composable
fun onOffLabel(on: Boolean?): String = stringResource(
    when (on) { true -> R.string.value_on; false -> R.string.value_off; null -> R.string.value_unknown }
)

@Composable
fun RegenBars(regen: RegenLevel?, barWidth: Dp = 10.dp, maxHeight: Dp = 18.dp) {
    val filled = when (regen) { RegenLevel.LOW -> 1; RegenLevel.MEDIUM -> 2; RegenLevel.HIGH -> 3; null -> 0 }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 1..3) {
            Box(
                Modifier.width(barWidth).height(maxHeight * (0.44f + 0.28f * (i - 1)))
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= filled) Qs.Energy else Color(0x2EFFFFFF))
            )
        }
    }
}

@Composable
fun ModeTile(car: CarState, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.desc_mode,
        car.batteryMode?.label() ?: stringResource(R.string.value_unknown),
        car.regen?.label() ?: stringResource(R.string.value_unknown),
    )
    TileFrame(description, modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Qs.Mode.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                QsIcon(car.batteryMode.icon(), Qs.Mode, 26.dp)
            }
            RegenBars(car.regen)
        }
    }
}

/** Arc ring, starting at 12 o'clock. */
@Composable
fun Ring(fraction: Float?, color: Color, size: Dp, stroke: Dp, modifier: Modifier = Modifier, track: Color = Color(0x1FFFFFFF)) {
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        val inset = w / 2
        val arcSize = Size(this.size.width - w, this.size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(w))
        if (fraction != null && fraction > 0f) {
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
    }
}

@Composable
fun AutonomyTile(car: CarState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.desc_autonomy, car.batterySoc?.toString() ?: "--", car.electricRangeKm?.toString() ?: "--")
    TileFrame(description, modifier) {
        Column(Modifier.fillMaxSize().padding(top = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Ring(car.batterySoc?.div(100f), Qs.chargeColor(car.batterySoc), 64.dp, 6.dp)
                Ring(car.fuelPercent?.div(100f), Qs.Fuel, 46.dp, 4.dp)
                Text(car.batterySoc?.toString() ?: "--", color = Qs.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Text(car.electricRangeKm?.let { "$it km" } ?: "-- km", color = Qs.TextSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun NavTile(nav: NavState?, modifier: Modifier = Modifier) {
    val description = if (nav == null) stringResource(R.string.desc_nav_idle) else stringResource(R.string.desc_nav, nav.title.orEmpty())
    TileFrame(description, modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            if (nav == null) {
                QsIcon(Icons.NavArrow, Qs.Route, 40.dp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    QsIcon(Icons.Mic, Qs.Muted, 12.dp)
                    Text(stringResource(R.string.where_to), color = Qs.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            } else {
                if (nav.maneuver != null) {
                    Image(nav.maneuver.asImageBitmap(), null, Modifier.size(42.dp))
                } else {
                    QsIcon(Icons.TurnRight, Qs.Text, 42.dp, strokeWidth = 2.4f)
                }
                Text(nav.title.orEmpty(), color = Qs.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
fun AssistRing(on: Boolean?, icon: String, size: Dp = 34.dp, iconSize: Dp = 20.dp) {
    val active = on == true
    Box(
        Modifier.size(size).clip(CircleShape).border(2.dp, if (active) Qs.Energy else Color(0x2EFFFFFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        QsIcon(icon, if (active) Qs.Energy else Qs.Dim, iconSize)
        if (!active) Box(Modifier.width(size).height(2.dp).rotate(-45f).background(Qs.Dim))
    }
}

@Composable
fun AssistTile(car: CarState, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.desc_assist, onOffLabel(car.aeb), onOffLabel(car.autoHold), onOffLabel(car.laneKeep))
    TileFrame(description, modifier) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistRing(car.aeb, Icons.Brake)
                AssistRing(car.autoHold, Icons.AutoHold)
            }
            AssistRing(car.laneKeep, Icons.Lane)
        }
    }
}

@Composable
fun Gap(size: Dp) = Spacer(Modifier.size(size))
