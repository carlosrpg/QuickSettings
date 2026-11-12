package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosrpg.cherry.quicksettings.car.CarItems
import com.carlosrpg.cherry.quicksettings.car.CarItems.BatteryMode
import com.carlosrpg.cherry.quicksettings.car.CarItems.Regen
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.nav.NavState

@Composable
fun QsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape).background(Qs.Tile).border(1.dp, Color(0x14FFFFFF), shape).padding(18.dp),
        content = content,
    )
}

@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier, color = Qs.Muted, fontSize = Qs.Label,
    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
)

@Composable
fun QsText(text: String, size: TextUnit = Qs.Body, color: Color = Qs.Text, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier) =
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** 56 dp button; with [label] it grows to fit. */
@Composable
fun GlassButton(
    icon: String?,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    tint: Color = Qs.Text,
    background: Color = Qs.Button,
    size: Dp = Qs.TouchMin,
    filledIcon: Boolean = false,
    shape: RoundedCornerShape = RoundedCornerShape(Qs.ButtonRadius),
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .height(size)
            .then(if (label == null && leading == null) Modifier.width(size) else Modifier)
            .clip(shape)
            .background(background)
            .border(1.dp, Qs.Hairline, shape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (label == null && leading == null) 0.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (icon != null) QsIcon(icon, tint, 22.dp, filled = filledIcon)
        if (label != null) QsText(label, 15.sp, tint, FontWeight.SemiBold)
    }
}

/** Segmented control; [selected] is highlighted in [accent]. Every option is ≥ 48 dp tall. */
@Composable
fun <T> Segmented(options: List<T>, selected: T?, label: (T) -> String, accent: Color, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x47000000)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.weight(1f).height(48.dp).clip(shape)
                    .background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent)
                    .border(1.dp, if (on) accent.copy(alpha = 0.4f) else Color.Transparent, shape)
                    .clickable(role = Role.RadioButton) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                QsText(label(option), 14.sp, if (on) accent else Color(0xFFB8C3D1), FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun OpenAppChip(color: Color, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassButton(
        icon = Icons.OpenApp, description = "Open $label", onClick = onClick, modifier = modifier,
        label = label, tint = Qs.Text,
        leading = { Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(color)) },
    )
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MediaCard(media: MediaState, actions: DrawerActions, listening: Boolean, modifier: Modifier = Modifier) = QsCard(modifier) {
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        val artShape = RoundedCornerShape(18.dp)
        Box(Modifier.size(150.dp).clip(artShape).background(ArtPlaceholder)) {
            media.art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (media.packageName != null) {
                    OpenAppChip(Color(0xFFE2557A), media.appLabel ?: "Media app", actions::openMediaApp)
                }
                Box(Modifier.weight(1f))
                GlassButton(
                    Icons.Mic, "Voice search in ${media.appLabel ?: "media app"}", actions::voiceMedia,
                    label = if (listening) "Listening" else "Search",
                    tint = if (listening) Qs.Route else Qs.Text,
                )
            }
            QsText(media.title ?: if (media.hasAccess) "Nothing playing" else "Allow notification access", 22.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            QsText(media.artist.orEmpty(), 15.sp, Qs.Muted)
            Box(Modifier.weight(1f))
            if (media.durationMs > 0) {
                val fraction = (media.positionMs / media.durationMs.toFloat()).coerceIn(0f, 1f)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x26FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Color.White))
                }
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    QsText(formatTime(media.positionMs), 12.sp, Qs.Muted)
                    QsText(formatTime(media.durationMs), 12.sp, Qs.Muted)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassButton(Icons.Prev, "Previous track", actions.media::previous, shape = RoundedCornerShape(28.dp), filledIcon = true)
        Gap(10.dp)
        GlassButton(
            if (media.playing) Icons.Pause else Icons.Play, if (media.playing) "Pause" else "Play",
            actions.media::playPause, size = 64.dp, background = Color.White, tint = Color(0xFF0B1018),
            filledIcon = true, shape = RoundedCornerShape(32.dp),
        )
        Gap(10.dp)
        GlassButton(Icons.Next, "Next track", actions.media::next, shape = RoundedCornerShape(28.dp), filledIcon = true)
        Box(Modifier.weight(1f))
        GlassButton(if (media.muted) Icons.Mute else Icons.Volume, if (media.muted) "Unmute" else "Mute", actions.media::toggleMute)
        Gap(8.dp)
        GlassButton(Icons.Minus, "Volume down", { actions.media.volumeStep(-1) })
        Gap(8.dp)
        val volFraction = if (media.maxVolume > 0) media.volume / media.maxVolume.toFloat() else 0f
        Box(Modifier.width(90.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x26FFFFFF))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(if (media.muted) 0f else volFraction).background(Qs.Text))
        }
        Gap(8.dp)
        GlassButton(Icons.Plus, "Volume up", { actions.media.volumeStep(1) })
        QsText("${media.volume}", 15.sp, weight = FontWeight.Bold, modifier = Modifier.width(30.dp).padding(start = 6.dp))
    }
}

