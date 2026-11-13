package com.carlosrpg.cherry.quicksettings.settings

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.carlosrpg.cherry.quicksettings.BuildConfig
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.app
import com.carlosrpg.cherry.quicksettings.nav.DebugLog
import com.carlosrpg.cherry.quicksettings.nav.NavStore
import com.carlosrpg.cherry.quicksettings.nav.QsNotificationListener
import com.carlosrpg.cherry.quicksettings.overlay.OverlayService
import com.carlosrpg.cherry.quicksettings.overlay.TapRelayService
import com.carlosrpg.cherry.quicksettings.ui.ArtPlaceholder
import com.carlosrpg.cherry.quicksettings.ui.CardLabel
import com.carlosrpg.cherry.quicksettings.ui.GlassButton
import com.carlosrpg.cherry.quicksettings.ui.Icons
import com.carlosrpg.cherry.quicksettings.ui.Qs
import com.carlosrpg.cherry.quicksettings.ui.QsIcon
import com.carlosrpg.cherry.quicksettings.ui.QsSlider
import com.carlosrpg.cherry.quicksettings.ui.QsText
import com.carlosrpg.cherry.quicksettings.ui.Segmented
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsScreen() }
    }

    override fun onResume() {
        super.onResume()
        OverlayService.start(this)
    }

    @Composable
    private fun SettingsScreen() {
        val store = app.settings
        val config by store.config.collectAsState()
        var refresh by remember { mutableIntStateOf(0) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        // Grants also arrive over adb while this screen stays open, so re-check on a timer too.
        LaunchedEffect(Unit) {
            var couldDraw = OverlayService.canDraw(this@SettingsActivity)
            while (true) {
                delay(STATUS_REFRESH_MS)
                app.media.refresh()
                refresh++
                // Start the drawer the moment the overlay grant lands.
                val canDraw = OverlayService.canDraw(this@SettingsActivity)
                if (canDraw && !couldDraw) OverlayService.start(this@SettingsActivity)
                couldDraw = canDraw
            }
        }
        val wide = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0B1018)).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassButton(Icons.ChevronLeft, stringResource(R.string.back), ::finish)
                QsText(stringResource(R.string.app_name), 26.sp, weight = FontWeight.Bold)
            }
            // Re-checked on every resume and every tick: grants happen in system settings or over adb.
            key(refresh) { Permissions(config.tapToOpen) }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Box(Modifier.weight(0.42f)) { PositionPanel(config) }
                    Box(Modifier.weight(0.58f)) { WidgetsPanel(config) }
                }
            } else {
                PositionPanel(config)
                WidgetsPanel(config)
            }
            key(refresh) { Status() }
        }
    }

    @Composable
    private fun PositionPanel(config: QsConfig) = Panel {
        val store = app.settings
        CardLabel(stringResource(R.string.drawer_position))
        var position by remember(config.edgePosition) { mutableFloatStateOf(config.edgePosition) }
        ScreenPreview(config.side, position)
        QsText(stringResource(R.string.side), 14.sp, Qs.TextSoft, FontWeight.SemiBold)
        Segmented(
            Side.entries, config.side,
            { stringResource(if (it == Side.LEFT) R.string.side_left else R.string.side_right) },
            Qs.Text, { side -> store.update { it.copy(side = side) } },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            QsText(stringResource(R.string.position_on_edge), 14.sp, Qs.TextSoft, FontWeight.SemiBold)
            QsText(stringResource(positionLabel(position)), 14.sp, Qs.Muted)
        }
        val sliderDescription = stringResource(R.string.position_on_edge)
        QsSlider(
            value = position,
            range = 0f..1f,
            description = sliderDescription,
            onChange = { position = it },
            onFinished = { store.update { it.copy(edgePosition = position) } },
        )
        val tapLabel = stringResource(R.string.tap_to_open)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                QsText(tapLabel, 16.sp, weight = FontWeight.SemiBold)
                Text(stringResource(R.string.tap_to_open_hint), color = Qs.Muted, fontSize = 13.sp)
            }
            Toggle(config.tapToOpen, tapLabel) { store.update { it.copy(tapToOpen = !it.tapToOpen) } }
        }
    }

    /** The screen in miniature with the drawer where it will sit, as in the styleguide. */
    @Composable
    private fun ColumnScope.ScreenPreview(side: Side, position: Float) {
        val screen = LocalConfiguration.current
        val ratio = screen.screenWidthDp.toFloat() / screen.screenHeightDp.coerceAtLeast(1)
        BoxWithConstraints(
            Modifier.height(200.dp).aspectRatio(ratio).align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D141E))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(16.dp))
        ) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(6.dp).background(Color(0xFF1A2431)))
            Box(Modifier.align(Alignment.Center).offset(x = maxWidth * 0.1f).width(6.dp).fillMaxHeight().background(Color(0xFF1A2431)))
            val drawerH = maxHeight * 0.55f
            val drawerW = maxOf(maxWidth * 0.09f, 26.dp)
            val y = (maxHeight - drawerH) * position.coerceIn(0f, 1f)
            Column(
                Modifier.align(if (side == Side.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset(x = if (side == Side.LEFT) 6.dp else (-6).dp, y = y)
                    .width(drawerW).height(drawerH).clip(RoundedCornerShape(10.dp))
                    .background(Qs.Route.copy(alpha = 0.22f)).border(2.dp, Qs.Route, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(5) { Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)).background(Color(0x59FFFFFF))) }
            }
        }
    }

    @Composable
    private fun WidgetsPanel(config: QsConfig) = Panel {
        val store = app.settings
        var tab by remember { mutableIntStateOf(0) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(stringResource(R.string.widgets), Modifier.weight(1f))
            Segmented(
                listOf(0, 1), tab, { stringResource(if (it == 0) R.string.state_semi else R.string.state_open) },
                Qs.Text, { tab = it }, Modifier.width(340.dp),
            )
        }
        QsText(stringResource(R.string.widgets_hint), 13.sp, Qs.Muted)
        val list = if (tab == 0) config.semi else config.open
        key(tab) {
            WidgetList(list) { updated -> store.update { if (tab == 0) it.copy(semi = updated) else it.copy(open = updated) } }
        }
    }

    @StringRes
    private fun positionLabel(p: Float) = when {
        p < 0.33f -> R.string.position_top
        p > 0.67f -> R.string.position_bottom
        else -> R.string.position_middle
    }

    @Composable
    private fun Permissions(tapToOpen: Boolean) {
        val overlay = OverlayService.canDraw(this)
        val notifications = hasNotificationAccess()
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        // The relay only matters in swipe-only mode; with tap to open a touch opens the drawer.
        val tapRelay = tapToOpen || TapRelayService.instance != null
        // Nothing to ask for: keep the screen about the drawer.
        if (overlay && notifications && mic && tapRelay) return
        Panel {
            CardLabel(stringResource(R.string.permissions))
            // The car has no settings page for the overlay, notification access or accessibility,
            // so those rows carry their adb command. The microphone is the normal prompt.
            PermissionRow(R.string.perm_overlay, R.string.perm_overlay_why, overlay, ADB_OVERLAY) {
                open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
            PermissionRow(R.string.perm_notif, R.string.perm_notif_why, notifications, ADB_NOTIFICATIONS) {
                open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            PermissionRow(R.string.perm_mic, R.string.perm_mic_why, mic, null) {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            if (!tapToOpen) PermissionRow(R.string.perm_tap, R.string.perm_tap_why, tapRelay, ADB_TAP_RELAY) {
                open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    /**
     * Which links are up, live. Nothing here can be tried off the car, so after a test on the
     * car this panel says which part failed.
     */
    @Composable
    private fun Status() = Panel {
        CardLabel(stringResource(R.string.status))
        val connected = stringResource(R.string.status_connected)
        val notConnected = stringResource(R.string.status_not_connected)
        val simulated = stringResource(R.string.status_simulated)
        val carOk = app.car.isConnected()
        StatusRow(R.string.status_car, carOk, if (BuildConfig.USE_MOCK) simulated else if (carOk) connected else notConnected)
        val mediaOk = app.media.carMediaConnected
        StatusRow(
            R.string.status_car_media, mediaOk,
            when {
                BuildConfig.USE_MOCK -> simulated
                !mediaOk -> notConnected
                else -> app.media.carSourceLabel ?: stringResource(R.string.status_no_source)
            },
        )
        val sessions = app.media.state.collectAsState().value.hasAccess
        StatusRow(
            R.string.status_media_apps, sessions,
            stringResource(if (sessions) R.string.status_access_granted else R.string.status_no_access),
        )
        val route = NavStore.state.collectAsState().value
        StatusRow(
            R.string.status_navigation, route != null,
            route?.appLabel ?: stringResource(R.string.status_no_route),
        )
        // What the nav app last posted: the clue when its directions don't show.
        NavStore.lastSeen?.let { Text(it, color = Qs.Muted, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QsText(stringResource(R.string.status_version, BuildConfig.VERSION_NAME), 12.sp, Qs.Muted, modifier = Modifier.weight(1f))
            GlassButton(null, stringResource(R.string.debug_log), { showDebug = !showDebug }, label = stringResource(R.string.debug_log))
        }
        if (showDebug) DebugText()
    }

    private var showDebug by mutableStateOf(false)

    /**
     * Everything needed to see why media or directions are missing, as plain text to photograph:
     * the links, the navigation state, what is posted right now and what the listener was handed
     * recently. If a nav app is guiding and has no line here, it is not posting a notification.
     */
    @Composable
    private fun DebugText() {
        val listener = QsNotificationListener.instance
        val nav = NavStore.state.collectAsState().value
        val navApps = NavStore.KNOWN_NAV_PACKAGES.mapNotNull { pkg ->
            runCatching { "$pkg ${packageManager.getPackageInfo(pkg, 0).versionName}" }.getOrNull()
        }
        val active = listener?.describeActive().orEmpty()
        val text = buildString {
            appendLine("${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}  v${BuildConfig.VERSION_NAME}  mock=${BuildConfig.USE_MOCK}  user=${android.os.Process.myUserHandle()}")
            appendLine("car=${app.car.isConnected()}  carMedia=${app.media.carMediaConnected}  source=${app.media.carSourceLabel}")
            appendLine("raw ${app.car.debugRanges()}")
            appendLine("access=${hasNotificationAccess()}  listenerConnected=${listener != null}")
            appendLine("nav apps installed: ${navApps.ifEmpty { listOf("none known") }.joinToString()}")
            appendLine("nav route: ${nav?.let { "${it.packageName} \"${it.title}\" / \"${it.text}\" / \"${it.subText}\" arrow=${it.maneuver != null}" } ?: "none"}")
            appendLine("last nav app: ${NavStore.lastNavPackage}")
            appendLine("last nav notification: ${NavStore.lastSeen}")
            appendLine("--- posted now (${active.size})")
            active.forEach(::appendLine)
            appendLine("--- handed to the listener (newest last)")
            DebugLog.snapshot().forEach(::appendLine)
        }
        Text(text.trimEnd(), color = Qs.Text, fontSize = 13.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace)
    }

    @Composable
    private fun StatusRow(@StringRes title: Int, ok: Boolean, value: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (ok) Qs.Energy else Qs.EnergyLow))
            QsText(stringResource(title), 16.sp, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            QsText(value, 14.sp, Qs.TextSoft)
        }
    }

    private fun hasNotificationAccess(): Boolean = getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(ComponentName(this, QsNotificationListener::class.java))

    /** Car builds often hide these settings pages; the adb command under the row is the fallback. */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // No such page on this head unit.
        }
    }

    @Composable
    private fun PermissionRow(@StringRes title: Int, @StringRes why: Int, granted: Boolean, adb: String?, grant: () -> Unit) {
        val name = stringResource(title)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (granted) Qs.Energy else Qs.EnergyLow))
            Column(Modifier.weight(1f)) {
                QsText(name, 16.sp, weight = FontWeight.SemiBold)
                QsText(stringResource(why), 12.sp, Qs.Muted)
                // The full command, wrapped, so it can be typed from the screen.
                if (!granted && adb != null) {
                    Text(adb, color = Qs.TextSoft, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (!granted) GlassButton(null, stringResource(R.string.grant_desc, name), grant, label = stringResource(R.string.grant))
        }
    }

    @Composable
    private fun WidgetList(enabled: List<WidgetType>, onChange: (List<WidgetType>) -> Unit) {
        val rowHeightPx = with(LocalDensity.current) { 74.dp.toPx() }
        var dragging by remember { mutableStateOf<WidgetType?>(null) }
        var dragOffset by remember { mutableFloatStateOf(0f) }
        var order by remember(enabled) { mutableStateOf(enabled) }
        val all = order + WidgetType.entries.filter { it !in order }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            all.forEach { type ->
                // Keyed so a row keeps its drag gesture when it moves to another slot.
                key(type) {
                    val on = type in order
                    val isDragged = dragging == type
                    val title = stringResource(type.titleRes())
                    Row(
                        Modifier.fillMaxWidth().height(64.dp)
                            .zIndex(if (isDragged) 1f else 0f)
                            .offset { IntOffset(0, if (isDragged) dragOffset.roundToInt() else 0) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDragged) Color(0x1AFFFFFF) else Color(0x0AFFFFFF))
                            .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                            .padding(start = 4.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val reorderDescription = stringResource(R.string.reorder, title)
                        Box(
                            Modifier.size(56.dp).semantics { contentDescription = reorderDescription }
                                .then(if (!on) Modifier else Modifier.pointerInput(type, enabled) {
                                    detectVerticalDragGestures(
                                        onDragStart = { dragging = type; dragOffset = 0f },
                                        onVerticalDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount
                                            val from = order.indexOf(type)
                                            val shift = (dragOffset / rowHeightPx).roundToInt()
                                            val to = (from + shift).coerceIn(0, order.lastIndex)
                                            if (to != from) {
                                                order = order.toMutableList().apply { add(to, removeAt(from)) }
                                                dragOffset -= (to - from) * rowHeightPx
                                            }
                                        },
                                        onDragEnd = { dragging = null; dragOffset = 0f; onChange(order) },
                                        onDragCancel = { dragging = null; dragOffset = 0f; order = enabled },
                                    )
                                }),
                            contentAlignment = Alignment.Center,
                        ) { QsIcon(Icons.Drag, if (on) Qs.Muted else Color(0x336B7686), 22.dp, strokeWidth = 3f) }
                        WidgetBadge(type, on)
                        Column(Modifier.weight(1f)) {
                            QsText(title, 16.sp, if (on) Qs.Text else Qs.Muted, FontWeight.SemiBold)
                            QsText(stringResource(type.subtitleRes()), 13.sp, Qs.Muted)
                        }
                        Toggle(on, stringResource(R.string.show_widget, title)) {
                            onChange(if (on) order - type else order + type)
                        }
                    }
                }
            }
        }
    }

    /** Mini version of each widget's identity, as the styleguide's settings rows show. */
    @Composable
    private fun WidgetBadge(type: WidgetType, on: Boolean) {
        val shape = RoundedCornerShape(12.dp)
        val (background, icon, tint) = when (type) {
            WidgetType.MEDIA -> Triple<Any, String?, Color>(ArtPlaceholder, null, Color.White)
            WidgetType.CLIMATE -> Triple<Any, String?, Color>(Qs.TempGradient, Icons.AirConditioner, Color.White)
            WidgetType.CAR_MODE -> Triple<Any, String?, Color>(Qs.Mode.copy(alpha = 0.18f), Icons.BatteryBolt, Qs.Mode)
            WidgetType.AUTONOMY -> Triple<Any, String?, Color>(Qs.Energy.copy(alpha = 0.18f), Icons.Battery, Qs.Energy)
            WidgetType.NAVIGATION -> Triple<Any, String?, Color>(Qs.Route.copy(alpha = 0.18f), Icons.TurnRight, Qs.Route)
            WidgetType.ASSIST -> Triple<Any, String?, Color>(Qs.Energy.copy(alpha = 0.18f), Icons.AutoHold, Qs.Energy)
        }
        val bg = when (background) {
            is Brush -> Modifier.background(background)
            is Color -> Modifier.background(background)
            else -> Modifier
        }
        Box(
            Modifier.size(40.dp).clip(shape).then(bg).then(if (on) Modifier else Modifier.background(Color(0x990B1018))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) QsIcon(icon, tint, 22.dp)
        }
    }

    @Composable
    private fun Toggle(on: Boolean, description: String, onToggle: () -> Unit) {
        Box(
            Modifier.width(64.dp).height(56.dp).clickable(role = Role.Switch, onClickLabel = description, onClick = onToggle)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.width(52.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(if (on) Qs.Mode else Color(0x2EFFFFFF))) {
                Box(Modifier.padding(3.dp).offset(x = if (on) 22.dp else 0.dp).size(24.dp).clip(RoundedCornerShape(12.dp)).background(Color.White))
            }
        }
    }

    @Composable
    private fun Panel(content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(24.dp)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(Color(0x0AFFFFFF)).border(1.dp, Color(0x14FFFFFF), shape).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@StringRes
fun WidgetType.titleRes(): Int = when (this) {
    WidgetType.MEDIA -> R.string.widget_media
    WidgetType.CLIMATE -> R.string.widget_climate
    WidgetType.CAR_MODE -> R.string.widget_car_mode
    WidgetType.AUTONOMY -> R.string.widget_autonomy
    WidgetType.NAVIGATION -> R.string.widget_navigation
    WidgetType.ASSIST -> R.string.widget_assist
}

@StringRes
fun WidgetType.subtitleRes(): Int = when (this) {
    WidgetType.MEDIA -> R.string.widget_media_sub
    WidgetType.CLIMATE -> R.string.widget_climate_sub
    WidgetType.CAR_MODE -> R.string.widget_car_mode_sub
    WidgetType.AUTONOMY -> R.string.widget_autonomy_sub
    WidgetType.NAVIGATION -> R.string.widget_navigation_sub
    WidgetType.ASSIST -> R.string.widget_assist_sub
}

private const val STATUS_REFRESH_MS = 1_500L

/** Typed by hand on a PC with adb; kept identical to the README. */
const val ADB_OVERLAY =
    "adb shell appops set com.carlosrpg.cherry.quicksettings SYSTEM_ALERT_WINDOW allow"

const val ADB_NOTIFICATIONS =
    "adb shell cmd notification allow_listener com.carlosrpg.cherry.quicksettings/.nav.QsNotificationListener"

/** Appends to the list instead of replacing it: the car's own accessibility services must stay on. */
const val ADB_TAP_RELAY =
    "adb shell 'settings --user current put secure enabled_accessibility_services " +
        "\"$(settings --user current get secure enabled_accessibility_services):" +
        "com.carlosrpg.cherry.quicksettings/.overlay.TapRelayService\"'\n" +
        "adb shell settings --user current put secure accessibility_enabled 1"
