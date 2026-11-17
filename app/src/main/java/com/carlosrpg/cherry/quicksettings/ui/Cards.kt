package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.annotation.StringRes
import app.jconfig.carbus.carinfo.ClimateSwitch
import app.jconfig.carbus.carinfo.FcwLevel
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.carlosrpg.cherry.quicksettings.car.Assist
import com.carlosrpg.cherry.quicksettings.car.AssistBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.CabinTempSpec
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.NewEnergyIds
import app.jconfig.carbus.carinfo.RegenLevel
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.nav.NavState
import kotlin.math.roundToInt

@Composable
fun QsCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.clip(shape).background(Qs.Tile).border(1.dp, Color(0x14FFFFFF), shape)
            // A tap anywhere a button doesn't take (buttons handle their own).
            .then(if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick) else Modifier)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier) = Text(
    text.uppercase(), modifier, color = Qs.Muted, fontSize = Qs.Label,
    fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, maxLines = 1,
)

@Composable
fun QsText(text: String, size: TextUnit = Qs.Body, color: Color = Qs.Text, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier) =
    Text(text, modifier, color = color, fontSize = size, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** The media card's buttons, about half again the standard size: tapped by feel while driving. */
private val MEDIA_BUTTON = 70.dp
private val MEDIA_ICON = 32.dp
private val MEDIA_PLAY = 92.dp
private val MEDIA_PLAY_ICON = 40.dp
private val VOLUME_BUTTON_WIDTH = MEDIA_BUTTON * 2

/** 56 dp button; with [label] or [leading] it grows to fit. */
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
    square: Boolean = label == null && leading == null,
    iconSize: Dp = 22.dp,
) {
    Row(
        modifier
            .height(size)
            .then(if (square) Modifier.width(size) else Modifier)
            .clip(shape)
            .background(background)
            .border(1.dp, Qs.Hairline, shape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (square) 0.dp else 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        if (icon != null) QsIcon(icon, tint, iconSize, filled = filledIcon)
        if (label != null) QsText(label, 15.sp, tint, FontWeight.SemiBold)
    }
}

/** Segmented control; [selected] is highlighted in [accent]. Every option is ≥ 48 dp tall. */
@Composable
fun <T> Segmented(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    accent: Color,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
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

/** "Bring this app to the front": the app's own icon and name, with an open-app glyph. */
@Composable
fun OpenAppChip(icon: Bitmap?, fallback: Color, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassButton(
        icon = Icons.OpenApp, description = stringResource(R.string.open_app, label), onClick = onClick,
        modifier = modifier, label = label, tint = Qs.Text,
        leading = {
            if (icon != null) {
                Image(icon.asImageBitmap(), null, Modifier.size(26.dp).clip(RoundedCornerShape(7.dp)))
            } else {
                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(fallback))
            }
        },
    )
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

private const val ASSIST_NOTICE_MS = 4_000L

@Composable
fun MediaCard(media: MediaState, actions: DrawerActions, listening: Boolean, modifier: Modifier = Modifier) = QsCard(
    modifier, onClick = actions::mediaToFront,
    onClickLabel = stringResource(R.string.open_app, media.appLabel ?: stringResource(R.string.media_app)),
) {
    val appName = media.appLabel ?: stringResource(R.string.media_app)
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        val artShape = RoundedCornerShape(18.dp)
        Box(Modifier.size(150.dp).clip(artShape).background(ArtPlaceholder)) {
            media.art?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // The chip takes the free space (and ellipsizes in it); Search stays at the end.
                Box(Modifier.weight(1f)) {
                    if (media.openPackage != null) {
                        OpenAppChip(media.appIcon, Color(0xFFE2557A), appName, actions::openMediaApp)
                    } else if (media.appLabel != null) {
                        // A car source with no app to open (radio): just its name.
                        QsText(appName, 15.sp, Qs.TextSoft, FontWeight.SemiBold)
                    }
                }
                GlassButton(
                    Icons.Mic, stringResource(R.string.voice_search_in, appName), actions::voiceMedia,
                    label = stringResource(if (listening) R.string.listening else R.string.search),
                    tint = if (listening) Qs.Route else Qs.Text,
                    modifier = Modifier.listeningBorder(listening),
                    size = MEDIA_BUTTON, iconSize = MEDIA_ICON,
                )
            }
            val title = media.title
                ?: stringResource(if (media.hasAccess) R.string.nothing_playing else R.string.allow_notification_access)
            QsText(title, 22.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
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
    // The control row keeps its own gaps: a near miss between buttons does nothing, instead of
    // falling through to the card and opening the media app.
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).pointerInput(Unit) { detectTapGestures { } },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassButton(
            Icons.Prev, stringResource(R.string.previous_track), actions.media::previous,
            shape = RoundedCornerShape(MEDIA_BUTTON / 2), filledIcon = true, size = MEDIA_BUTTON, iconSize = MEDIA_ICON,
        )
        Gap(12.dp)
        GlassButton(
            if (media.playing) Icons.Pause else Icons.Play, stringResource(if (media.playing) R.string.pause else R.string.play),
            actions.media::playPause, size = MEDIA_PLAY, background = Color.White, tint = Color(0xFF0B1018),
            filledIcon = true, shape = RoundedCornerShape(MEDIA_PLAY / 2), iconSize = MEDIA_PLAY_ICON,
        )
        Gap(12.dp)
        GlassButton(
            Icons.Next, stringResource(R.string.next_track), actions.media::next,
            shape = RoundedCornerShape(MEDIA_BUTTON / 2), filledIcon = true, size = MEDIA_BUTTON, iconSize = MEDIA_ICON,
        )
        Box(Modifier.weight(1f))
        GlassButton(
            if (media.muted) Icons.Mute else Icons.Volume,
            stringResource(if (media.muted) R.string.unmute else R.string.mute), actions.media::toggleMute,
            size = MEDIA_BUTTON, iconSize = MEDIA_ICON,
        )
        Gap(8.dp)
        // Twice as wide as the other buttons: volume is the one tapped by feel while driving.
        GlassButton(
            Icons.Minus, stringResource(R.string.volume_down), { actions.media.volumeStep(-1) }, Modifier.width(VOLUME_BUTTON_WIDTH),
            size = MEDIA_BUTTON, iconSize = MEDIA_ICON,
        )
        Gap(8.dp)
        // On the car the level can't be read; its own volume bar pops up on every step instead.
        if (media.volumeKnown) {
            val volFraction = if (media.maxVolume > 0) media.volume / media.maxVolume.toFloat() else 0f
            Box(Modifier.width(90.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x26FFFFFF))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(if (media.muted) 0f else volFraction).background(Qs.Text))
            }
            Gap(8.dp)
        }
        GlassButton(
            Icons.Plus, stringResource(R.string.volume_up), { actions.media.volumeStep(1) }, Modifier.width(VOLUME_BUTTON_WIDTH),
            size = MEDIA_BUTTON, iconSize = MEDIA_ICON,
        )
        if (media.volumeKnown) {
            QsText("${media.volume}", 15.sp, weight = FontWeight.Bold, modifier = Modifier.width(30.dp).padding(start = 6.dp))
        }
    }
}

