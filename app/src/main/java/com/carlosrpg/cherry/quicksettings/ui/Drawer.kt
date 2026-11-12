package com.carlosrpg.cherry.quicksettings.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.jconfig.carbus.carinfo.BatteryMode
import app.jconfig.carbus.carinfo.CabinTempSpec
import app.jconfig.carbus.carinfo.FanSpec
import app.jconfig.carbus.carinfo.RegenLevel
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaRepository
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.nav.NavState
import com.carlosrpg.cherry.quicksettings.nav.NavStore
import com.carlosrpg.cherry.quicksettings.settings.QsConfig
import com.carlosrpg.cherry.quicksettings.settings.Side
import com.carlosrpg.cherry.quicksettings.settings.WidgetType
import com.carlosrpg.cherry.quicksettings.util.AppInfo
import com.carlosrpg.cherry.quicksettings.voice.VoiceInput
import kotlin.math.abs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sign

enum class DrawerMode { SEMI, OPEN }

/** Gap between the drawer and the screen edge; the overlay window itself starts at the edge. */
val DRAWER_EDGE_MARGIN = 16.dp

/** Everything the drawer can do; the overlay service supplies the navigation callbacks. */
class DrawerActions(
    private val context: Context,
    val car: CarRepository,
    val media: MediaRepository,
    val voice: VoiceInput,
    val toSemi: () -> Unit,
    val toOpen: () -> Unit,
    val close: () -> Unit,
    val openSettings: () -> Unit,
) {
    fun openMediaApp() {
        if (media.openApp()) close()
    }

    fun openNavApp() {
        if (NavStore.openApp(context)) close()
    }

    fun navAppLabel(): String? = NavStore.navPackage(context)?.let { AppInfo.label(context, it) }
    fun navAppIcon(): Bitmap? = NavStore.navPackage(context)?.let { AppInfo.icon(context, it) }

    /** Speech goes to the app that owns the media session; no recognizer = open that app. */
    fun voiceMedia() = voice.listen(onText = { media.playFromSearch(it) }, onUnavailable = ::openMediaApp)

    /** Speech becomes a destination search in the nav app; no recognizer = open the nav app. */
    fun voiceNav() = voice.listen(
        onText = { if (NavStore.searchDestination(context, it)) close() },
        onUnavailable = ::openNavApp,
    )
}

/** Calls [onTouch] for every touch anywhere inside, without consuming it (idle timer). */
fun Modifier.onAnyTouch(onTouch: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial)
            onTouch()
        }
    }
}

@Composable
fun DrawerRoot(
    mode: DrawerMode,
    config: QsConfig,
    actions: DrawerActions,
    portrait: Boolean,
    maxSemiHeight: Dp,
    closing: Boolean,
    onClosed: () -> Unit,
    onTouch: () -> Unit,
) {
    val car by actions.car.state.collectAsState()
    val media by actions.media.state.collectAsState()
    val nav by NavStore.state.collectAsState()
    val listening by actions.voice.listening.collectAsState()
    // Motion: each state slides in from the drawer's edge (semi → open included), and collapsing
    // slides it back into the edge before the window goes away.
    // A fresh animatable per mode starts at 0 in the very composition that switches mode, so the
    // first frame of the new state is already offscreen (no flash of the end position).
    val slide = remember(mode) { Animatable(0f) }
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(mode, closing) {
        if (closing) {
            slide.animateTo(0f, tween(durationMillis = 260, easing = FastOutLinearInEasing))
            closed()
        } else {
            slide.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow))
        }
    }
    val left = config.side == Side.LEFT
    Box(
        Modifier
            .graphicsLayer {
                val p = slide.value
                // Whole width including the edge margin: the drawer travels fully off screen.
                translationX = (1f - p) * size.width * (if (left) -1f else 1f)
                alpha = (0.4f + 0.6f * p).coerceIn(0f, 1f)
            }
            // The window is flush with the screen edge; the gap to the edge lives inside it.
            .padding(start = if (left) DRAWER_EDGE_MARGIN else 0.dp, end = if (left) 0.dp else DRAWER_EDGE_MARGIN)
            .onAnyTouch(onTouch)
    ) {
        when (mode) {
            DrawerMode.SEMI -> SemiDrawer(config, car, media, nav, actions, maxSemiHeight)
            DrawerMode.OPEN -> OpenDrawer(config, car, media, nav, actions, listening, portrait)
        }
    }
}