@Composable
fun NavCard(nav: NavState?, actions: DrawerActions, listening: Boolean, modifier: Modifier = Modifier) = QsCard(modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OpenAppChip(Qs.Route, nav?.appLabel ?: actions.navAppLabel() ?: "Navigation", actions::openNavApp, Modifier.weight(1f))
        Gap(10.dp)
        GlassButton(Icons.Mic, "Voice search a destination", actions::voiceNav, tint = if (listening) Qs.Route else Qs.Text)
    }
    if (nav == null) {
        Column(
            Modifier.weight(1f).fillMaxWidth().clickable(onClickLabel = "Search a destination", onClick = actions::voiceNav),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QsIcon(Icons.NavArrow, Qs.Route, 48.dp)
            QsText(if (listening) "Listening…" else "Where to?", 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            QsText("Tap and say a destination", 13.sp, Qs.Muted)
        }
    } else {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)).background(Qs.Route), contentAlignment = Alignment.Center) {
                if (nav.maneuver != null) Image(nav.maneuver.asImageBitmap(), null, Modifier.size(44.dp))
                else QsIcon(Icons.TurnRight, Color.White, 40.dp, strokeWidth = 2.6f)
            }
            Column(Modifier.padding(start = 14.dp)) {
                QsText(nav.title.orEmpty(), 26.sp, weight = FontWeight.Bold)
                QsText(nav.text.orEmpty(), 14.sp, Qs.TextSoft)
            }
        }
        Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Qs.Hairline))
        if (nav.subText != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QsIcon(Icons.Pin, Qs.Muted, 18.dp)
                QsText(nav.subText, 18.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AssistCard(car: CarState, modifier: Modifier = Modifier) = QsCard(modifier) {
    CardLabel("Driver assist")
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistRow(car.aeb, Icons.Brake, "Emergency braking")
        AssistRow(car.autoHold, Icons.AutoHold, "Auto hold")
        AssistRow(car.laneKeep, Icons.Lane, "Lane keeping")
    }
}

@Composable
private fun AssistRow(on: Boolean?, icon: String, name: String) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x38000000)).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistRing(on, icon, 40.dp, 22.dp)
        QsText(name, 14.sp, if (on == true) Qs.Text else Qs.Muted, FontWeight.SemiBold, Modifier.weight(1f).padding(start = 12.dp))
        val (text, fg, bg) = when (on) {
            true -> Triple("On", Qs.Energy, Qs.Energy.copy(alpha = 0.16f))
            false -> Triple("Off", Qs.Muted, Color(0x14FFFFFF))
            null -> Triple("--", Qs.Muted, Color(0x14FFFFFF))
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
            QsText(text, 13.sp, fg, FontWeight.Bold)
        }
    }
}

@Composable
fun ClimateCard(car: CarState, actions: DrawerActions, modifier: Modifier = Modifier) = QsCard(modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CardLabel("Climate · driver")
        car.outsideTempC?.let { QsText("out $it°", 13.sp, Qs.Muted) }
    }
    val temp = car.cabinTempC
    val fan = car.fanLevel
    Column(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.height(140.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.CenterHorizontally) {
                GlassButton(Icons.Plus, "Warmer", { actions.car.stepCabinTemp(1) }, tint = Color(0xFFFF9A5C))
                QsText(temp?.let { "$it°" } ?: "--", 16.sp, weight = FontWeight.Bold)
                GlassButton(Icons.Minus, "Cooler", { actions.car.stepCabinTemp(-1) }, tint = Color(0xFF6EA0FF))
            }
            Box(Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(Qs.TempGradient)) {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xC70A0E14), Color(0x0D0A0E14)))))
                if (temp != null && fan != null) {
                    val x = 140.dp * (fan / 10f).coerceIn(0f, 1f)
                    val y = 140.dp * (1f - ((temp - 16) / 14f).coerceIn(0f, 1f))
                    Box(Modifier.offset(y = y).width(x).height(1.dp).background(Color(0x8CFFFFFF)))
                    Box(Modifier.offset(x = x, y = y).width(1.dp).fillMaxHeight().background(Color(0x8CFFFFFF)))
                    Box(Modifier.offset(x = x - 10.dp, y = y - 10.dp).size(20.dp).clip(CircleShape).background(Color.White).border(5.dp, Color(0x40FFFFFF), CircleShape))
                }
            }
        }
        Row(Modifier.padding(start = 66.dp, top = 10.dp).width(140.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            GlassButton(Icons.Minus, "Less fan", { actions.car.stepFan(-1) })
            QsText(fan?.toString() ?: "--", 16.sp, weight = FontWeight.Bold)
            GlassButton(Icons.Plus, "More fan", { actions.car.stepFan(1) })
        }
    }
}