@Composable
fun NavCard(nav: NavState?, actions: DrawerActions, listening: Boolean, modifier: Modifier = Modifier) = QsCard(
    modifier, onClick = actions::navToFront,
    onClickLabel = stringResource(R.string.open_app, nav?.appLabel ?: actions.navAppLabel() ?: stringResource(R.string.navigation)),
) {
    val appName = nav?.appLabel ?: actions.navAppLabel() ?: stringResource(R.string.navigation)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OpenAppChip(actions.navAppIcon(), Qs.Route, appName, actions::openNavApp, Modifier.weight(1f))
        Gap(10.dp)
        GlassButton(
            Icons.Mic, stringResource(R.string.voice_destination), actions::voiceNav,
            tint = if (listening) Qs.Route else Qs.Text, modifier = Modifier.listeningBorder(listening),
        )
        // Send the nav app to the instrument cluster, or bring it back. Always shown; dimmed
        // and inert where the system reports no cluster display (looked up each time the card
        // is drawn, since a display can come and go).
        val hasCluster = actions.cluster.clusterDisplayId() != null
        val onCluster = actions.cluster.onCluster.collectAsState().value != null
        Gap(10.dp)
        GlassButton(
            Icons.Cluster,
            stringResource(
                when {
                    !hasCluster -> R.string.cluster_none
                    onCluster -> R.string.cluster_bring_back
                    else -> R.string.cluster_send
                }
            ),
            { if (hasCluster || onCluster) actions.toggleNavOnCluster() },
            tint = when { onCluster -> Qs.Energy; hasCluster -> Qs.Text; else -> Qs.Dim },
            background = if (onCluster) Qs.Energy.copy(alpha = 0.16f) else Qs.Button,
        )
    }
    // Without a route the whole middle is the mic; while listening it says so, route or not
    // (a new destination is being asked for).
    if (nav == null || listening) {
        Column(
            Modifier.weight(1f).fillMaxWidth().clickable(onClickLabel = stringResource(R.string.voice_destination), onClick = actions::voiceNav),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (listening) ListeningMic(48.dp) else QsIcon(Icons.NavArrow, Qs.Route, 48.dp)
            QsText(stringResource(if (listening) R.string.listening_ellipsis else R.string.where_to), 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            QsText(stringResource(if (listening) R.string.say_destination else R.string.tap_say_destination), 13.sp, Qs.Muted)
        }
    } else {
        // The next manoeuvre takes all the room the card has; the ETA line only claims space
        // when the nav app actually sends one.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 12.dp), contentAlignment = Alignment.CenterStart) {
            // The text needs the width more than the arrow does: nav apps put the distance and
            // the street in one title ("0 ft - toward Main St"), so it gets two lines.
            val arrow = minOf(maxHeight * 0.8f, maxWidth * 0.26f).coerceIn(56.dp, 140.dp)
            val distanceSize = (arrow.value * 0.3f).coerceIn(20f, 36f).sp
            val instructionSize = (arrow.value * 0.17f).coerceIn(14f, 24f).sp
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(arrow * 0.18f)) {
                Box(
                    Modifier.size(arrow).clip(RoundedCornerShape(arrow * 0.26f)).background(Qs.Route),
                    contentAlignment = Alignment.Center,
                ) {
                    if (nav.maneuver != null) Image(nav.maneuver.asImageBitmap(), null, Modifier.size(arrow * 0.7f))
                    else QsIcon(Icons.TurnRight, Color.White, arrow * 0.64f, strokeWidth = 2.6f)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    nav.title?.let {
                        Text(it, color = Qs.Text, fontSize = distanceSize, lineHeight = distanceSize * 1.1f, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    nav.text?.let {
                        Text(it, color = Qs.TextSoft, fontSize = instructionSize, lineHeight = instructionSize * 1.2f, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (nav.subText != null) {
            Box(Modifier.padding(vertical = 12.dp).fillMaxWidth().height(1.dp).background(Qs.Hairline))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QsIcon(Icons.Pin, Qs.Muted, 18.dp)
                QsText(nav.subText, 18.sp, weight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AssistCard(car: CarState, actions: DrawerActions, modifier: Modifier = Modifier) = QsCard(modifier) {
    CardLabel(stringResource(R.string.driver_assist))
    // Fills the card: the gap between the rows grows with it.
    Column(Modifier.weight(1f).fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.SpaceBetween) {
        AssistRow(Assist.AEB, car.aeb, Icons.Brake, stringResource(R.string.assist_aeb), actions)
        CollisionWarningRow(car.collisionWarning, actions)
        AssistRow(Assist.AUTO_HOLD, car.autoHold, Icons.AutoHold, stringResource(R.string.assist_auto_hold), actions)
        AssistRow(Assist.LANE, car.laneKeep, Icons.Lane, stringResource(R.string.assist_lane), actions)
    }
}

@StringRes
private fun AssistBlock.messageRes(): Int = when (this) {
    AssistBlock.IGNITION_OFF -> R.string.assist_block_ignition
    AssistBlock.TOO_FAST -> R.string.assist_block_speed
    AssistBlock.UNAVAILABLE -> R.string.assist_block_unavailable
}

/** Clears a row's "tap again" or "why not" notice after a moment. */
@Composable
private fun NoticeTimeout(active: Boolean, key: Any?, clear: () -> Unit) {
    LaunchedEffect(active, key) {
        if (active) {
            delay(ASSIST_NOTICE_MS)
            clear()
        }
    }
}

@Composable
private fun AssistRow(assist: Assist, on: Boolean?, icon: String, name: String, actions: DrawerActions) {
    val scope = rememberCoroutineScope()
    // Switching a safety function off takes a second tap, as the car's own page asks first.
    var armed by remember { mutableStateOf(false) }
    // Why the car's rules refused the last tap, shown in place of On / Off for a moment.
    var blocked by remember { mutableStateOf<AssistBlock?>(null) }
    NoticeTimeout(armed || blocked != null, blocked) { armed = false; blocked = null }
    val notice = if (armed) R.string.assist_confirm_off else blocked?.messageRes()
    val pill = when (on) {
        true -> stringResource(R.string.pill_on)
        false -> stringResource(R.string.pill_off)
        null -> "--"
    }
    AssistRowFrame(on, icon, name, pill, notice, enabled = on != null) {
        blocked = null
        if (on == true && assist.confirmOff && !armed) {
            armed = true
        } else {
            armed = false
            scope.launch { blocked = actions.car.toggleAssist(assist) }
        }
    }
}

/** Off, Near, Standard, Far: each tap moves to the next; a step the car's rules block is skipped. */
@Composable
private fun CollisionWarningRow(level: FcwLevel?, actions: DrawerActions) {
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf<AssistBlock?>(null) }
    NoticeTimeout(blocked != null, blocked) { blocked = null }
    val pill = when (level) {
        FcwLevel.OFF -> stringResource(R.string.pill_off)
        FcwLevel.NEAR -> stringResource(R.string.fcw_near)
        FcwLevel.STANDARD -> stringResource(R.string.fcw_standard)
        FcwLevel.FAR -> stringResource(R.string.fcw_far)
        null -> "--"
    }
    AssistRowFrame(
        level?.let { it != FcwLevel.OFF }, Icons.Collision, stringResource(R.string.assist_fcw), pill,
        blocked?.messageRes(), enabled = level != null,
    ) {
        scope.launch { blocked = actions.car.cycleCollisionWarning() }
    }
}

@Composable
private fun AssistRowFrame(
    on: Boolean?,
    icon: String,
    name: String,
    pill: String,
    @StringRes notice: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x38000000))
            .clickable(enabled = enabled, onClickLabel = name, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistRing(on, icon, 40.dp, 22.dp)
        QsText(name, 14.sp, if (on == true) Qs.Text else Qs.Muted, FontWeight.SemiBold, Modifier.weight(1f).padding(start = 12.dp))
        val (text, fg, bg) = when {
            notice != null -> Triple(stringResource(notice), Qs.EnergyLow, Qs.EnergyLow.copy(alpha = 0.16f))
            on == true -> Triple(pill, Qs.Energy, Qs.Energy.copy(alpha = 0.16f))
            else -> Triple(pill, Qs.Muted, Color(0x14FFFFFF))
        }
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 5.dp)) {
            QsText(text, 13.sp, fg, FontWeight.Bold)
        }
    }
}

@Composable
fun ClimateCard(car: CarState, actions: DrawerActions, modifier: Modifier = Modifier) = QsCard(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CardLabel(stringResource(R.string.climate_driver), Modifier.weight(1f))
        car.outsideTempC?.let { QsText(stringResource(R.string.outside_temp, it), 13.sp, Qs.Muted) }
        ClimateButton(ClimateSwitch.AC, car.acOn, Icons.AirConditioner, stringResource(R.string.climate_ac), "A/C", actions, compact = true)
    }
    // The sliders follow the car while untouched and send their value once, on release.
    var temp by remember { mutableStateOf<Int?>(null) }
    var fan by remember { mutableStateOf<Int?>(null) }
    val shownTemp = temp ?: car.cabinTempC
    val shownFan = fan ?: car.fanLevel
    Row(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            ClimateButton(ClimateSwitch.POWER, car.climateOn, Icons.Power, stringResource(R.string.climate_power), stringResource(R.string.climate_power_short), actions)
            ClimateButton(ClimateSwitch.AUTO, car.autoClimate, null, stringResource(R.string.climate_auto), "AUTO", actions)
        }
        SeatAirflow(
            shownTemp, shownFan, airOn = car.climateOn != false,
            description = stringResource(R.string.desc_climate, shownTemp?.toString() ?: "--", shownFan?.toString() ?: "--"),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            ClimateButton(ClimateSwitch.SYNC, car.sync, Icons.SyncZones, stringResource(R.string.climate_sync), stringResource(R.string.climate_sync_short), actions)
            ClimateButton(ClimateSwitch.RECIRCULATION, car.recirculation, Icons.Recirculate, stringResource(R.string.climate_recirculation), stringResource(R.string.climate_recirculation_short), actions)
        }
    }
    // The temperature fill walks the scale from the coldest setting, so its end shows the chosen one.
    val tempColors = shownTemp?.let { t -> (CabinTempSpec.MIN..t).map { Qs.tempColor(it) } }.orEmpty()
    HorizontalSlider(
        shownTemp, CabinTempSpec.MIN..CabinTempSpec.MAX, Icons.Thermometer,
        shownTemp?.let { "$it°" } ?: "--", stringResource(R.string.slider_temperature),
        Brush.horizontalGradient(if (tempColors.size > 1) tempColors else List(2) { Qs.tempColor(shownTemp) }),
        onChange = { temp = it },
        onFinished = { temp?.let(actions.car::setCabinTemp); temp = null },
    )
    Spacer(Modifier.height(8.dp))
    HorizontalSlider(
        shownFan, FanSpec.MIN..FanSpec.MAX, Icons.Wind,
        shownFan?.toString() ?: "--", stringResource(R.string.slider_fan),
        Brush.horizontalGradient(listOf(Qs.Route.copy(alpha = 0.55f), Qs.Route)),
        onChange = { fan = it },
        onFinished = { fan?.let(actions.car::setFan); fan = null },
    )
}

/** Fan 1..10 drawn as four airflow strengths: face, then chest, lap and legs join in. */
private val AIRFLOW_BANDS = intArrayOf(1, 1, 2, 2, 2, 3, 3, 3, 4, 4)

/** Where each band ends, as fractions of the seat image (face, chest, lap, legs), and how it bows. */
private val BAND_ENDS = listOf(Offset(0.79f, 0.09f), Offset(0.68f, 0.35f), Offset(0.45f, 0.50f), Offset(0.20f, 0.76f))
private val BAND_BOW = floatArrayOf(-0.16f, -0.04f, 0.06f, 0.20f)

/** Width over height of the seat image. */
private const val SEAT_ASPECT = 480f / 551f

/**
 * The seated occupant, with airflow from the vent on the left: its colour follows the
 * temperature, its strength the fan. The ribbons run in under the body, which is drawn on top.
 */
@Composable
private fun SeatAirflow(temp: Int?, fan: Int?, airOn: Boolean, description: String, modifier: Modifier = Modifier) {
    val level = fan?.takeIf { airOn && it in 1..AIRFLOW_BANDS.size }?.let { AIRFLOW_BANDS[it - 1] } ?: 0
    val color = Qs.tempColor(temp)
    // One 3.6 s cycle; level n moves the dashes n periods per cycle, so faster air never jumps at the wrap.
    val cycle by rememberInfiniteTransition(label = "airflow").animateFloat(
        0f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing)), label = "airflowPhase",
    )
    BoxWithConstraints(modifier.semantics { contentDescription = description }) {
        val seatHeight = maxHeight * 0.96f
        val seatWidth = seatHeight * SEAT_ASPECT
        if (level > 0) Canvas(Modifier.fillMaxSize()) {
            val seatLeft = size.width - seatWidth.toPx()
            val seatTop = size.height - seatHeight.toPx()
            val strands = 2 + level
            val width = (1.2f + 0.4f * level).dp.toPx()
            val dash = 35.dp.toPx()
            val gap = 25.dp.toPx()
            val phase = -cycle * level * (dash + gap)
            val start = Offset(0f, size.height * 0.5f)
            val brush = Brush.horizontalGradient(0f to color.copy(alpha = 0f), 0.2f to color, startX = 0f, endX = size.width)
            for (band in 0 until level) {
                val end = BAND_ENDS[band]
                val tip = Offset(seatLeft + end.x * seatWidth.toPx(), seatTop + end.y * seatHeight.toPx())
                for (k in 0 until strands) {
                    val dy = (k - (strands - 1) / 2f) * 3.dp.toPx()
                    val path = Path().apply {
                        moveTo(start.x, start.y + dy * 0.4f)
                        cubicTo(
                            size.width * 0.24f, start.y + BAND_BOW[band] * size.height + dy * 1.6f,
                            size.width * 0.43f, tip.y + dy,
                            tip.x, tip.y + dy * 1.2f,
                        )
                    }
                    val effect = PathEffect.dashPathEffect(floatArrayOf(dash, gap), phase + k * 0.3f * (dash + gap))
                    val alpha = 0.5f + 0.1f * (k % 3)
                    // A wide faint stroke under each strand stands in for a glow.
                    drawPath(path, brush, alpha = alpha * 0.18f, style = Stroke(width * 4, cap = StrokeCap.Round, pathEffect = effect))
                    drawPath(path, brush, alpha = alpha, style = Stroke(width, cap = StrokeCap.Round, pathEffect = effect))
                }
            }
        }
        Image(
            painterResource(R.drawable.climate_seat), contentDescription = null,
            modifier = Modifier.align(Alignment.BottomEnd).width(seatWidth).height(seatHeight),
        )
    }
}

