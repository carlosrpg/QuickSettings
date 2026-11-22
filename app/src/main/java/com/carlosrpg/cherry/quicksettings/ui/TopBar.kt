package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.nav.NavState

/**
 * The info strip that lives in the status bar's area. The car draws its own icons (Bluetooth,
 * wireless, clock) over both ends of this strip, so content stays between [startInset] and
 * [endInset]. Display only: the car's status bar, a higher layer, takes the touches here.
 * No background of its own: it sits on the car's bar. While a route is active the next turn
 * joins it, and range, regeneration and climate shrink to make room.
 */
@Composable
fun TopBar(car: CarState, media: MediaState, nav: NavState?, startInset: Dp, endInset: Dp) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = startInset, end = endInset)) {
        if (nav == null) FullBar(car, media) else NavBar(car, media, nav, maxWidth, maxHeight)
    }
}

@Composable
private fun NavBar(car: CarState, media: MediaState, nav: NavState, width: Dp, height: Dp) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        // The next turn takes only the room its fields need (up to half), media gets the rest.
        NowPlaying(media, Modifier.weight(1f))
        NextTurn(nav, Modifier.widthIn(max = width / 2), height)
        // Range: the drawer's two half rings, with the electric % over the fuel % inside.
        MirrorRing(car.batterySoc?.div(100f), car.fuelPercent?.div(100f), Qs.chargeColor(car.batterySoc), height * 0.7f, 4.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(car.batterySoc?.let { "$it%" } ?: "–", color = Qs.chargeColor(car.batterySoc), fontSize = 14.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(car.fuelPercent?.let { "$it%" } ?: "–", color = Qs.Fuel, fontSize = 14.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        RegenDial(car.regen, 34.dp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QsIcon(Icons.Thermometer, Qs.Warm, 20.dp)
                Spacer(Modifier.width(4.dp))
                Shadowed { QsText(car.cabinTempC?.let { "$it°" } ?: "–", 16.sp, Qs.Text, FontWeight.SemiBold) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                QsIcon(Icons.Wind, Qs.Cool, 20.dp)
                Spacer(Modifier.width(4.dp))
                Shadowed { QsText(car.fanLevel?.toString() ?: "–", 16.sp, Qs.Text, FontWeight.SemiBold) }
            }
        }
    }
}

/**
 * The next manoeuvre with everything the drawer's navigation card shows: the arrow tile, the
 * distance, the street, and the ETA line (pin icon) when the app sends one. Lines are packed
 * tight so all three fit the bar's height.
 */
@Composable
private fun NextTurn(nav: NavState, modifier: Modifier, height: Dp) {
    val tile = (height * 0.7f).coerceIn(40.dp, 56.dp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        // Drop shadow and dark rim, like the text halo, so the tile stands out on a light map.
        val shape = RoundedCornerShape(tile * 0.26f)
        Box(
            Modifier.size(tile).shadow(4.dp, shape).border(1.dp, Color.Black.copy(alpha = 0.35f), shape).clip(shape).background(Qs.Route),
            Alignment.Center,
        ) {
            if (nav.maneuver != null) Image(nav.maneuver.asImageBitmap(), null, Modifier.size(tile * 0.7f))
            else QsIcon(Icons.TurnRight, Color.White, tile * 0.64f, strokeWidth = 2.6f)
        }
        Spacer(Modifier.width(10.dp))
        Shadowed {
            Column {
                nav.title?.let { Text(it, color = Qs.Text, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                nav.text?.let { Text(it, color = BarSoft, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                nav.subText?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QsIcon(Icons.Pin, BarSoft, 12.dp)
                        Spacer(Modifier.width(3.dp))
                        Text(it, color = BarSoft, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullBar(car: CarState, media: MediaState) {
    // The strip is narrow (about 550 dp). The stats are kept tight (24 dp icons, 8 dp gaps) so the
    // song keeps about 100 dp of text beside them; looser spacing left it none.
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NowPlaying(media, Modifier.weight(1f))
        Stat(Icons.BatteryBolt, Qs.Energy, car.batterySoc?.let { "$it%" }, car.electricRangeKm?.let { "$it km" })
        Stat(Icons.Fuel, Qs.Fuel, car.fuelPercent?.let { "$it%" }, car.fuelRangeKm?.let { "$it km" })
        Row(verticalAlignment = Alignment.CenterVertically) {
            RegenDial(car.regen, 30.dp)
            Spacer(Modifier.width(5.dp))
            Column {
                QsText(car.regen?.label() ?: "–", 17.sp, Qs.Energy, FontWeight.SemiBold)
                QsText(stringResource(R.string.regeneration), 13.sp, Qs.Muted)
            }
        }
        Climate(car)
    }
}

/** Set temperature and fan level, with the outside temperature under them. */
@Composable
private fun Climate(car: CarState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        QsIcon(Icons.Thermometer, Qs.Warm, 24.dp)
        Spacer(Modifier.width(4.dp))
        Column {
            Shadowed {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    QsText(car.cabinTempC?.let { "$it°" } ?: "–", 20.sp, Qs.Text, FontWeight.SemiBold)
                    Spacer(Modifier.width(6.dp))
                    QsIcon(Icons.Wind, Qs.Cool, 20.dp)
                    Spacer(Modifier.width(4.dp))
                    QsText(car.fanLevel?.toString() ?: "–", 18.sp, Qs.Text, FontWeight.SemiBold)
                }
                car.outsideTempC?.let { QsText(stringResource(R.string.outside_temp, it), 14.sp, BarSoft) }
            }
        }
    }
}

@Composable
private fun NowPlaying(media: MediaState, modifier: Modifier, art: Dp = 48.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val cover = media.art ?: media.appIcon
        if (cover != null) {
            Image(
                cover.asImageBitmap(), null,
                Modifier.size(art).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(art).clip(RoundedCornerShape(12.dp)).background(Qs.Tile), Alignment.Center) {
                QsIcon(if (media.playing) Icons.Pause else Icons.Play, Qs.Muted, 28.dp, filled = true)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Shadowed {
                QsText(media.title ?: stringResource(R.string.nothing_playing), 20.sp, weight = FontWeight.SemiBold)
                listOfNotNull(media.artist, media.appLabel).firstOrNull()?.let { QsText(it, 15.sp, BarSoft) }
            }
        }
    }
}

@Composable
private fun Stat(icon: String, tint: Color, value: String?, sub: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        QsIcon(icon, tint, 24.dp)
        Spacer(Modifier.width(5.dp))
        Column {
            QsText(value ?: "–", 20.sp, tint, FontWeight.SemiBold)
            sub?.let { QsText(it, 14.sp, Qs.Muted) }
        }
    }
}

/**
 * White text over whatever the car's bar shows (a light map, a wallpaper) gets a tight black
 * halo with a slight drop, so it reads on any background.
 */
/**
 * Secondary text under the halo. The drawer's mid greys read as a dark smudge once the black
 * halo sits around them on a light map, so the bar's subtitles stay near-white and step down by
 * size and weight instead.
 */
private val BarSoft = Color(0xFFD9E0E8)

@Composable
private fun Shadowed(content: @Composable () -> Unit) {
    // In dp, so the halo is as thick on the car's denser screen as on the emulator.
    val shadow = with(LocalDensity.current) { Shadow(Color.Black, Offset(0f, 1.dp.toPx()), blurRadius = 3.dp.toPx()) }
    CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(shadow = shadow), content = content)
}
