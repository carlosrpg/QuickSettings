package com.carlosrpg.cherry.quicksettings.media

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import app.jconfig.carbus.cluster.ClusterMedia
import app.jconfig.carbus.cluster.ClusterTrack

/** Where [ClusterMediaSync] writes: the car's cluster, or a log on the emulator. */
interface ClusterMediaSink {
    /** Whether writes can go out right now; null where there is no cluster (emulator). */
    val connected: Boolean?
    fun showTrack(title: String, artist: String, cover: Bitmap?): Boolean
    fun showProgress(playing: Boolean, positionMs: Long, durationMs: Long): Boolean
}

/** The instrument cluster's media card, through jconfig-carbus. */
class CarClusterMediaSink(private val cluster: ClusterMedia) : ClusterMediaSink {
    override val connected: Boolean get() = cluster.connected

    override fun showTrack(title: String, artist: String, cover: Bitmap?) =
        cluster.showTrack(ClusterTrack(title, artist, cover?.let(ClusterMedia::encodeCover)))

    override fun showProgress(playing: Boolean, positionMs: Long, durationMs: Long) =
        cluster.showProgress(playing, positionMs, durationMs)
}

/** Emulator stand-in: logs what would have gone to the cluster. */
class LogClusterMediaSink : ClusterMediaSink {
    override val connected: Boolean? = null

    override fun showTrack(title: String, artist: String, cover: Bitmap?): Boolean {
        Log.i(TAG, "cluster track: '$title' / '$artist' cover=${cover != null}")
        return true
    }

    override fun showProgress(playing: Boolean, positionMs: Long, durationMs: Long): Boolean {
        Log.i(TAG, "cluster progress: playing=$playing $positionMs/$durationMs ms")
        return true
    }

    private companion object { const val TAG = "QsClusterSync" }
}

/**
 * Keeps the instrument cluster's media card on an app that plays on its own (Motion, for one).
 * The car's meter service fills that card only from the car's own sources, so while such an app
 * plays the cluster shows nothing or a stale track. Only writes while an app session is the one
 * shown and playing: a car source playing is the meter service's to show.
 *
 * Call [tick] every couple of seconds with the media widget's state; it writes the track once per
 * change, then play status and position, and one last "paused" when the app stops.
 */
class ClusterMediaSync(
    private val sink: ClusterMediaSink,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private var sentTrack: String? = null
    private var sentPlaying = false

    /** What the sync did, newest last, for the debug screen. */
    val events = EventRing()
    @Volatile var writes = 0
        private set
    @Volatile var lastWrite: String = "none"
        private set

    /** One line for the debug screen: link, what is held, how much has been written. */
    fun describe(): String =
        "link=${sink.connected ?: "none (emulator)"}  holding=${sentTrack ?: "nothing"}  playing=$sentPlaying  writes=$writes  last=$lastWrite"

    /** What changed, for the log; null when nothing worth logging happened. */
    fun tick(state: MediaState): String? = step(state)?.also(events::add)

    private fun step(state: MediaState): String? {
        val appShown = state.hasAccess && state.packageName != null && !state.title.isNullOrBlank()
        if (!appShown) {
            val hadIt = sentTrack != null
            reset()
            return if (hadIt) "car source or nothing playing: the cluster is the car's again" else null
        }
        // Never take the card over for an app that is only paused.
        if (!state.playing && !sentPlaying) return null
        val track = "${state.packageName}|${state.title}|${state.artist}"
        var change: String? = null
        if (track != sentTrack) {
            val artist = state.artist?.takeIf { it.isNotBlank() } ?: state.appLabel.orEmpty()
            if (!sink.showTrack(state.title.orEmpty(), artist, state.art)) return "cluster not connected"
            sentTrack = track
            change = "track '${state.title}' from ${state.packageName}"
        }
        val position = livePosition(state)
        if (!sink.showProgress(state.playing, position, state.durationMs)) return "cluster not connected"
        writes++
        lastWrite = "${if (state.playing) "play" else "pause"} $position/${state.durationMs} ms '${state.title}'"
        if (sentPlaying && !state.playing) change = change ?: "paused"
        sentPlaying = state.playing
        return change
    }

    fun reset() {
        sentTrack = null
        sentPlaying = false
    }

    /** The session reports a position at a moment; while playing it has moved on since. */
    internal fun livePosition(state: MediaState): Long {
        if (!state.playing || state.positionAt <= 0) return state.positionMs
        val moved = state.positionMs + (now() - state.positionAt).coerceAtLeast(0)
        return if (state.durationMs > 0) moved.coerceAtMost(state.durationMs) else moved
    }
}