@Composable
private fun SemiDrawer(
    config: QsConfig,
    car: CarState,
    media: MediaState,
    nav: NavState?,
    actions: DrawerActions,
    maxHeight: Dp,
) {
    val density = LocalDensity.current
    var hud by remember { mutableStateOf<Hud?>(null) }
    // The guide sits beside the tile being dragged, as in the styleguide.
    val tileCenters = remember { HashMap<WidgetType, Float>() }
    var anchorY by remember { mutableFloatStateOf(0f) }
    var drawerTop by remember { mutableFloatStateOf(0f) }
    var drawerHeight by remember { mutableIntStateOf(0) }

    val drawer = @Composable {
        val shape = RoundedCornerShape(Qs.DrawerRadius)
        Column(
            Modifier.width(120.dp).heightIn(max = maxHeight).clip(shape).background(Qs.Glass)
                .border(1.dp, Qs.Hairline, shape)
                .onGloballyPositioned {
                    drawerTop = it.boundsInRoot().top
                    drawerHeight = it.size.height
                }
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Tiles scroll when they don't fit in the height cap; the buttons stay visible.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                config.semi.forEach { type ->
                    Box(Modifier.onGloballyPositioned { tileCenters[type] = it.boundsInRoot().center.y }) {
                        SemiTile(type, car, media, nav, actions) { next ->
                            if (next != null && hud == null) anchorY = tileCenters[type] ?: anchorY
                            hud = next
                        }
                    }
                }
            }
            Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(1.dp).background(Qs.Hairline))
            GlassButton(Icons.DoubleRight.mirrorFor(config.side), stringResource(R.string.action_open_fully), actions.toOpen, Modifier.width(96.dp), background = Color(0x24FFFFFF))
            GlassButton(Icons.Tune, stringResource(R.string.action_settings), actions.openSettings, Modifier.width(96.dp))
            GlassButton(Icons.ChevronLeft.mirrorFor(config.side), stringResource(R.string.action_close), actions.close, Modifier.width(96.dp))
        }
    }
    val guide = @Composable {
        val current = hud
        if (current != null && drawerHeight > 0) {
            val hudHeightPx = with(density) { HUD_HEIGHT.toPx() }
            val y = (anchorY - drawerTop - hudHeightPx / 2).coerceIn(0f, maxOf(0f, drawerHeight - hudHeightPx))
            Box(Modifier.height(with(density) { drawerHeight.toDp() })) {
                GuideHud(current, Modifier.offset { IntOffset(0, y.roundToInt()) })
            }
        }
    }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (config.side == Side.RIGHT) guide()
        drawer()
        if (config.side == Side.LEFT) guide()
    }
}

private val HUD_HEIGHT = 230.dp
private const val MEDIA_DEAD_DP = 16
private const val MEDIA_BAND_DP = 64
private const val MEDIA_SIDE_DP = 56

/** Icons are drawn for a left drawer; a right drawer points the other way. */
private fun String.mirrorFor(side: Side): String = if (side == Side.LEFT) this else when (this) {
    Icons.DoubleRight -> Icons.DoubleLeft
    Icons.ChevronLeft -> Icons.ChevronRight
    Icons.DoubleLeft -> Icons.DoubleRight
    else -> this
}

/** Per-gesture scratch state; survives the recompositions the guide's updates cause. */
private class GestureMemo {
    var steps = DragSteps(0f, 1f)
    var start = 0
    var target: Any? = null

    fun reset(steps: DragSteps, start: Int) {
        this.steps = steps
        this.start = start
        target = null
    }
}