@Composable
fun DriveEnergyCard(car: CarState, actions: DrawerActions, modifier: Modifier = Modifier) = QsCard(modifier) {
    CardLabel("Battery mode")
    Segmented(BatteryMode.entries, car.batteryMode, { it.label() }, Qs.Mode, actions.car::setBatteryMode, Modifier.padding(top = 6.dp))
    if (car.batteryMode == BatteryMode.FORCED) {
        // Written once on release, not on every step.
        var dragging by remember { mutableStateOf(false) }
        var value by remember { mutableFloatStateOf((car.holdTarget ?: 50).toFloat()) }
        if (!dragging) value = (car.holdTarget ?: 50).toFloat()
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            QsText("Hold battery at", 13.sp, Qs.TextSoft, FontWeight.SemiBold)
            QsText("${value.toInt()}%", 15.sp, Qs.Mode, FontWeight.Bold)
        }
        Slider(
            value = value,
            onValueChange = { dragging = true; value = it },
            onValueChangeFinished = { dragging = false; actions.car.setHoldTarget(value.toInt()) },
            valueRange = CarItems.HOLD_MIN.toFloat()..CarItems.HOLD_MAX.toFloat(),
            steps = (CarItems.HOLD_MAX - CarItems.HOLD_MIN) / 5 - 1,
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Qs.Mode, inactiveTrackColor = Color(0x1FFFFFFF)),
            modifier = Modifier.height(40.dp).semantics { contentDescription = "Battery hold target" },
        )
    }
    Box(Modifier.weight(1f)) // regen stays at the bottom whether or not the slider shows
    CardLabel("Regeneration", Modifier.padding(top = 8.dp))
    Segmented(Regen.entries, car.regen, { it.label() }, Qs.Energy, actions.car::setRegen, Modifier.padding(top = 6.dp))
}

@Composable
fun RangeCard(car: CarState, modifier: Modifier = Modifier) = QsCard(modifier) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        CardLabel("Range")
        val total = listOfNotNull(car.electricRangeKm, car.fuelRangeKm).takeIf { it.isNotEmpty() }?.sum()
        QsText("total ${total?.let { "$it km" } ?: "--"}", 14.sp, Qs.Muted)
    }
    Row(Modifier.weight(1f).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        RangeHalf("Electric", Icons.Battery, Qs.chargeColor(car.batterySoc), car.electricRangeKm, car.batterySoc, car.chargeLimit?.let { "limit $it%" }, car.chargeLimit)
        RangeHalf("Fuel", Icons.Fuel, Qs.Fuel, car.fuelRangeKm, car.fuelPercent, "tank", null)
    }
}

@Composable
private fun RowScope.RangeHalf(name: String, icon: String, color: Color, km: Int?, percent: Int?, note: String?, marker: Int?) {
    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Color(0x38000000)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QsIcon(icon, color, 20.dp)
            QsText(name, 14.sp, color, FontWeight.SemiBold)
        }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
            QsText(km?.toString() ?: "--", 44.sp, weight = FontWeight.Bold)
            QsText("km", 18.sp, Qs.Muted, modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
        }
        Box(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(7.dp)).background(Color(0x1AFFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth((percent ?: 0) / 100f).background(color))
            }
            if (marker != null) {
                Box(Modifier.fillMaxWidth(marker / 100f).fillMaxHeight()) {
                    Box(Modifier.align(Alignment.CenterEnd).width(2.dp).height(24.dp).background(Qs.Text))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            QsText(percent?.let { "$it%" } ?: "--", 13.sp, color, FontWeight.Bold)
            note?.let { QsText(it, 13.sp, Qs.Muted) }
        }
    }
}