/**
 * One of the climate panel's buttons: a symbol over a short [label] (or the label alone when
 * there is no [icon], as AUTO on the car's own panel), lit from the car's state with a dot in its
 * corner, flipped on tap. [compact] is a small pill for the card's header.
 */
@Composable
private fun ClimateButton(
    switch: ClimateSwitch, on: Boolean?, icon: String?, name: String, label: String,
    actions: DrawerActions, compact: Boolean = false,
) {
    val state = onOffLabel(on)
    val tint = when (on) { true -> Qs.Energy; false -> Qs.TextSoft; null -> Qs.Dim }
    val shape = RoundedCornerShape(if (compact) 16.dp else Qs.ButtonRadius)
    Box(
        Modifier.then(if (compact) Modifier.height(32.dp) else Modifier.size(68.dp, 58.dp))
            .clip(shape)
            .background(if (on == true) Qs.Energy.copy(alpha = 0.16f) else Qs.Button)
            .border(1.dp, if (on == true) Qs.Energy.copy(alpha = 0.45f) else Qs.Hairline, shape)
            .clickable(role = Role.Button, onClickLabel = name) { actions.car.toggleClimate(switch) }
            .semantics { contentDescription = "$name, $state" },
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                icon?.let { QsIcon(it, tint, 16.dp) }
                QsText(label, 13.sp, tint, FontWeight.Bold)
            }
        } else {
            Box(Modifier.align(Alignment.TopEnd).padding(7.dp).size(6.dp).clip(CircleShape).background(if (on == true) Qs.Energy else Qs.Hairline))
            if (icon == null) {
                QsText(label, 15.sp, tint, FontWeight.Bold)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    QsIcon(icon, tint, 22.dp)
                    QsText(label, 11.sp, tint)
                }
            }
        }
    }
}

