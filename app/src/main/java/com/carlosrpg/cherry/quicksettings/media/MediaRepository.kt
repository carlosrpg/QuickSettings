package com.carlosrpg.cherry.quicksettings.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import androidx.annotation.StringRes
import app.jconfig.carbus.media.CarMedia
import app.jconfig.carbus.media.CarMediaAction
import app.jconfig.carbus.media.CarMediaSource
import app.jconfig.carbus.media.CarMediaState
import app.jconfig.carbus.media.CarMediaTrack
import app.jconfig.carbus.media.CarPlayStatus
import com.carlosrpg.cherry.quicksettings.BuildConfig
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.nav.QsNotificationListener
import com.carlosrpg.cherry.quicksettings.util.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaState(
    /** Package that published the session: voice search and "open app" go to it. Null for a car source. */
    val packageName: String? = null,
    /** The app's name, or the car source's (Bluetooth, CarPlay, …). */
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
     * False on Android Automotive: the level belongs to whichever source the car's audio
     * service is driving, and an app can't read that. Volume up/down still work there (the
     * car shows its own volume bar), so only the number and the bar are hidden.
     */
    val volumeKnown: Boolean = true,
    /** Debug builds on fixed-volume systems (the emulator): the number is only simulated. */
    val volumeSimulated: Boolean = false,
    /** False until notification access is granted (needed to see other apps' sessions). */
    val hasAccess: Boolean = false,
)

/**
 * Which feed the media widget shows. Whichever is playing wins, an app first. When neither is
 * playing (the usual case right after a pause), the one already on screen stays: pausing an app
 * must not hand the widget to a car source that happens to exist, and the other way round.
 * With nothing shown before, a known car source is shown ahead of a paused app.
 */
internal fun showCarSource(sessionPresent: Boolean, sessionPlaying: Boolean, car: CarMediaState?, carShown: Boolean): Boolean = when {
    car == null -> false
    sessionPlaying -> false
    car.status == CarPlayStatus.PLAYING || car.status == CarPlayStatus.LOADING -> true
    !sessionPresent -> true
    else -> carShown
}

@StringRes
internal fun CarMediaSource.labelRes(): Int = when (this) {
    CarMediaSource.LOCAL -> R.string.source_usb
    CarMediaSource.BLUETOOTH -> R.string.source_bluetooth
    CarMediaSource.CARPLAY -> R.string.source_carplay
    CarMediaSource.ANDROID_AUTO -> R.string.source_android_auto
    CarMediaSource.HICAR -> R.string.source_hicar
    CarMediaSource.CARLIFE -> R.string.source_carlife
    CarMediaSource.ONLINE -> R.string.source_online
    CarMediaSource.VIDEO -> R.string.source_video
    CarMediaSource.RADIO -> R.string.source_radio
    CarMediaSource.DAB -> R.string.source_dab
}

/**
 * What is playing, from two feeds that never overlap:
 *  - Android `MediaSession`s, for apps installed on the head unit. Seeing them needs
 *    notification-listener access for [QsNotificationListener].
 *  - [carMedia], the head unit's own sources (Bluetooth, USB, CarPlay, Android Auto, its
 *    online apps, radio). Those never publish a session.
 */
class MediaRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val carMedia: CarMedia?,
) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val listener = ComponentName(context, QsNotificationListener::class.java)

    private val _state = MutableStateFlow(MediaState())
    val state: StateFlow<MediaState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var hasAccess = false
    private var pollJob: Job? = null
    private var sessionsListenerAdded = false

    /** Last reading of the car's own source, and the one on screen right now (if any). */
    private var car: CarMediaState? = null
    private var shownCar: CarMediaState? = null
    private var shownOnce = false
    private var carArtKey: Any? = null
    private var carArt: Bitmap? = null

    /** For the settings status panel: the link to the car's media service and what it reports. */
    val carMediaConnected: Boolean get() = carMedia?.connected == true
    val carSourceLabel: String? get() = car?.let { context.getString(it.source.labelRes()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refresh()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { refresh() }

    /** Starts following both feeds and re-reads every [intervalMs] (neither pushes everything). */
    fun start(intervalMs: Long = POLL_MS) {
        if (pollJob?.isActive == true) return refresh()
        pollJob = scope.launch {
            while (isActive) {
                refreshNow()
                delay(intervalMs)
            }
        }
    }

    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        scope.launch { refreshNow() }
    }

    private suspend fun refreshNow() {
        readSessions()
        carMedia?.let { media ->
            // Binder calls into the car's media service: off the main thread.
            val reading = withContext(Dispatchers.IO) { media.read() }
            val key = artKey(reading?.track)
            if (key != carArtKey) {
                carArt = withContext(Dispatchers.IO) { decodeArt(reading?.track) }
                carArtKey = key
            }
            car = reading
        }
        publish()
    }

    private fun readSessions() {
        val active = try {
            if (!sessionsListenerAdded) {
                sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener)
                sessionsListenerAdded = true
            }
            sessions.getActiveSessions(listener)
        } catch (e: SecurityException) {
            hasAccess = false
            setController(null)
            return
        }
        hasAccess = true
        setController(
            active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: active.firstOrNull()
        )
    }

    private fun setController(picked: MediaController?) {
        if (picked?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(controllerCallback)
        controller = picked
        picked?.registerCallback(controllerCallback)
    }

    /** The cover comes as encoded bytes (phone projections) or as a content URI (USB, Bluetooth). */
    private fun artBytes(track: CarMediaTrack?) = track?.artBytes?.takeIf { it.isNotEmpty() }

    private fun artUri(track: CarMediaTrack?) = track?.artUrl?.takeIf { it.startsWith("content://") }

    private fun artKey(track: CarMediaTrack?): Any? = artBytes(track)?.contentHashCode() ?: artUri(track)

    private fun decodeArt(track: CarMediaTrack?): Bitmap? = try {
        val bytes = artBytes(track)
        val uri = artUri(track)
        when {
            bytes != null -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            uri != null -> context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
            else -> null
        }
    } catch (e: Exception) {
        // Unreadable URI (another app's provider) or a bad image: show the placeholder.
        null
    }

    private fun publish() {
        val c = controller
        val sessionPlaying = c?.playbackState?.state == PlaybackState.STATE_PLAYING
        val carNow = car.takeIf { showCarSource(c != null, sessionPlaying, it, carShown = shownCar != null || !shownOnce) }
        shownOnce = true
        shownCar = carNow
        _state.value = (if (carNow != null) carState(carNow) else sessionState(c)).withVolume()
    }

    private fun carState(c: CarMediaState): MediaState {
        val label = context.getString(c.source.labelRes())
        return MediaState(
            appLabel = label,
            // A source without track text (radio) still says what it is.
            title = c.track?.title?.takeIf { it.isNotBlank() } ?: label,
            artist = c.track?.artist?.takeIf { it.isNotBlank() },
            art = carArt,
            playing = c.status == CarPlayStatus.PLAYING,
            positionMs = c.positionMs.toLong(),
            durationMs = c.durationMs.toLong(),
            hasAccess = hasAccess,
        )
    }

    private fun sessionState(c: MediaController?): MediaState {
        val meta = c?.metadata
        val playback = c?.playbackState
        return MediaState(
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
            hasAccess = hasAccess,
        )
    }

    // Testing aid: where volume is fixed (Android Automotive, the emulator) debug builds keep an
    // expected volume so the number can be watched. Release builds never simulate.
    private val simulateVolume = BuildConfig.DEBUG && audio.isVolumeFixed
    private var simulatedVolume: Int? = null
    private var simulatedMute = false

    private fun MediaState.withVolume(): MediaState {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return copy(
            // Simulated volume starts mid-range so both directions can be tried.
            volume = if (simulateVolume) simulatedVolume ?: (max / 2) else audio.getStreamVolume(AudioManager.STREAM_MUSIC),
            maxVolume = max,
            muted = if (simulateVolume) simulatedMute else audio.isStreamMute(AudioManager.STREAM_MUSIC),
            volumeKnown = !audio.isVolumeFixed || simulateVolume,
            volumeSimulated = simulateVolume,
        )
    }

    fun playPause() {
        val carNow = shownCar ?: run {
            val controls = controller?.transportControls ?: return
            return if (_state.value.playing) controls.pause() else controls.play()
        }
        // As the car's own media card: no command while the source reports no usable status.
        val status = carNow.status ?: return
        sendCar(carNow, if (status == CarPlayStatus.PLAYING) CarMediaAction.PAUSE else CarMediaAction.PLAY)
    }

    fun next() = shownCar?.let { sendCar(it, CarMediaAction.NEXT) } ?: controller?.transportControls?.skipToNext()

    fun previous() =
        shownCar?.let { sendCar(it, CarMediaAction.PREVIOUS) } ?: controller?.transportControls?.skipToPrevious()

    private fun sendCar(target: CarMediaState, action: CarMediaAction) {
        val media = carMedia ?: return
        scope.launch {
            withContext(Dispatchers.IO) { media.send(target, action) }
            delay(CAR_SETTLE_MS)
            refreshNow()
        }
    }

    /** Hands a spoken query to the app that owns the session. Car sources have no search. */
    fun playFromSearch(query: String) =
        controller?.transportControls?.playFromSearch(query, Bundle())

    /**
     * One step up or down. `adjustVolume`, not a stream volume: on Android Automotive stream
     * volumes are fixed, and this is the call the steering-wheel keys end in. The car's audio
     * service moves whichever source is playing by one step and shows its own volume bar.
     */
    fun volumeStep(direction: Int) {
        audio.adjustVolume(if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
        if (simulateVolume) simulatedVolume = (_state.value.volume + direction).coerceIn(0, _state.value.maxVolume)
        publish()
    }

    fun toggleMute() {
        audio.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, 0)
        if (simulateVolume) simulatedMute = !simulatedMute
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
        private const val CAR_SETTLE_MS = 300L
    }
}