@Composable
private fun SemiTile(
    type: WidgetType,
    car: CarState,
    media: MediaState,
    nav: NavState?,
    actions: DrawerActions,
    setHud: (Hud?) -> Unit,
) {
    val density = LocalDensity.current
    val lockPx = with(density) { 24.dp.toPx() }
    val choiceStepPx = with(density) { 64.dp.toPx() }
    val climateStepPx = with(density) { 20.dp.toPx() }
    // Media zones: ±16 dp dead band, slow volume to 64 dp, fast beyond; tracks from 56 dp sideways.
    val mediaDeadPx = with(density) { MEDIA_DEAD_DP.dp.toPx() }
    val mediaBandPx = with(density) { MEDIA_BAND_DP.dp.toPx() }
    val mediaSidePx = with(density) { MEDIA_SIDE_DP.dp.toPx() }
    when (type) {
        WidgetType.MEDIA -> {
            // Zone map around the press point: volume zones step the volume on entry and then
            // repeatedly (1/s, or 2/s in the outer zones) while held; track zones only skip when
            // the finger lifts inside them.
            var zone by remember { mutableStateOf<MediaZone?>(null) }
            fun publish(z: MediaZone?) {
                val now = actions.media.state.value
                setHud(z?.let { Hud.Media(it, now.volume, now.maxVolume, now.volumeAdjustable, now.volumeSimulated) })
            }
            LaunchedEffect(zone) {
                val z = zone ?: return@LaunchedEffect
                if (!z.isVolume || !actions.media.state.value.volumeAdjustable) return@LaunchedEffect
                while (true) {
                    actions.media.setVolume(actions.media.state.value.volume + z.direction)
                    publish(z)
                    delay(1000L / z.stepsPerSecond)
                }
            }
            MediaTile(media, Modifier.tapOrDrag(
                onTap = actions.media::playPause,
                onDragStart = { zone = MediaZone.NONE; publish(MediaZone.NONE) },
                onDrag = { total ->
                    val next = mediaZone(total, mediaDeadPx, mediaBandPx, mediaSidePx)
                    if (next != zone) {
                        zone = next
                        publish(next)
                    }
                },
                onRelease = {
                    when (zone) {
                        MediaZone.NEXT -> actions.media.next()
                        MediaZone.PREVIOUS -> actions.media.previous()
                        else -> Unit
                    }
                    zone = null
                    publish(null)
                },
                onCancel = { zone = null; publish(null) },
            ))
        }
        WidgetType.CAR_MODE -> {
            val g = remember { GestureMemo() }
            ModeTile(car, Modifier.tapOrDrag(
                onTap = actions.toOpen,
                onDragStart = { g.reset(DragSteps(lockPx, choiceStepPx), 0) },
                onDrag = { total ->
                    val s = g.steps.steps(total)
                    val now = actions.car.state.value
                    when (g.steps.axis) {
                        Axis.VERTICAL -> { // down = next mode in Initial → Smart → Forced
                            val start = (now.batteryMode ?: BatteryMode.INITIAL).ordinal
                            val mode = BatteryMode.entries[(start + s).coerceIn(0, BatteryMode.entries.lastIndex)]
                            g.target = mode
                            setHud(Hud.Mode(now.batteryMode, mode, now.holdTarget))
                        }
                        Axis.HORIZONTAL -> { // right = more regeneration
                            val start = (now.regen ?: RegenLevel.MEDIUM).ordinal
                            val regen = RegenLevel.entries[(start + s).coerceIn(0, RegenLevel.entries.lastIndex)]
                            g.target = regen
                            setHud(Hud.Regen(now.regen, regen))
                        }
                        null -> Unit
                    }
                },
                onRelease = {
                    val now = actions.car.state.value
                    when (val t = g.target) {
                        is BatteryMode -> if (t != now.batteryMode) actions.car.setBatteryMode(t)
                        is RegenLevel -> if (t != now.regen) actions.car.setRegen(t)
                    }
                    setHud(null)
                },
                onCancel = { setHud(null) },
            ))
        }
        WidgetType.CLIMATE -> {
            // Press and drag moves the marker: up/down = temperature, left/right = fan, both at
            // once like the quadrant itself. Written once, on release.
            var startTemp by remember { mutableIntStateOf(0) }
            var startFan by remember { mutableIntStateOf(0) }
            var target by remember { mutableStateOf<Hud.Climate?>(null) }
            ClimateTile(car, Modifier.tapOrDrag(
                onTap = actions.toOpen,
                onDragStart = {
                    val now = actions.car.state.value
                    startTemp = now.cabinTempC ?: CabinTempSpec.MIN
                    startFan = now.fanLevel ?: FanSpec.MIN
                    target = Hud.Climate(startTemp, startFan).also(setHud)
                },
                onDrag = { total ->
                    val next = Hud.Climate(
                        temp = (startTemp - (total.y / climateStepPx).roundToInt()).coerceIn(CabinTempSpec.MIN, CabinTempSpec.MAX),
                        fan = (startFan + (total.x / climateStepPx).roundToInt()).coerceIn(FanSpec.MIN, FanSpec.MAX),
                    )
                    if (next != target) {
                        target = next
                        setHud(next)
                    }
                },
                onRelease = {
                    target?.let { t ->
                        if (t.temp != startTemp) actions.car.setCabinTemp(t.temp)
                        if (t.fan != startFan) actions.car.setFan(t.fan)
                    }
                    target = null
                    setHud(null)
                },
                onCancel = { target = null; setHud(null) },
            ))
        }
        WidgetType.AUTONOMY -> AutonomyTile(car, Modifier.tap(actions.toOpen))
        WidgetType.NAVIGATION -> NavTile(nav, Modifier.tap { if (nav == null) actions.voiceNav() else actions.toOpen() })
        WidgetType.ASSIST -> AssistTile(car, Modifier.tap(actions.toOpen))
    }
}

