package com.carlosrpg.cherry.quicksettings.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.weather.WeatherKind
import com.carlosrpg.cherry.quicksettings.weather.WeatherProblem
import com.carlosrpg.cherry.quicksettings.weather.WeatherRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlinx.coroutines.delay

/**
 * Where, when and how warm it is now, then the days ahead (as many as fit). The current weather
 * is the card's backdrop: a large picture fading into the sky-tinted background.
 */
@Composable
fun WeatherCard(weather: WeatherRepository, modifier: Modifier = Modifier) {
    val state by weather.state.collectAsState()
    LaunchedEffect(Unit) { weather.refresh() }
    val context = LocalContext.current
    // The system's own patterns: its 12/24-hour setting and the order and words of its language.
    val locale = LocalConfiguration.current.locales[0]
    val is24 = android.text.format.DateFormat.is24HourFormat(context)
    val timeFormat = remember(locale, is24) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, if (is24) "Hm" else "hm"), locale)
    }
    val dateFormat = remember(locale) {
        DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEdMMM"), locale)
    }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val forecast = state.forecast
    val shape = RoundedCornerShape(24.dp)
    val sky = forecast?.let { skyTint(it.kind, it.isDay) } ?: Qs.Route
    BoxWithConstraints(
        modifier.clip(shape).background(Qs.Tile)
            .background(Brush.verticalGradient(listOf(sky.copy(alpha = 0.34f), Color.Transparent)))
            .border(1.dp, Color(0x14FFFFFF), shape),
    ) {
        val cardWidth = maxWidth
        if (forecast != null) {
            val art = minOf(maxWidth * 0.68f, maxHeight * 0.6f)
            WeatherArt(
                forecast.kind, forecast.isDay, art,
                Modifier.align(Alignment.TopEnd).offset(x = art * 0.2f, y = -art * 0.08f).fadeIntoCard(),
            )
        }
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                QsIcon(Icons.Pin, Qs.TextSoft, 15.dp)
                QsText(forecast?.place ?: stringResource(R.string.widget_weather), 14.sp, Qs.TextSoft, FontWeight.SemiBold)
            }
            QsText(now.format(timeFormat), 30.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            QsText(now.format(dateFormat).replaceFirstChar { it.titlecase(locale) }, 13.sp, Qs.Muted, FontWeight.SemiBold)
            if (forecast == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val message = when (state.problem) {
                        WeatherProblem.NO_PERMISSION -> R.string.weather_no_permission
                        WeatherProblem.NO_LOCATION -> R.string.weather_no_location
                        WeatherProblem.UNAVAILABLE -> R.string.weather_unavailable
                        null -> R.string.weather_loading
                    }
                    QsText(stringResource(message), 13.sp, Qs.Muted, FontWeight.SemiBold)
                }
                return@Column
            }
            // The temperature fills the room between the clock and the week; the condition and
            // today's high and low sit beside it at one size.
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                val tempSize = with(LocalDensity.current) { minOf(maxHeight, (maxWidth - 140.dp) / 1.7f).coerceAtLeast(48.dp).toSp() }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "${forecast.tempC}°", color = Qs.Text, fontSize = tempSize, lineHeight = tempSize,
                        fontWeight = FontWeight.Bold, maxLines = 1,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        QsText(stringResource(forecast.kind.labelRes()), 18.sp, Qs.Text, FontWeight.SemiBold)
                        forecast.days.firstOrNull()?.let { today ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                QsText("↑ ${today.maxC}°", 18.sp, Qs.TextSoft, FontWeight.SemiBold)
                                QsText("↓ ${today.minC}°", 18.sp, Qs.Muted, FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
            Box(Modifier.padding(vertical = 10.dp).fillMaxWidth().height(1.dp).background(Qs.Hairline))
            val count = ((cardWidth - 36.dp) / 42.dp).toInt().coerceIn(3, 7).coerceAtMost(forecast.days.size)
            val today = stringResource(R.string.weather_today)
            Row(Modifier.fillMaxWidth()) {
                forecast.days.take(count).forEachIndexed { i, day ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val name = if (i == 0) today else day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                            .trimEnd('.').replaceFirstChar { it.titlecase(locale) }
                        QsText(name, 11.sp, if (i == 0) Qs.Text else Qs.Muted, FontWeight.SemiBold)
                        WeatherArt(day.kind, true, 26.dp)
                        QsText("${day.maxC}°", 13.sp, weight = FontWeight.Bold)
                        QsText("${day.minC}°", 11.sp, Qs.Muted)
                    }
                }
            }
        }
    }
}

/** Half see-through, and fading out toward the lower left where the text is. */
private fun Modifier.fadeIntoCard(): Modifier =
    graphicsLayer { alpha = 0.6f; compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.Black),
                    start = Offset(size.width * 0.1f, size.height * 0.95f), end = Offset(size.width * 0.6f, size.height * 0.35f),
                ),
                blendMode = BlendMode.DstIn,
            )
        }

/** The semi-open drawer's tile: the picture and the temperature. */
@Composable
fun WeatherTile(weather: WeatherRepository, modifier: Modifier = Modifier) {
    val state by weather.state.collectAsState()
    LaunchedEffect(Unit) { weather.refresh() }
    val forecast = state.forecast
    val description = forecast?.let { stringResource(R.string.desc_weather, it.tempC, stringResource(it.kind.labelRes())) }
        ?: stringResource(R.string.widget_weather)
    TileFrame(description, modifier) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            if (forecast == null) {
                QsIcon(Icons.Sun, Qs.Muted, 36.dp)
            } else {
                WeatherArt(forecast.kind, forecast.isDay, 48.dp)
                QsText("${forecast.tempC}°", 18.sp, weight = FontWeight.Bold)
            }
        }
    }
}

/** The colour of the sky behind the card. */
private fun skyTint(kind: WeatherKind, day: Boolean): Color = when (kind) {
    WeatherKind.CLEAR, WeatherKind.PARTLY_CLOUDY -> if (day) Color(0xFF3D8BFF) else Color(0xFF4A4FB0)
    WeatherKind.CLOUDY -> Color(0xFF7C8AA3)
    WeatherKind.FOG -> Color(0xFF8C97A8)
    WeatherKind.DRIZZLE, WeatherKind.RAIN -> Color(0xFF3F74B0)
    WeatherKind.SNOW -> Color(0xFF9DB7D6)
    WeatherKind.THUNDER -> Color(0xFF6A55B0)
}

@StringRes
private fun WeatherKind.labelRes(): Int = when (this) {
    WeatherKind.CLEAR -> R.string.weather_clear
    WeatherKind.PARTLY_CLOUDY -> R.string.weather_partly_cloudy
    WeatherKind.CLOUDY -> R.string.weather_cloudy
    WeatherKind.FOG -> R.string.weather_fog
    WeatherKind.DRIZZLE -> R.string.weather_drizzle
    WeatherKind.RAIN -> R.string.weather_rain
    WeatherKind.SNOW -> R.string.weather_snow
    WeatherKind.THUNDER -> R.string.weather_thunder
}
