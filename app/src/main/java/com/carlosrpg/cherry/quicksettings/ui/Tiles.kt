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
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.remember
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
 * Regenerative braking, drawn from the owner's icon: a brake disc with a bolt at its hub, the
 * caliper at the upper right, and the wheel's rim running round the rest. The rim is cut into
 * three sections, and as many are lit as the level: one for Low, two for Medium, three for High.
 */
@Composable
fun RegenDial(regen: RegenLevel?, size: Dp, modifier: Modifier = Modifier) {
    val lit = when (regen) { RegenLevel.LOW -> 1; RegenLevel.MEDIUM -> 2; RegenLevel.HIGH -> 3; null -> 0 }
    val rim = remember { PathParser().parsePathString(REGEN_RIM).toPath() }
    val brake = remember { PathParser().parsePathString(REGEN_BRAKE).toPath().apply { fillType = PathFillType.EvenOdd } }
    Canvas(modifier.size(size)) {
        scale(this.size.minDimension / REGEN_VIEWPORT, pivot = Offset.Zero) {
            drawPath(brake, if (lit > 0) Qs.Mode else Qs.Dim)
            // The rim is one shape; each third is painted through a wedge cut from the centre.
            val section = REGEN_RIM_SWEEP / 3
            for (i in 0 until 3) {
                val wedge = Path().apply {
                    moveTo(REGEN_VIEWPORT / 2, REGEN_VIEWPORT / 2)
                    arcTo(
                        Rect(-REGEN_VIEWPORT, -REGEN_VIEWPORT, 2 * REGEN_VIEWPORT, 2 * REGEN_VIEWPORT),
                        REGEN_RIM_START + i * section + REGEN_RIM_GAP / 2, section - REGEN_RIM_GAP, false,
                    )
                    close()
                }
                clipPath(wedge) { drawPath(rim, if (i < lit) Qs.Mode else Color(0x33FFFFFF)) }
            }
        }
    }
}

private const val REGEN_VIEWPORT = 32f

// The rim runs clockwise from just below 3 o'clock round to just left of 12 o'clock.
private const val REGEN_RIM_START = 9.6f
private const val REGEN_RIM_SWEEP = 250.8f
private const val REGEN_RIM_GAP = 7f
private const val REGEN_RIM = "M 13.835938 3.1835938 C 7.5579375 4.2335938 3 9.624 3 16 C 3 23.168 8.832 29 16 29 C 22.376 29 27.767406 24.442063 28.816406 18.164062 C 28.907406 17.619063 28.539141 17.104672 27.994141 17.013672 C 27.447141 16.922672 26.93575 17.291937 26.84375 17.835938 C 25.95475 23.146937 21.395 27 16 27 C 9.935 27 5 22.065 5 16 C 5 10.605 8.8530625 6.04425 14.164062 5.15625 C 14.709063 5.06525 15.077328 4.5508594 14.986328 4.0058594 C 14.896328 3.4618594 14.378937 3.0955937 13.835938 3.1835938 z"
private const val REGEN_BRAKE = "M 17.912109 2.1835938 C 17.472984 2.2054375 17.050234 2.3694531 16.708984 2.6582031 C 16.258984 3.0382031 16 3.5965469 16 4.1855469 L 16 8.265625 C 16 9.180625 16.607563 9.9724063 17.476562 10.191406 C 19.608563 10.731406 21.268594 12.390484 21.808594 14.521484 C 22.027594 15.391484 22.818375 16 23.734375 16 L 27.814453 16 C 28.404453 16 28.96175 15.740062 29.34375 15.289062 C 29.72875 14.833062 29.891016 14.234484 29.791016 13.646484 C 28.799016 7.7964844 24.203516 3.2029375 18.353516 2.2109375 C 18.205766 2.1856875 18.058484 2.1763125 17.912109 2.1835938 z M 18.019531 4.1816406 C 23.030531 5.0306406 26.968453 8.969 27.814453 14 L 23.748047 14.033203 C 23.027047 11.187203 20.812 8.973625 18 8.265625 L 18 4.1855469 L 18.019531 4.1816406 z M 21 6 C 20.448 6 20 6.448 20 7 C 20 7.552 20.448 8 21 8 C 21.552 8 22 7.552 22 7 C 22 6.448 21.552 6 21 6 z M 11.181641 7.4765625 C 10.792719 7.5184687 10.454437 7.7856406 10.335938 8.1816406 C 10.175937 8.7106406 10.475906 9.2677344 11.003906 9.4277344 L 11.300781 9.5175781 C 9.2627812 10.989578 8 13.365 8 16 C 8 20.411 11.589 24 16 24 C 19.569 24 22.649969 21.704156 23.667969 18.285156 C 23.825969 17.756156 23.523141 17.199969 22.994141 17.042969 C 22.463141 16.883969 21.908953 17.186844 21.751953 17.714844 C 20.987953 20.277844 18.677 22 16 22 C 12.691 22 10 19.309 10 16 C 10 14.07 10.907047 12.332656 12.373047 11.222656 L 12.226562 11.705078 C 12.066563 12.234078 12.366531 12.791172 12.894531 12.951172 C 12.991531 12.980172 13.088594 12.994141 13.183594 12.994141 C 13.613594 12.994141 14.010625 12.716203 14.140625 12.283203 L 14.957031 9.5742188 C 15.117031 9.0452188 14.817063 8.488125 14.289062 8.328125 L 11.580078 7.5136719 C 11.445828 7.4736719 11.311281 7.4625938 11.181641 7.4765625 z M 25 10 C 24.448 10 24 10.448 24 11 C 24 11.552 24.448 12 25 12 C 25.552 12 26 11.552 26 11 C 26 10.448 25.552 10 25 10 z M 17.210938 12.003906 C 17.143203 12.012953 17.075031 12.042813 17.019531 12.101562 L 13.070312 16.328125 C 12.907313 16.502125 13.03725 16.777344 13.28125 16.777344 L 15.154297 16.777344 L 14.558594 19.636719 C 14.494594 19.942719 14.897375 20.129578 15.109375 19.892578 L 18.931641 15.626953 C 19.088641 15.451953 18.957797 15.179688 18.716797 15.179688 L 16.826172 15.179688 L 17.564453 12.378906 C 17.625203 12.149406 17.414141 11.976766 17.210938 12.003906 z"

@Composable
fun ModeTile(car: CarState, modifier: Modifier = Modifier) {
    val description = stringResource(
        R.string.desc_mode,
        car.batteryMode?.label() ?: stringResource(R.string.value_unknown),
        car.regen?.label() ?: stringResource(R.string.value_unknown),
    )
    TileFrame(description, modifier) {
        // Regeneration is the tile: its icon takes nearly all of it. The battery mode is a small
        // badge over its lower right corner.
        RegenDial(car.regen, Qs.TileSize * 0.78f, Modifier.align(Alignment.Center))
        Box(
            Modifier.align(Alignment.BottomEnd).padding(5.dp).size(Qs.TileSize * 0.3f)
                .clip(CircleShape).background(Color(0xF0161C26)),
            contentAlignment = Alignment.Center,
        ) {
            QsIcon(car.batteryMode.icon(), Qs.Mode, Qs.TileSize * 0.2f)
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
