package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaState

/**
 * The info strip that lives in the status bar's area. The car draws its own voice avatar
 * (left) and clock and icons (right) over this strip, so content stays between [startInset]
 * and [endInset]. Display only: the car's status bar, a higher layer, takes the touches here.
 */
@Composable
fun TopBar(car: CarState, media: MediaState, startInset: Dp, endInset: Dp) {
    Row(
        Modifier.fillMaxSize().background(Qs.Glass).padding(start = startInset, end = endInset),
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
