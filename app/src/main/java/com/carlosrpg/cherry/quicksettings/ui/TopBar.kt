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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The info strip that lives in the status bar's area. The car draws its own voice avatar
 * (left) and clock and icons (right) over this strip, so content stays between [startInset]
 * and [endInset]. Display only: the car's status bar, a higher layer, takes the touches here.
 * No background of its own: it sits on the car's bar. While a route is active the next turn
 * joins it, and range, regeneration and climate shrink to make room.
 */
@Composable
fun TopBar(car: CarState, media: MediaState, nav: NavState?, startInset: Dp, endInset: Dp) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = startInset, end = endInset)) {
        if (nav == null) FullBar(car, media) else NavBar(car, media, nav, maxHeight)
    }
}

@Composable
private fun NavBar(car: CarState, media: MediaState, nav: NavState, height: Dp) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        NowPlaying(media, Modifier.weight(1f))
        NextTurn(nav, Modifier.weight(1.3f))
        // Range: the drawer's two half rings, with the electric % over the fuel % inside.
        MirrorRing(car.batterySoc?.div(100f), car.fuelPercent?.div(100f), Qs.chargeColor(car.batterySoc), height * 0.86f, 4.dp) {
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
                QsText(car.cabinTempC?.let { "$it°" } ?: "–", 16.sp, Qs.Text, FontWeight.SemiBold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                QsIcon(Icons.Wind, Qs.Cool, 20.dp)
                Spacer(Modifier.width(4.dp))
                QsText(car.fanLevel?.toString() ?: "–", 16.sp, Qs.Text, FontWeight.SemiBold)
            }
        }
    }
}

/** The next manoeuvre, as on the drawer's navigation card: arrow, distance, then the street. */
@Composable
private fun NextTurn(nav: NavState, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Qs.Route), Alignment.Center) {
            if (nav.maneuver != null) Image(nav.maneuver.asImageBitmap(), null, Modifier.size(40.dp))
            else QsIcon(Icons.TurnRight, Color.White, 36.dp, strokeWidth = 2.6f)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            nav.title?.let { Text(it, color = Qs.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            nav.text?.let { Text(it, color = Qs.TextSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun FullBar(car: CarState, media: MediaState) {
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        NowPlaying(media, Modifier.weight(1f))
        Stat(Icons.BatteryBolt, Qs.Energy, car.batterySoc?.let { "$it%" }, car.electricRangeKm?.let { "$it km" })
        Stat(Icons.Fuel, Qs.Fuel, car.fuelPercent?.let { "$it%" }, car.fuelRangeKm?.let { "$it km" })
        Row(verticalAlignment = Alignment.CenterVertically) {
            RegenDial(car.regen, 34.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                QsText(car.regen?.label() ?: "–", 18.sp, Qs.Energy, FontWeight.SemiBold)
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
        QsIcon(Icons.Thermometer, Qs.Warm, 28.dp)
        Spacer(Modifier.width(6.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QsText(car.cabinTempC?.let { "$it°" } ?: "–", 22.sp, Qs.Text, FontWeight.SemiBold)
                Spacer(Modifier.width(10.dp))
                QsIcon(Icons.Wind, Qs.Cool, 20.dp)
                Spacer(Modifier.width(4.dp))
                QsText(car.fanLevel?.toString() ?: "–", 18.sp, Qs.Text, FontWeight.SemiBold)
            }
            car.outsideTempC?.let { QsText(stringResource(R.string.outside_temp, it), 14.sp, Qs.Muted) }
        }
    }
}

@Composable
private fun NowPlaying(media: MediaState, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        val art = media.art ?: media.appIcon
        if (art != null) {
            Image(
                art.asImageBitmap(), null,
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Qs.Tile), Alignment.Center) {
                QsIcon(if (media.playing) Icons.Pause else Icons.Play, Qs.Muted, 28.dp, filled = true)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            QsText(media.title ?: stringResource(R.string.nothing_playing), 20.sp, weight = FontWeight.SemiBold)
            listOfNotNull(media.artist, media.appLabel).firstOrNull()?.let { QsText(it, 15.sp, Qs.Muted) }
        }
    }
}

@Composable
private fun Stat(icon: String, tint: Color, value: String?, sub: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        QsIcon(icon, tint, 28.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            QsText(value ?: "–", 22.sp, tint, FontWeight.SemiBold)
            sub?.let { QsText(it, 14.sp, Qs.Muted) }
        }
    }
}