@Composable
fun DriveEnergyCard(car: CarState, actions: DrawerActions, modifier: Modifier = Modifier) = QsCard(modifier) {
    // Two buttons, each a symbol of the current state; a tap moves to the next one.
    Row(Modifier.weight(3f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CycleButton(
            stringResource(R.string.battery_mode), car.batteryMode?.label() ?: "--", Modifier.weight(1f),
            onClick = { actions.car.setBatteryMode(nextOf(BatteryMode.entries, car.batteryMode)) },
        ) { size -> QsIcon(car.batteryMode.icon(), Qs.Mode, size, strokeWidth = 1.6f) }
        CycleButton(
            stringResource(R.string.regeneration), car.regen?.label() ?: "--", Modifier.weight(1f),
            onClick = { actions.car.setRegen(nextOf(RegenLevel.entries, car.regen)) },
        ) { size -> RegenDial(car.regen, size) }
    }
    // The hold target is always shown, so the card keeps one layout; it only works in Forced,
    // so anywhere else it is greyed out and disabled. Written once on release, not per step.
    val forced = car.batteryMode == BatteryMode.FORCED
    var dragging by remember { mutableStateOf(false) }
    var value by remember { mutableFloatStateOf((car.holdTarget ?: 50).toFloat()) }
    if (!dragging) value = (car.holdTarget ?: 50).toFloat()
    Spacer(Modifier.height(12.dp))
    // Same inset as the segmented controls above and below, so all three line up.
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            QsText(stringResource(R.string.hold_battery_at), 13.sp, if (forced) Qs.TextSoft else Qs.Dim, FontWeight.SemiBold)
            QsText("${value.toInt()}%", 15.sp, if (forced) Qs.Mode else Qs.Dim, FontWeight.Bold)
        }
        QsSlider(
            value = value,
            range = NewEnergyIds.SOC_TARGET_MIN.toFloat()..NewEnergyIds.SOC_TARGET_MAX.toFloat(),
            description = stringResource(R.string.hold_target_desc),
            onChange = { dragging = true; value = it },
            onFinished = { dragging = false; actions.car.setHoldTarget(value.roundToInt()) },
            accent = Qs.Mode,
            step = 5f,
            enabled = forced,
        )
    }
}

