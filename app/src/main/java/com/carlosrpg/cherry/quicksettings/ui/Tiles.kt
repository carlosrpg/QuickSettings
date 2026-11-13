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
    TileFrame(description, modifier) {
        ClimateQuadrant(temp, fan, QuadrantStyle.Tile, acOn = car.acOn)
    }
}

/** Initial = a marker with a bolt, Smart = a light bulb, Forced = a charging battery. */
fun BatteryMode?.icon(): String = when (this) {
    BatteryMode.INITIAL -> Icons.MarkerBolt
    BatteryMode.SMART -> Icons.Bulb
    BatteryMode.FORCED -> Icons.BatteryBolt
    null -> Icons.Battery
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

/**
 * Regenerative braking as a braked wheel: the brake disc with a bolt at its hub, the caliper
 * gripping it at the upper right, and the wheel's rim running round the rest. The rim is three
 * arcs, and as many are green as the level: one for Low, two for Medium, all three for High.
 */
@Composable
fun RegenDial(regen: RegenLevel?, size: Dp, modifier: Modifier = Modifier) {
    val lit = when (regen) { RegenLevel.LOW -> 1; RegenLevel.MEDIUM -> 2; RegenLevel.HIGH -> 3; null -> 0 }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = this.size.minDimension
            val centre = Offset(this.size.width / 2, this.size.height / 2)
            fun ring(radius: Float) = Offset(centre.x - radius, centre.y - radius) to Size(radius * 2, radius * 2)

            // The rim: open at the upper right, where the caliper sits. Angles are clockwise
            // from 3 o'clock; the arcs fill from the lower right round to the top.
            val rimWidth = s * 0.085f
            val (rimAt, rimSize) = ring(s / 2 - rimWidth / 2)
            val segment = (RIM_SWEEP - 2 * RIM_GAP) / 3
            for (i in 0 until 3) {
                drawArc(
                    if (i < lit) Qs.Energy else Color(0x2EFFFFFF), RIM_START + i * (segment + RIM_GAP), segment, false,
                    rimAt, rimSize, style = Stroke(rimWidth, cap = StrokeCap.Round),
                )
            }
            // The brake disc.
            val discRadius = s * 0.27f
            drawCircle(Color(0xB3FFFFFF), discRadius, centre, style = Stroke(s * 0.055f))
            // The caliper: a thick pad over the disc's edge, with its mounting bolt.
            val (padAt, padSize) = ring(s * 0.35f)
            drawArc(Qs.Text, CALIPER_START, CALIPER_SWEEP, false, padAt, padSize, style = Stroke(s * 0.2f, cap = StrokeCap.Round))
            val mid = Math.toRadians((CALIPER_START + CALIPER_SWEEP / 2).toDouble())
            drawCircle(
                Color(0xFF161C26), s * 0.035f,
                centre + Offset((s * 0.37f * kotlin.math.cos(mid)).toFloat(), (s * 0.37f * kotlin.math.sin(mid)).toFloat()),
            )
        }
        QsIcon(Icons.Bolt, if (lit > 0) Qs.Energy else Qs.Dim, size * 0.28f, strokeWidth = 2.2f)
    }
}

private const val RIM_START = 12f
private const val RIM_SWEEP = 246f
private const val RIM_GAP = 14f
private const val CALIPER_START = -70f
private const val CALIPER_SWEEP = 50f

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
            RegenDial(car.regen, 30.dp)
        }
    }
}

@Composable
fun AutonomyTile(car: CarState, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.desc_autonomy,
        car.batterySoc?.toString() ?: "--", car.electricRangeKm?.toString() ?: "--",
        car.fuelPercent?.toString() ?: "--", car.fuelRangeKm?.toString() ?: "--",
        totalRange(car.electricRangeKm, car.fuelRangeKm)?.toString() ?: "--",
    )
    TileFrame(description, modifier) {
        MirrorRangeTile(car.batterySoc, car.electricRangeKm, car.fuelPercent, car.fuelRangeKm, Qs.chargeColor(car.batterySoc))
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
