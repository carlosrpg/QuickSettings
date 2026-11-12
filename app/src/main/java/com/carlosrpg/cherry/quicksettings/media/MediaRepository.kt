package com.carlosrpg.cherry.quicksettings.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import com.carlosrpg.cherry.quicksettings.BuildConfig
import com.carlosrpg.cherry.quicksettings.nav.QsNotificationListener
import com.carlosrpg.cherry.quicksettings.util.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class MediaState(
    /** Package that published the session: voice search and "open app" go to it. */
    val packageName: String? = null,
    val appLabel: String? = null,
    val appIcon: Bitmap? = null,
    val title: String? = null,
    val artist: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 0,
    val maxVolume: Int = 0,
    val muted: Boolean = false,
    /**
     * False on builds with fixed volume (Android Automotive): there the car's volume lives in
     * CarAudioManager, which needs a privileged permission, and AudioManager calls do nothing.
     */
    val volumeAdjustable: Boolean = true,
    /** Debug builds on fixed-volume systems (the emulator): the volume is only simulated. */
    val volumeSimulated: Boolean = false,
    /** False until notification access is granted (needed to see other apps' sessions). */
    val hasAccess: Boolean = false,
)

/**
 * Follows the active media session. Seeing other apps' sessions needs notification-listener
 * access for [QsNotificationListener]; without it the state stays empty.
 */
class MediaRepository(private val context: Context, private val scope: CoroutineScope) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listener = ComponentName(context, QsNotificationListener::class.java)

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private var sessionsListenerAdded = false

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }

    /** Starts following sessions and re-reads every [intervalMs] (sessions and volume change elsewhere). */
    fun start(intervalMs: Long = POLL_MS) {
        refresh()
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                refresh()
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        val active = try {
            if (!sessionsListenerAdded) {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener)
                sessionsListenerAdded = true
            }
            sessions.getActiveSessions(listener)
        } catch (e: SecurityException) {
            _state.value = MediaState(hasAccess = false).withVolume()
            return
        }
        val picked = active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: active.firstOrNull()
        if (picked?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(controllerCallback)
            controller = picked
            picked?.registerCallback(controllerCallback)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val playback = c?.playbackState
        _state.value = MediaState(
            packageName = c?.packageName,
            appLabel = c?.packageName?.let { AppInfo.label(context, it) },
            appIcon = c?.packageName?.let { AppInfo.icon(context, it) },
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            playing = playback?.state == PlaybackState.STATE_PLAYING,
            positionMs = playback?.position ?: 0,
            durationMs = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0,
            hasAccess = true,
        ).withVolume()
    }

    // Testing aid: where volume is fixed (Android Automotive, the emulator) debug builds keep an
    // expected volume so the controls can be exercised. Release builds never simulate.
    private val simulateVolume = BuildConfig.DEBUG && audio.isVolumeFixed
    private var simulatedVolume: Int? = null
    private var simulatedMute = false

    private fun MediaState.withVolume(): MediaState {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return withVolume(max)
    }

    private fun MediaState.withVolume(max: Int) = copy(
        // Simulated volume starts mid-range so both directions can be tried.
        volume = if (simulateVolume) simulatedVolume ?: (max / 2) else audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        maxVolume = max,
        muted = if (simulateVolume) simulatedMute else audio.isStreamMute(AudioManager.STREAM_MUSIC),
        volumeAdjustable = !audio.isVolumeFixed || simulateVolume,
        volumeSimulated = simulateVolume,
    )

    fun playPause() {
        val controls = controller?.transportControls ?: return
        if (_state.value.playing) controls.pause() else controls.play()
    }

    fun next() = controller?.transportControls?.skipToNext()
    fun previous() = controller?.transportControls?.skipToPrevious()

    /** Hands a spoken query to the app that owns the session. */
    fun playFromSearch(query: String) =
        controller?.transportControls?.playFromSearch(query, Bundle())

    // Only meaningful where volume isn't fixed (or is simulated); on a release Automotive build
    // [MediaState.volumeAdjustable] is false and the UI hides these controls.
    fun volumeStep(direction: Int) {
        if (simulateVolume) return setVolume(_state.value.volume + direction)
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0
        )
        publish()
    }

    fun setVolume(index: Int) {
        val target = index.coerceIn(0, _state.value.maxVolume)
        if (simulateVolume) simulatedVolume = target
        else audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        publish()
    }

    fun toggleMute() {
        if (simulateVolume) simulatedMute = !simulatedMute
        else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        publish()
    }

    /** Brings the media app to the front. */
    fun openApp(): Boolean = launch(_state.value.packageName)

    private fun launch(pkg: String?): Boolean {
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    companion object {
        const val POLL_MS = 2_000L
    }
}
