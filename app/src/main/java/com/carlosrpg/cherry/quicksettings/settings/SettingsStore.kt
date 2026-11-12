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
 * @property tapToOpen touching the edge handle opens the drawer; off = swipe only.
 */
data class QsConfig(
    val side: Side = Side.LEFT,
    val edgePosition: Float = 0.5f,
    val semi: List<WidgetType> = WidgetType.entries,
    val open: List<WidgetType> = DEFAULT_OPEN,
    val tapToOpen: Boolean = true,
)

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
            .apply()
    }

    private fun load() = QsConfig(
        side = prefs.getString(KEY_SIDE, null)?.let { runCatching { Side.valueOf(it) }.getOrNull() }
            ?: Side.LEFT,
        edgePosition = prefs.getFloat(KEY_EDGE, 0.5f),
        semi = decode(prefs.getString(KEY_SEMI, null)) ?: WidgetType.entries,
        open = decode(prefs.getString(KEY_OPEN, null)) ?: DEFAULT_OPEN,
        tapToOpen = prefs.getBoolean(KEY_TAP_OPEN, true),
    )

    companion object {
        private const val KEY_SIDE = "side"
        private const val KEY_EDGE = "edge_position"
        private const val KEY_SEMI = "semi_widgets"
        private const val KEY_OPEN = "open_widgets"
        private const val KEY_TAP_OPEN = "tap_to_open"

        internal fun encode(list: List<WidgetType>) = list.joinToString(",") { it.name }

        /** Unknown names are dropped; null when nothing was stored. An empty list is kept. */
        internal fun decode(value: String?): List<WidgetType>? = value?.split(',')
            ?.mapNotNull { name -> WidgetType.entries.firstOrNull { it.name == name } }
            ?.distinct()
    }
}