@Composable
private fun GuideHud(hud: Hud, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.width(300.dp).height(HUD_HEIGHT).clip(shape).background(Qs.GlassOpen)
            .border(1.dp, Qs.Hairline, shape).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (hud) {
            is Hud.Climate -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ClimateQuadrant(hud.temp, hud.fan, QuadrantStyle.Guide, Modifier.clip(RoundedCornerShape(18.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QsIcon(Icons.Thermometer, Qs.tempColor(hud.temp), 18.dp)
                        QsText("${hud.temp}°", 22.sp, weight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        QsIcon(Icons.Wind, Qs.Text, 18.dp)
                        QsText("${hud.fan}", 22.sp, weight = FontWeight.Bold)
                    }
                }
            }
            is Hud.Media -> MediaGuide(hud)
            is Hud.Mode -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BatteryMode.entries.forEach { mode ->
                    val selected = mode == hud.target
                    val current = mode == hud.current
                    val rowShape = RoundedCornerShape(16.dp)
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).clip(rowShape)
                            .background(if (selected) Qs.Mode.copy(alpha = 0.2f) else Color.Transparent)
                            .border(1.dp, if (selected) Qs.Mode.copy(alpha = 0.5f) else if (current) Color(0x33FFFFFF) else Color.Transparent, rowShape)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val color = if (selected) Qs.Mode else Qs.Muted
                        QsIcon(mode.icon(), color, 24.dp)
                        QsText(mode.label(), 15.sp, color, FontWeight.SemiBold, Modifier.weight(1f))
                        if (current && !selected) QsText(stringResource(R.string.hud_current), 12.sp, Qs.Muted)
                        if (selected && mode == BatteryMode.FORCED) hud.holdTarget?.let { QsText(stringResource(R.string.hud_hold_at, it), 13.sp, color) }
                    }
                }
            }
            is Hud.Regen -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CardLabel(stringResource(R.string.regeneration))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    RegenLevel.entries.forEachIndexed { i, regen ->
                        val selected = regen == hud.target
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(34.dp + 24.dp * i).clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Qs.Energy else Qs.Energy.copy(alpha = 0.35f))
                            )
                            val name = regen.label()
                            val text = if (regen == hud.current && !selected) stringResource(R.string.hud_now, name) else name
                            QsText(text, 13.sp, if (selected) Qs.Energy else Qs.Muted, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The media control card, drawn as the zone map it responds to: fast volume up across the top,
 * fast volume down across the bottom, previous / next on the sides, and in the middle slow
 * volume up above the volume and slow volume down below it. The zone under the finger is lit.
 */
@Composable
private fun MediaGuide(hud: Hud.Media) {
    val z = hud.zone
    val gap = 6.dp
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
        ZoneCell(z == MediaZone.VOLUME_UP_FAST, hud.adjustable, Modifier.fillMaxWidth().height(34.dp)) {
            ZoneIcons(Icons.Plus, 2, z == MediaZone.VOLUME_UP_FAST, hud.adjustable)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
            ZoneCell(z == MediaZone.PREVIOUS, true, Modifier.width(58.dp).fillMaxHeight()) {
                QsIcon(Icons.Prev, if (z == MediaZone.PREVIOUS) Color(0xFF0B1018) else Qs.Text, 22.dp, filled = true)
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(gap)) {
                ZoneCell(z == MediaZone.VOLUME_UP, hud.adjustable, Modifier.fillMaxWidth().weight(1f)) {
                    ZoneIcons(Icons.Plus, 1, z == MediaZone.VOLUME_UP, hud.adjustable)
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hud.adjustable) QsText("${hud.volume}", 26.sp, weight = FontWeight.Bold)
                    else QsIcon(Icons.Volume, Qs.Dim, 22.dp)
                    val caption = when (z) {
                        MediaZone.NEXT -> stringResource(R.string.hud_release_next)
                        MediaZone.PREVIOUS -> stringResource(R.string.hud_release_previous)
                        else -> when {
                            !hud.adjustable -> stringResource(R.string.use_car_volume)
                            hud.simulated -> stringResource(R.string.hud_volume_simulated)
                            else -> stringResource(R.string.hud_media_volume)
                        }
                    }
                    androidx.compose.material3.Text(
                        caption, color = if (z.isTrack) Qs.Text else Qs.Muted, fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                ZoneCell(z == MediaZone.VOLUME_DOWN, hud.adjustable, Modifier.fillMaxWidth().weight(1f)) {
                    ZoneIcons(Icons.Minus, 1, z == MediaZone.VOLUME_DOWN, hud.adjustable)
                }
            }
            ZoneCell(z == MediaZone.NEXT, true, Modifier.width(58.dp).fillMaxHeight()) {
                QsIcon(Icons.Next, if (z == MediaZone.NEXT) Color(0xFF0B1018) else Qs.Text, 22.dp, filled = true)
            }
        }
        ZoneCell(z == MediaZone.VOLUME_DOWN_FAST, hud.adjustable, Modifier.fillMaxWidth().height(34.dp)) {
            ZoneIcons(Icons.Minus, 2, z == MediaZone.VOLUME_DOWN_FAST, hud.adjustable)
        }
    }
}

@Composable
private fun ZoneCell(active: Boolean, enabled: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.clip(shape)
            .background(if (active) Color.White else Color(0x0FFFFFFF))
            .border(1.dp, if (active) Color.White else Qs.Hairline, shape)
            .alpha(if (enabled) 1f else 0.4f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** [count] copies of a +/− glyph: one for the slow zone, two for the fast one. */
@Composable
private fun ZoneIcons(icon: String, count: Int, active: Boolean, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
        repeat(count) {
            QsIcon(icon, if (active) Color(0xFF0B1018) else if (enabled) Qs.Text else Qs.Dim, 18.dp, strokeWidth = 2.4f)
        }
    }
}

/** Wide cards span two columns. */
fun WidgetType.span(): Int = if (this == WidgetType.MEDIA || this == WidgetType.AUTONOMY) 2 else 1

/** Packs widgets into rows of [columns] in order; a widget that doesn't fit starts a new row. */
fun packRows(items: List<WidgetType>, columns: Int): List<List<WidgetType>> {
    val rows = mutableListOf<MutableList<WidgetType>>()
    var used = columns
    items.forEach { item ->
        val span = item.span().coerceAtMost(columns)
        if (used + span > columns) {
            rows += mutableListOf<WidgetType>()
            used = 0
        }
        rows.last() += item
        used += span
    }
    return rows
}

@Composable
private fun OpenDrawer(
    config: QsConfig,
    car: CarState,
    media: MediaState,
    nav: NavState?,
    actions: DrawerActions,
    listening: Boolean,
    portrait: Boolean,
) {
    val shape = RoundedCornerShape(Qs.DrawerRadius)
    val columns = if (portrait) 2 else 4
    Column(
        Modifier.fillMaxSize().clip(shape).background(Qs.GlassOpen).border(1.dp, Qs.Hairline, shape).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            CardLabel(stringResource(R.string.quick_panel), Modifier.weight(1f))
            GlassButton(Icons.DoubleLeft.mirrorFor(config.side), stringResource(R.string.action_back_semi), actions.toSemi)
            Spacer(Modifier.size(10.dp))
            GlassButton(Icons.Close, stringResource(R.string.action_close), actions.close)
            Spacer(Modifier.size(10.dp))
            GlassButton(Icons.Tune, stringResource(R.string.action_settings), actions.openSettings)
        }
        packRows(config.open, columns).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { type ->
                    val m = Modifier.weight(type.span().coerceAtMost(columns).toFloat()).fillMaxHeight()
                    when (type) {
                        WidgetType.MEDIA -> MediaCard(media, actions, listening, m)
                        WidgetType.NAVIGATION -> NavCard(nav, actions, listening, m)
                        WidgetType.ASSIST -> AssistCard(car, m)
                        WidgetType.CLIMATE -> ClimateCard(car, actions, m)
                        WidgetType.CAR_MODE -> DriveEnergyCard(car, actions, m)
                        WidgetType.AUTONOMY -> RangeCard(car, m)
                    }
                }
                val used = row.sumOf { it.span().coerceAtMost(columns) }
                if (used < columns) Spacer(Modifier.weight((columns - used).toFloat()))
            }
        }
    }
}
