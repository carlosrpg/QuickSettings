package com.carlosrpg.cherry.quicksettings.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WidgetType { MEDIA, CLIMATE, CAR_MODE, AUTONOMY, NAVIGATION, ASSIST }

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
 * @property topBar keep a strip of car and media info in the status bar's area at the top of
 *   the screen, under the car's own clock and icons; display only (the car takes those touches).
 */
data class QsConfig(
    val side: Side = Side.LEFT,
    val edgePosition: Float = 0.5f,
    val semi: List<WidgetType> = WidgetType.entries,
    val open: List<WidgetType> = DEFAULT_OPEN,
    val tapToOpen: Boolean = true,
    val topBar: Boolean = false,
    val clusterMediaSync: Boolean = false,
    val semiOrder: List<WidgetType> = withHidden(semi),
    val openOrder: List<WidgetType> = withHidden(open),
)

/** [shown] followed by every widget it leaves out: the list order before any was saved. */
fun withHidden(shown: List<WidgetType>): List<WidgetType> = shown + WidgetType.entries.filter { it !in shown }

/** The approved open layout: media + nav + assist, then climate + car mode + range. */
val DEFAULT_OPEN = listOf(
    WidgetType.MEDIA, WidgetType.NAVIGATION, WidgetType.ASSIST,
    WidgetType.CLIMATE, WidgetType.CAR_MODE, WidgetType.AUTONOMY,
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
            .putBoolean(KEY_TOP_BAR, next.topBar)
            .putBoolean(KEY_CLUSTER_MEDIA, next.clusterMediaSync)
            .putString(KEY_SEMI_ORDER, encode(next.semiOrder))
            .putString(KEY_OPEN_ORDER, encode(next.openOrder))
            .apply()
    }

    private fun load(): QsConfig {
        val semi = decode(prefs.getString(KEY_SEMI, null)) ?: WidgetType.entries
        val open = decode(prefs.getString(KEY_OPEN, null)) ?: DEFAULT_OPEN
        return QsConfig(
            side = prefs.getString(KEY_SIDE, null)?.let { runCatching { Side.valueOf(it) }.getOrNull() }
                ?: Side.LEFT,
            edgePosition = prefs.getFloat(KEY_EDGE, 0.5f),
            semi = semi,
            open = open,
            tapToOpen = prefs.getBoolean(KEY_TAP_OPEN, true),
            topBar = prefs.getBoolean(KEY_TOP_BAR, false),
            clusterMediaSync = prefs.getBoolean(KEY_CLUSTER_MEDIA, false),
            // A saved order may predate a widget; withHidden appends whatever it lacks.
            semiOrder = withHidden(decode(prefs.getString(KEY_SEMI_ORDER, null)) ?: semi),
            openOrder = withHidden(decode(prefs.getString(KEY_OPEN_ORDER, null)) ?: open),
        )
    }

    companion object {
        private const val KEY_SIDE = "side"
        private const val KEY_EDGE = "edge_position"
        private const val KEY_SEMI = "semi_widgets"
        private const val KEY_OPEN = "open_widgets"
        private const val KEY_TAP_OPEN = "tap_to_open"
        private const val KEY_TOP_BAR = "top_bar"
        private const val KEY_CLUSTER_MEDIA = "cluster_media_sync"
        private const val KEY_SEMI_ORDER = "semi_order"
        private const val KEY_OPEN_ORDER = "open_order"

        internal fun encode(list: List<WidgetType>) = list.joinToString(",") { it.name }

        /** Unknown names are dropped; null when nothing was stored. An empty list is kept. */
        internal fun decode(value: String?): List<WidgetType>? = value?.split(',')
            ?.mapNotNull { name -> WidgetType.entries.firstOrNull { it.name == name } }
            ?.distinct()
    }
}