/** The state after [current] in [all], wrapping round; the first when nothing is known yet. */
private fun <T> nextOf(all: List<T>, current: T?): T = all[(all.indexOf(current) + 1) % all.size]

/** A big button that shows one state as a symbol and steps to the next on tap. */
@Composable
private fun CycleButton(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    symbol: @Composable (size: Dp) -> Unit,
) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Qs.Button)
            .clickable(onClickLabel = title, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$title, $value" }
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CardLabel(title)
        // The symbol is the point of the button: it takes all the room the two texts leave.
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            symbol(minOf(maxWidth, maxHeight) * 0.8f)
        }
        QsText(value, 13.sp, Qs.TextSoft, FontWeight.SemiBold)
    }
}

@Composable
fun RangeCard(car: CarState, modifier: Modifier = Modifier) = QsCard(modifier) {
    CardLabel(stringResource(R.string.range))
    // The same mirrored gauge as the semi-open tile, sized to the card.
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        // As large as the card allows, leaving the corners free for the four values.
        MirrorRangeLarge(
            car.batterySoc, car.electricRangeKm, car.fuelPercent, car.fuelRangeKm, car.chargeLimit,
            Qs.chargeColor(car.batterySoc), minOf(maxHeight, maxWidth * 0.78f),
        )
    }
}

