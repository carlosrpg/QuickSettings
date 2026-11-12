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
import com.carlosrpg.cherry.quicksettings.nav.QsNotificationListener
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
    val title: String? = null,
    val artist: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val volume: Int = 0,
    val maxVolume: Int = 0,
    val muted: Boolean = false,
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
            appLabel = c?.packageName?.let(::labelOf),
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

    private fun MediaState.withVolume() = copy(
        volume = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
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

    // shortcut: media volume goes through AudioManager's music stream; on a car build with
    // dynamic audio routing this may not move the car's media group. Verify on the head unit.
    fun volumeStep(direction: Int) {
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0
        )
        publish()
    }

    fun setVolume(index: Int) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, index.coerceIn(0, _state.value.maxVolume), 0)
        publish()
    }

    fun toggleMute() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
        publish()
    }

    /** Brings the media app to the front. */
    fun openApp(): Boolean = launch(_state.value.packageName)

    private fun launch(pkg: String?): Boolean {
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    private fun labelOf(pkg: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    companion object {
        const val POLL_MS = 2_000L
    }
}
