package com.carlosrpg.cherry.quicksettings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.FanSpec
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.VolumeChannel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Width of one side slider in the open drawer. */
private val SliderWidth = 76.dp
private val SliderGap = 12.dp

/**
 * The styleguide's slider, standing up: a tall rounded track filled from the bottom in [fill],
 * with [icon] near its foot saying what it sets and the value above it. Touch anywhere to jump,
 * drag to adjust; [onChange] fires while moving (whole steps of [range]), [onFinished] on release.
 */
@Composable
fun VerticalSlider(
    value: Int?,
    range: IntRange,
    icon: String,
    label: String,
    description: String,
    fill: Brush,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val change by rememberUpdatedState(onChange)
    val finished by rememberUpdatedState(onFinished)
    val span = (range.last - range.first).coerceAtLeast(1)
    val fraction = value?.let { ((it - range.first).toFloat() / span).coerceIn(0f, 1f) } ?: 0f
    val shape = RoundedCornerShape(26.dp)
    Column(modifier.width(SliderWidth), horizontalAlignment = Alignment.CenterHorizontally) {
        QsText(label, 18.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().clip(shape).background(Qs.Button).border(1.dp, Qs.Hairline, shape)
                .semantics {
                    contentDescription = description
                    if (value != null) {
                        progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), range.first.toFloat()..range.last.toFloat())
                        setProgress { change(it.roundToInt().coerceIn(range)); finished(); true }
                    }
                }
                .pointerInput(range) {
                    fun at(y: Float) {
                        val f = 1f - (y / size.height).coerceIn(0f, 1f)
                        change((range.first + f * span).roundToInt().coerceIn(range))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        at(down.position.y)
                        while (true) {
                            val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            c.consume()
                            if (!c.pressed) break
                            at(c.position.y)
                        }
                        finished()
                    }
                },
        ) {
            val filled = maxHeight * fraction
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(filled).background(fill))
            // A short grip on the fill's edge, so the level reads even when the fill is low.
            if (value != null) {
                Box(
                    Modifier.align(Alignment.BottomCenter).offset(y = -(filled - 10.dp).coerceAtLeast(6.dp))
                        .width(28.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White)
                )
            }
            QsIcon(icon, Color.White, 26.dp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
        }
    }
}

/**
 * The driver's side of the open drawer: temperature over fan. Both follow the car while
 * untouched and send the new value once, on release (the car is then stepped to it).
 */
@Composable
fun ClimateSliders(car: CarState, actions: DrawerActions, modifier: Modifier = Modifier) {
    var temp by remember { mutableStateOf<Int?>(null) }
    var fan by remember { mutableStateOf<Int?>(null) }
    val shownTemp = temp ?: car.cabinTempC
    val shownFan = fan ?: car.fanLevel
    Column(modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        VerticalSlider(
            shownTemp, CabinTempSpec.MIN..CabinTempSpec.MAX, Icons.Thermometer,
            shownTemp?.let { "$it°" } ?: "--", stringResource(R.string.slider_temperature),
            Brush.verticalGradient(listOf(Qs.tempColor(shownTemp), Qs.tempColor(shownTemp).copy(alpha = 0.55f))),
            onChange = { temp = it },
            onFinished = { temp?.let(actions.car::setCabinTemp); temp = null },
            modifier = Modifier.weight(1f),
        )
        VerticalSlider(
            shownFan, FanSpec.MIN..FanSpec.MAX, Icons.Wind,
            shownFan?.toString() ?: "--", stringResource(R.string.slider_fan),
            Brush.verticalGradient(listOf(Qs.Route, Qs.Route.copy(alpha = 0.55f))),
            onChange = { fan = it },
            onFinished = { fan?.let(actions.car::setFan); fan = null },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The passenger side of the open drawer: media volume, and behind a button the navigation
 * voice. Both step the car's level to the slider once it holds still (debounced, one step per
 * command) and re-read it every second while untouched.
 */
@Composable
fun VolumeSliders(actions: DrawerActions, modifier: Modifier = Modifier) {
    var navOpen by remember { mutableStateOf(false) }
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(SliderGap)) {
            AnimatedVisibility(navOpen) {
                ChannelSlider(VolumeChannel.NAVIGATION, Icons.Voice, stringResource(R.string.slider_nav_voice), Qs.Route, actions)
            }
            ChannelSlider(VolumeChannel.MEDIA, Icons.Volume, stringResource(R.string.slider_volume), Qs.Mode, actions)
        }
        // Spans whatever sliders are shown, so it widens along with the voice slider.
        val buttonWidth by animateDpAsState(if (navOpen) SliderWidth * 2 + SliderGap else SliderWidth, label = "voiceButton")
        GlassButton(
            Icons.Voice,
            stringResource(if (navOpen) R.string.slider_nav_voice_hide else R.string.slider_nav_voice_show),
            { navOpen = !navOpen },
            tint = if (navOpen) Qs.Route else Qs.TextSoft,
            background = if (navOpen) Qs.Route.copy(alpha = 0.16f) else Qs.Button,
            modifier = Modifier.width(buttonWidth),
            iconSize = 30.dp,
        )
    }
}

@Composable
private fun ChannelSlider(channel: VolumeChannel, icon: String, description: String, accent: Color, actions: DrawerActions) {
    var level by remember { mutableStateOf(actions.media.level(channel)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(channel) {
        while (true) {
            delay(VOLUME_POLL_MS)
            if (!dragging && !actions.media.moving(channel)) level = actions.media.level(channel)
        }
    }
    VerticalSlider(
        level.value, 0..level.max.coerceAtLeast(1), icon, level.value.toString(), description,
        Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.55f))),
        onChange = {
            dragging = true
            if (it != level.value) {
                level = level.copy(value = it)
                actions.media.moveLevel(channel, it)
            }
        },
        onFinished = { dragging = false },
        modifier = Modifier.fillMaxHeight(),
    )
}

private const val VOLUME_POLL_MS = 1_000L