/** A route-blue border that pulses while [active]: the button that is listening. */
@Composable
fun Modifier.listeningBorder(active: Boolean): Modifier {
    if (!active) return this
    val pulse by rememberInfiniteTransition(label = "listening").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "border",
    )
    return drawWithContent {
        drawContent()
        val w = 2.5.dp.toPx()
        val r = Qs.ButtonRadius.toPx()
        drawRoundRect(
            Qs.Route.copy(alpha = pulse), Offset(w / 2, w / 2), Size(size.width - w, size.height - w),
            CornerRadius(r, r), style = Stroke(w),
        )
    }
}

/** A mic on a route-blue disc with rings spreading out of it: voice input is listening. */
@Composable
private fun ListeningMic(dot: Dp) {
    val spread by rememberInfiniteTransition(label = "listening").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1_400, easing = LinearEasing)), label = "rings",
    )
    Box(Modifier.size(dot * 2), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val base = dot.toPx() / 2
            // Two rings half a cycle apart, each growing to twice the disc and fading out.
            for (i in 0..1) {
                val q = (spread + i * 0.5f) % 1f
                drawCircle(Qs.Route.copy(alpha = (1f - q) * 0.45f), radius = base * (1f + q))
            }
        }
        Box(Modifier.size(dot).clip(CircleShape).background(Qs.Route), contentAlignment = Alignment.Center) {
            QsIcon(Icons.Mic, Color.White, dot * 0.55f)
        }
    }
}
