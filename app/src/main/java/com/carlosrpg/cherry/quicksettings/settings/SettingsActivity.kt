package com.carlosrpg.cherry.quicksettings.settings

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.carlosrpg.cherry.quicksettings.app
import com.carlosrpg.cherry.quicksettings.nav.QsNotificationListener
import com.carlosrpg.cherry.quicksettings.overlay.OverlayService
import com.carlosrpg.cherry.quicksettings.overlay.TapRelayService
import com.carlosrpg.cherry.quicksettings.ui.CardLabel
import com.carlosrpg.cherry.quicksettings.ui.GlassButton
import com.carlosrpg.cherry.quicksettings.ui.Icons
import com.carlosrpg.cherry.quicksettings.ui.Qs
import com.carlosrpg.cherry.quicksettings.ui.QsIcon
import com.carlosrpg.cherry.quicksettings.ui.QsText
import com.carlosrpg.cherry.quicksettings.ui.Segmented
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
        Column(
            Modifier.fillMaxSize().background(Color(0xFF0B1018)).verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                GlassButton(Icons.ChevronLeft, "Back", ::finish)
                QsText("QuickSettings", 26.sp, weight = FontWeight.Bold)
            }
            // Re-checked on every resume: grants happen in system settings or over adb.
            key(refresh) { Permissions() }
            Panel {
                CardLabel("Drawer position")
                QsText("Side", 14.sp, Qs.TextSoft, FontWeight.SemiBold)
                Segmented(Side.entries, config.side, { if (it == Side.LEFT) "Left" else "Right" }, Qs.Text, { side -> store.update { it.copy(side = side) } })
                var position by remember(config.edgePosition) { mutableFloatStateOf(config.edgePosition) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    QsText("Position on edge", 14.sp, Qs.TextSoft, FontWeight.SemiBold)
                    QsText(positionLabel(position), 14.sp, Qs.Muted)
                }
                Slider(
                    value = position,
                    onValueChange = { position = it },
                    onValueChangeFinished = { store.update { it.copy(edgePosition = position) } },
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Qs.Route, inactiveTrackColor = Color(0x1FFFFFFF)),
                    modifier = Modifier.height(56.dp).semantics { contentDescription = "Drawer position on edge" },
                )
            }
            var tab by remember { mutableStateOf(0) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardLabel("Widgets", Modifier.weight(1f))
                    Segmented(listOf(0, 1), tab, { if (it == 0) "Semi-open" else "Open" }, Qs.Text, { tab = it }, Modifier.width(340.dp))
                }
                QsText("Hold the handle and drag to reorder. Each state keeps its own list.", 13.sp, Qs.Muted)
                val list = if (tab == 0) config.semi else config.open
                WidgetList(list) { updated -> store.update { if (tab == 0) it.copy(semi = updated) else it.copy(open = updated) } }
            }
        }
    }

    private fun positionLabel(p: Float) = when {
        p < 0.33f -> "top"
        p > 0.67f -> "bottom"
        else -> "middle"
    }

    @Composable
    private fun Permissions() = Panel {
        CardLabel("Permissions")
        PermissionRow(
            "Draw over other apps", "Needed for the drawer.", OverlayService.canDraw(this@SettingsActivity),
            "adb shell appops set $packageName SYSTEM_ALERT_WINDOW allow",
        ) { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        PermissionRow(
            "Notification access", "Media sessions and navigation directions.", hasNotificationAccess(),
            "adb shell cmd notification allow_listener $packageName/${QsNotificationListener::class.java.name}",
        ) { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        PermissionRow(
            "Microphone", "Voice search for media and destinations.",
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            "adb shell pm grant --user current $packageName android.permission.RECORD_AUDIO",
        ) { micPermission.launch(Manifest.permission.RECORD_AUDIO) }
        PermissionRow(
            "Tap pass-through (optional)", "Lets taps in the edge swipe zone reach the app behind.", TapRelayService.instance != null,
            "adb shell settings put secure enabled_accessibility_services $packageName/${TapRelayService::class.java.name}",
        ) { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    private fun hasNotificationAccess(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(this, QsNotificationListener::class.java)
        return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** Car builds often hide these settings pages; the adb line under each row is the fallback. */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            // No such page on this head unit.
        }
    }

    @Composable
    private fun PermissionRow(title: String, why: String, granted: Boolean, adb: String, grant: () -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(if (granted) Qs.Energy else Qs.EnergyLow))
            Column(Modifier.weight(1f)) {
                QsText(title, 16.sp, weight = FontWeight.SemiBold)
                QsText(if (granted) why else "$why  ·  $adb", 12.sp, Qs.Muted)
            }
            if (!granted) GlassButton(null, "Grant $title", grant, label = "Grant")
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
                val on = type in order
                val isDragged = dragging == type
                Row(
                    Modifier.fillMaxWidth().height(64.dp)
                        .zIndex(if (isDragged) 1f else 0f)
                        .offset { IntOffset(0, if (isDragged) dragOffset.roundToInt() else 0) }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isDragged) Color(0x1AFFFFFF) else Color(0x0AFFFFFF))
                        .border(1.dp, Color(0x0FFFFFFF), RoundedCornerShape(16.dp))
                        .padding(start = 6.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        Modifier.size(56.dp).semantics { contentDescription = "Reorder ${type.title()}" }
                            .then(if (!on) Modifier else Modifier.pointerInput(type) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragging = type; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
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
                    Column(Modifier.weight(1f)) {
                        QsText(type.title(), 16.sp, if (on) Qs.Text else Qs.Muted, FontWeight.SemiBold)
                        QsText(type.subtitle(), 13.sp, Qs.Muted)
                    }
                    Toggle(on, "Show ${type.title()}") {
                        onChange(if (on) order - type else order + type)
                    }
                }
            }
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

fun WidgetType.title(): String = when (this) {
    WidgetType.MEDIA -> "Media"
    WidgetType.CLIMATE -> "Climate"
    WidgetType.CAR_MODE -> "Car mode"
    WidgetType.AUTONOMY -> "Autonomy"
    WidgetType.NAVIGATION -> "Navigation"
    WidgetType.ASSIST -> "Assist"
}

fun WidgetType.subtitle(): String = when (this) {
    WidgetType.MEDIA -> "Art, play / pause, hold for volume and track"
    WidgetType.CLIMATE -> "Temperature × fan quadrant"
    WidgetType.CAR_MODE -> "Battery mode and regeneration"
    WidgetType.AUTONOMY -> "Battery and fuel, range"
    WidgetType.NAVIGATION -> "Next turn, ETA, voice destination"
    WidgetType.ASSIST -> "Emergency braking, auto hold, lane keeping"
}
