package com.carlosrpg.cherry.quicksettings.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WidgetType { MEDIA, CLIMATE, CAR_MODE, AUTONOMY, NAVIGATION, ASSIST, WEATHER }

enum class Side { LEFT, RIGHT }

/**
 * @property edgePosition where along the edge the drawer sits, 0 = top, 1 = bottom.
 * @property semi widgets of the semi-open drawer, in order.
 * @property open widgets of the open drawer, in order.
 * @property semiOrder every widget in the order the settings list shows it for the semi-open
 *   drawer, hidden ones included, so hiding a widget keeps its place. Same for [openOrder].
 * @property clusterMediaSync while an app plays on its own (no car source behind it), write its
 *   track to the instrument cluster's media card. Off by default: it writes to the car.
 * @property tapToOpen touching the edge handle opens the drawer; off = swipe only.
 * @property sideHandle show the side handle. Off only takes effect with [topBar] on, whose
 *   swipe-down zone then is the one way in.
 * @property topBar keep a strip of car and media info in the status bar's area at the top of
 *   the screen, under the car's own clock and icons; display only (the car takes those touches).
 * @property carGesture the car's own two-finger swipe down opens the full drawer (QuickSettings
 *   listens for the car's gesture broadcast itself, no gesture app needed).
 * @property phoneControl paired phones on the car's Wi-Fi can see and use the drawer. Off by
 *   default: it opens a port on the car's network.
 * @property remoteAssist phones reaching the car through the relay may change driver assist.
 *   Off by default; turning it on asks for confirmation (docs/companion-api.md section 4).
 */
data class QsConfig(
    val side: Side = Side.LEFT,
    val edgePosition: Float = 0.5f,
    val semi: List<WidgetType> = WidgetType.entries,
    val open: List<WidgetType> = DEFAULT_OPEN,
    val tapToOpen: Boolean = true,
    val sideHandle: Boolean = false,
    val topBar: Boolean = true,
    val clusterMediaSync: Boolean = true,
    val carGesture: Boolean = true,
    val phoneControl: Boolean = false,
    val remoteAssist: Boolean = false,
    val semiOrder: List<WidgetType> = withHidden(semi),
    val openOrder: List<WidgetType> = withHidden(open),
)

/** [shown] followed by every widget it leaves out: the list order before any was saved. */
fun withHidden(shown: List<WidgetType>): List<WidgetType> = shown + WidgetType.entries.filter { it !in shown }

/**
 * A layout saved before the weather widget: it takes the half the range card gave up, right
 * after it. Unchanged when it already has weather or has no range card.
 */
fun addWeatherBesideRange(list: List<WidgetType>): List<WidgetType> {
    val at = list.indexOf(WidgetType.AUTONOMY)
    if (at < 0 || WidgetType.WEATHER in list) return list
    return list.toMutableList().apply { add(at + 1, WidgetType.WEATHER) }
}

/** The approved open layout: media + nav + assist, then climate + car mode + range + weather. */
val DEFAULT_OPEN = listOf(
    WidgetType.MEDIA, WidgetType.NAVIGATION, WidgetType.ASSIST,
    WidgetType.CLIMATE, WidgetType.CAR_MODE, WidgetType.AUTONOMY, WidgetType.WEATHER,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("quicksettings", Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(load())
    val config: StateFlow<QsConfig> = _config.asStateFlow()

    fun update(transform: (QsConfig) -> QsConfig) {
        val next = transform(_config.value)
        _config.value = next
        prefs.edit()
            .putString(KEY_SIDE, next.side.name)
            .putFloat(KEY_EDGE, next.edgePosition)
            .putString(KEY_SEMI, encode(next.semi))
            .putString(KEY_OPEN, encode(next.open))
            .putBoolean(KEY_TAP_OPEN, next.tapToOpen)
            .putBoolean(KEY_SIDE_HANDLE, next.sideHandle)
            .putBoolean(KEY_TOP_BAR, next.topBar)
            .putBoolean(KEY_CLUSTER_MEDIA, next.clusterMediaSync)
            .putBoolean(KEY_PHONE_CONTROL, next.phoneControl)
            .putBoolean(KEY_CAR_GESTURE, next.carGesture)
            .putBoolean(KEY_REMOTE_ASSIST, next.remoteAssist)
            .putString(KEY_SEMI_ORDER, encode(next.semiOrder))
            .putString(KEY_OPEN_ORDER, encode(next.openOrder))
            .apply()
    }

    private fun load(): QsConfig {
        val semi = decode(prefs.getString(KEY_SEMI, null)) ?: WidgetType.entries
        val savedOpenOrder = decode(prefs.getString(KEY_OPEN_ORDER, null))
        val open = (decode(prefs.getString(KEY_OPEN, null)) ?: DEFAULT_OPEN)
            .let { if (savedOpenOrder != null && WidgetType.WEATHER !in savedOpenOrder) addWeatherBesideRange(it) else it }
        return QsConfig(
            side = prefs.getString(KEY_SIDE, null)?.let { runCatching { Side.valueOf(it) }.getOrNull() }
                ?: Side.LEFT,
            edgePosition = prefs.getFloat(KEY_EDGE, 0.5f),
            semi = semi,
            open = open,
            tapToOpen = prefs.getBoolean(KEY_TAP_OPEN, true),
            sideHandle = prefs.getBoolean(KEY_SIDE_HANDLE, false),
            topBar = prefs.getBoolean(KEY_TOP_BAR, true),
            clusterMediaSync = prefs.getBoolean(KEY_CLUSTER_MEDIA, true),
            phoneControl = prefs.getBoolean(KEY_PHONE_CONTROL, false),
            carGesture = prefs.getBoolean(KEY_CAR_GESTURE, true),
            remoteAssist = prefs.getBoolean(KEY_REMOTE_ASSIST, false),
            // A saved order may predate a widget; withHidden appends whatever it lacks.
            semiOrder = withHidden(decode(prefs.getString(KEY_SEMI_ORDER, null)) ?: semi),
            openOrder = withHidden(savedOpenOrder?.let(::addWeatherBesideRange) ?: open),
        )
    }

    companion object {
        private const val KEY_SIDE = "side"
        private const val KEY_EDGE = "edge_position"
        private const val KEY_SEMI = "semi_widgets"
        private const val KEY_OPEN = "open_widgets"
        private const val KEY_TAP_OPEN = "tap_to_open"
        private const val KEY_TOP_BAR = "top_bar"
        private const val KEY_SIDE_HANDLE = "side_handle"
        private const val KEY_CLUSTER_MEDIA = "cluster_media_sync"
        private const val KEY_PHONE_CONTROL = "phone_control"
        private const val KEY_CAR_GESTURE = "car_gesture"
        private const val KEY_REMOTE_ASSIST = "remote_assist"
        private const val KEY_SEMI_ORDER = "semi_order"
        private const val KEY_OPEN_ORDER = "open_order"

        internal fun encode(list: List<WidgetType>) = list.joinToString(",") { it.name }

        /** Unknown names are dropped; null when nothing was stored. An empty list is kept. */
        internal fun decode(value: String?): List<WidgetType>? = value?.split(',')
            ?.mapNotNull { name -> WidgetType.entries.firstOrNull { it.name == name } }
            ?.distinct()
    }
}
