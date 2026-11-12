package com.carlosrpg.cherry.quicksettings.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.roundToInt
import kotlin.math.sign

enum class DrawerMode { SEMI, OPEN }

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
    onTouch: () -> Unit,
) {
    val car by actions.car.state.collectAsState()
    val media by actions.media.state.collectAsState()
    val nav by NavStore.state.collectAsState()
    val listening by actions.voice.listening.collectAsState()
    // Styleguide motion: a short spring in from the edge each time the state changes.
    val appear = remember(mode) { Animatable(0f) }
    LaunchedEffect(mode) {
        appear.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow))
    }
    val slidePx = with(LocalDensity.current) { 40.dp.toPx() }
    val fromSide = if (config.side == Side.LEFT) -1f else 1f
    Box(
        Modifier
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationX = (1f - appear.value) * slidePx * fromSide
            }
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
    val volumeStepPx = with(density) { 16.dp.toPx() }
    val choiceStepPx = with(density) { 64.dp.toPx() }
    when (type) {
        WidgetType.MEDIA -> {
            val g = remember { GestureMemo() }
            MediaTile(media, Modifier.tapOrDrag(
                onTap = actions.media::playPause,
                onDragStart = { g.reset(DragSteps(lockPx, volumeStepPx), actions.media.state.value.volume) },
                onDrag = { total ->
                    val s = g.steps.steps(total)
                    when (g.steps.axis) {
                        Axis.VERTICAL -> {
                            val now = actions.media.state.value
                            if (!now.volumeAdjustable) {
                                setHud(Hud.VolumeUnavailable)
                            } else {
                                val v = (g.start - s).coerceIn(0, now.maxVolume) // up = louder
                                if (v != now.volume) actions.media.setVolume(v)
                                setHud(Hud.Volume(v, now.maxVolume))
                            }
                        }
                        Axis.HORIZONTAL -> {
                            val direction = total.x.sign.toInt()
                            g.target = if (abs(total.x) >= choiceStepPx) direction else 0
                            setHud(Hud.Track(direction, g.target != 0))
                        }
                        null -> Unit
                    }
                },
                onRelease = {
                    if (g.steps.axis == Axis.HORIZONTAL) when (g.target) {
                        1 -> actions.media.next()
                        -1 -> actions.media.previous()
                    }
                    setHud(null)
                },
                onCancel = { setHud(null) },
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
        WidgetType.CLIMATE -> ClimateTile(car, Modifier.tap(actions.toOpen))
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
            Hud.VolumeUnavailable -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QsIcon(Icons.Volume, Qs.Dim, 40.dp)
                QsText(stringResource(R.string.hud_volume_unavailable), 16.sp, weight = FontWeight.SemiBold)
                QsText(stringResource(R.string.hud_volume_use_car), 13.sp, Qs.Muted)
            }
            is Hud.Volume -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val fraction = if (hud.max > 0) hud.value / hud.max.toFloat() else 0f
                Box(Modifier.width(28.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(Qs.Text))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    QsIcon(Icons.ChevronUp, Qs.Text, 28.dp)
                    QsText("${hud.value}", 64.sp, weight = FontWeight.Bold)
                    QsText(stringResource(R.string.hud_media_volume), 13.sp, Qs.Muted)
                    QsIcon(Icons.ChevronDown, Qs.Dim, 28.dp)
                }
            }
            is Hud.Track -> Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TrackBubble(Icons.Prev, hud.armed && hud.direction < 0)
                    QsText(stringResource(if (hud.direction < 0) R.string.previous_track else R.string.next_track), 15.sp, weight = FontWeight.SemiBold)
                    TrackBubble(Icons.Next, hud.armed && hud.direction > 0)
                }
                QsText(stringResource(if (hud.armed) R.string.hud_lift_to_skip else R.string.hud_keep_dragging), 13.sp, Qs.Muted)
            }
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

@Composable
private fun TrackBubble(icon: String, active: Boolean) {
    val size = if (active) 72.dp else 56.dp
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size / 2))
            .background(if (active) Color.White else Color.Transparent)
            .border(1.dp, Qs.Hairline, RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center,
    ) {
        QsIcon(icon, if (active) Color(0xFF0B1018) else Qs.Dim, 24.dp, filled = true)
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
