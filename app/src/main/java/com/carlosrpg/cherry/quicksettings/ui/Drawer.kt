package com.carlosrpg.cherry.quicksettings.ui

import android.content.Context
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carlosrpg.cherry.quicksettings.car.CarItems.BatteryMode
import com.carlosrpg.cherry.quicksettings.car.CarItems.Regen
import com.carlosrpg.cherry.quicksettings.car.CarRepository
import com.carlosrpg.cherry.quicksettings.car.CarState
import com.carlosrpg.cherry.quicksettings.media.MediaRepository
import com.carlosrpg.cherry.quicksettings.media.MediaState
import com.carlosrpg.cherry.quicksettings.nav.NavStore
import com.carlosrpg.cherry.quicksettings.settings.QsConfig
import com.carlosrpg.cherry.quicksettings.settings.Side
import com.carlosrpg.cherry.quicksettings.settings.WidgetType
import com.carlosrpg.cherry.quicksettings.voice.VoiceInput
import kotlin.math.abs
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

    fun navAppLabel(): String? = NavStore.navPackage(context)?.let { pkg ->
        runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

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
    Box(Modifier.onAnyTouch(onTouch)) {
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
    nav: com.carlosrpg.cherry.quicksettings.nav.NavState?,
    actions: DrawerActions,
    maxHeight: Dp,
) {
    var hud by remember { mutableStateOf<Hud?>(null) }
    val drawer = @Composable {
        val shape = RoundedCornerShape(Qs.DrawerRadius)
        Column(
            Modifier.width(120.dp).heightIn(max = maxHeight).clip(shape).background(Qs.Glass)
                .border(1.dp, Qs.Hairline, shape).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Tiles scroll when they don't fit in the height cap; the buttons stay visible.
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                config.semi.forEach { type ->
                    SemiTile(type, car, media, nav, actions) { hud = it }
                }
            }
            Box(Modifier.padding(horizontal = 6.dp).fillMaxWidth().height(1.dp).background(Qs.Hairline))
            GlassButton(Icons.DoubleRight.mirrorFor(config.side), "Open drawer fully", actions.toOpen, Modifier.width(96.dp), background = Color(0x24FFFFFF))
            GlassButton(Icons.Tune, "Settings", actions.openSettings, Modifier.width(96.dp))
            GlassButton(Icons.ChevronLeft.mirrorFor(config.side), "Close drawer", actions.close, Modifier.width(96.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (config.side == Side.RIGHT) hud?.let { GuideHud(it) }
        drawer()
        if (config.side == Side.LEFT) hud?.let { GuideHud(it) }
    }
}

/** Icons are drawn for a left drawer; a right drawer points the other way. */
private fun String.mirrorFor(side: Side): String = if (side == Side.LEFT) this else when (this) {
    Icons.DoubleRight -> Icons.DoubleLeft
    Icons.ChevronLeft -> Icons.ChevronRight
    Icons.DoubleLeft -> Icons.DoubleRight
    else -> this
}

@Composable
private fun SemiTile(
    type: WidgetType,
    car: CarState,
    media: MediaState,
    nav: com.carlosrpg.cherry.quicksettings.nav.NavState?,
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
            MediaTile(media, Modifier.tapOrHoldDrag(
                onTap = actions.media::playPause,
                onHoldStart = {
                    val now = actions.media.state.value
                    g.reset(DragSteps(lockPx, volumeStepPx), now.volume)
                    setHud(Hud.Volume(now.volume, now.maxVolume))
                },
                onDrag = { total ->
                    val s = g.steps.steps(total)
                    when (g.steps.axis) {
                        Axis.VERTICAL -> {
                            val now = actions.media.state.value
                            val v = (g.start - s).coerceIn(0, now.maxVolume) // up = louder
                            if (v != now.volume) actions.media.setVolume(v)
                            setHud(Hud.Volume(v, now.maxVolume))
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
            ModeTile(car, Modifier.tapOrHoldDrag(
                onTap = actions.toOpen,
                onHoldStart = {
                    val now = actions.car.state.value
                    g.reset(DragSteps(lockPx, choiceStepPx), 0)
                    setHud(Hud.Mode(now.batteryMode, now.batteryMode ?: BatteryMode.INITIAL, now.holdTarget))
                },
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
                            val start = (now.regen ?: Regen.MEDIUM).ordinal
                            val regen = Regen.entries[(start + s).coerceIn(0, Regen.entries.lastIndex)]
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
                        is Regen -> if (t != now.regen) actions.car.setRegen(t)
                    }
                    setHud(null)
                },
                onCancel = { setHud(null) },
            ))
        }
        WidgetType.CLIMATE -> ClimateTile(car, Modifier.tapOrHoldDrag(onTap = actions.toOpen))
        WidgetType.AUTONOMY -> AutonomyTile(car, Modifier.tapOrHoldDrag(onTap = actions.toOpen))
        WidgetType.NAVIGATION -> NavTile(nav, Modifier.tapOrHoldDrag(onTap = { if (nav == null) actions.voiceNav() else actions.toOpen() }))
        WidgetType.ASSIST -> AssistTile(car, Modifier.tapOrHoldDrag(onTap = actions.toOpen))
    }
}

@Composable
private fun GuideHud(hud: Hud) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.width(300.dp).height(230.dp).clip(shape).background(Qs.GlassOpen)
            .border(1.dp, Qs.Hairline, shape).padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (hud) {
            is Hud.Volume -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val fraction = if (hud.max > 0) hud.value / hud.max.toFloat() else 0f
                Box(Modifier.width(28.dp).fillMaxHeight().clip(RoundedCornerShape(14.dp)).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(fraction).background(Qs.Text))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    QsIcon(Icons.ChevronUp, Qs.Text, 28.dp)
                    QsText("${hud.value}", 64.sp, weight = FontWeight.Bold)
                    QsText("media volume", 13.sp, Qs.Muted)
                    QsIcon(Icons.ChevronDown, Qs.Dim, 28.dp)
                }
            }
            is Hud.Track -> Column(verticalArrangement = Arrangement.spacedBy(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TrackBubble(Icons.Prev, hud.armed && hud.direction < 0)
                    QsText(if (hud.direction < 0) "Previous track" else "Next track", 15.sp, weight = FontWeight.SemiBold)
                    TrackBubble(Icons.Next, hud.armed && hud.direction > 0)
                }
                QsText(if (hud.armed) "Lift to skip · drag back to cancel" else "Keep dragging", 13.sp, Qs.Muted)
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
                        if (current && !selected) QsText("current", 12.sp, Qs.Muted)
                        if (selected && mode == BatteryMode.FORCED) hud.holdTarget?.let { QsText("hold $it%", 13.sp, color) }
                    }
                }
            }
            is Hud.Regen -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CardLabel("Regeneration")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                    Regen.entries.forEachIndexed { i, regen ->
                        val selected = regen == hud.target
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.fillMaxWidth().height(34.dp + 24.dp * i).clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) Qs.Energy else Qs.Energy.copy(alpha = 0.35f))
                            )
                            val suffix = if (regen == hud.current && !selected) " · now" else ""
                            QsText(regen.label() + suffix, 13.sp, if (selected) Qs.Energy else Qs.Muted, FontWeight.SemiBold, Modifier.padding(top = 8.dp))
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
    nav: com.carlosrpg.cherry.quicksettings.nav.NavState?,
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
            CardLabel("Quick panel", Modifier.weight(1f))
            GlassButton(Icons.DoubleLeft.mirrorFor(config.side), "Back to semi-open", actions.toSemi)
            Spacer(Modifier.size(10.dp))
            GlassButton(Icons.Close, "Close drawer", actions.close)
            Spacer(Modifier.size(10.dp))
            GlassButton(Icons.Tune, "Settings", actions.openSettings)
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
