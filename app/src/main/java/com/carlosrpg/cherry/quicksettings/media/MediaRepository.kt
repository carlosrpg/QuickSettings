package com.carlosrpg.cherry.quicksettings.media

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.service.media.MediaBrowserService
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A volume the side sliders set. The navigation voice is the text-to-speech stream, which the
 * head unit's audio service maps to its navigation guidance volume group.
 */
enum class VolumeChannel(val stream: Int) {
    MEDIA(AudioManager.STREAM_MUSIC),
    NAVIGATION(STREAM_TTS),
}

data class VolumeLevel(val value: Int, val max: Int)

/** `AudioSystem.STREAM_TTS`: not public, but a valid stream for the volume calls. */
private const val STREAM_TTS = 9
/**
 * Moves a level to a target once the target has held still for [debounceMs], then one [step]
 * (+1 or -1) every [stepMs], following the target if it moves meanwhile. Not thread-safe: call
 * [moveTo] on [scope]'s (single) thread.
 */
internal class LevelStepper(
    private val scope: CoroutineScope,
    private val read: () -> Int,
    private val step: (direction: Int) -> Unit,
    private val debounceMs: Long = 300L,
    private val stepMs: Long = 100L,
) {
    private var target: Int? = null
    private var job: Job? = null

    val moving: Boolean get() = job?.isActive == true

    fun moveTo(value: Int) {
        target = value
        if (moving) return
        job = scope.launch {
            var held: Int?
            do {
                held = target
                delay(debounceMs)
            } while (target != held)
            var current = read()
            while (true) {
                val goal = target ?: break
                if (goal == current) break
                val direction = if (goal > current) 1 else -1
                current += direction
                step(direction)
                delay(stepMs)
            }
        }
    }
}

data class MediaState(
    /** Package that published the session: voice search and "open app" go to it. Null for a car source. */
    val packageName: String? = null,
    /**
     * The app that shows what is playing, for the card's tap: the session's package, or for a car
     * source the head unit app that plays it (Android Auto, CarPlay, …). Null when there is none.
     */
    val openPackage: String? = null,
    /** The app's name, or the car source's (Bluetooth, CarPlay, …). */
    val appLabel: String? = null,
    val appIcon: Bitmap? = null,
    val title: String? = null,
    val artist: String? = null,
    val art: Bitmap? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    /** [android.os.SystemClock.elapsedRealtime] when [positionMs] was reported; 0 when unknown. */
    val positionAt: Long = 0,
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
    /** Apps that had a media session lately, newest first, without the one on the card. */
    val recent: List<RecentApp> = emptyList(),
)

/** A media app (one that had a session; never a car source or a system session) the card offers to go back to. */
data class RecentApp(val packageName: String, val label: String, val icon: Bitmap?)

/** [recent] with [pkg] moved to the front, at most [keep] long. */
internal fun bumpRecent(recent: List<String>, pkg: String, keep: Int): List<String> = (listOf(pkg) + (recent - pkg)).take(keep)

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

/**
 * The head unit app that shows a car source, or null when it has none to open (radio and DAB
 * live in the tuner service; HiCar, CarLife and the online apps are not installed here).
 */
internal fun CarMediaSource.appPackage(): String? = when (this) {
    CarMediaSource.ANDROID_AUTO -> "com.google.android.projection.sink"
    CarMediaSource.CARPLAY -> "com.desaysv.vehicle.carplayapp"
    CarMediaSource.BLUETOOTH, CarMediaSource.LOCAL -> "com.desaysv.svaudioapp"
    CarMediaSource.VIDEO -> "com.desaysv.videoapp"
    else -> null
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
    private var sessionsListenerAdded = false

    /** A car media read that timed out may still be blocked in the service: wait on it, don't stack more. */
    private var carRead: kotlinx.coroutines.Deferred<CarMediaState?>? = null

    /**
     * Changes of the car's own source and of the app session, newest last. The car's meter
     * service rewrites the cluster's media card on a car source change, so one right before the
     * cluster lost an app's track points at that.
     */
    val events = EventRing(30)
    private var lastCarLine: String? = null
    private var lastSessionLine: String? = null

    /** The car's media service now: source, status, track, time. */
    fun describeCar(): String = describe(car)

    /** The app session in front now: package, playing, track. */
    fun describeSession(): String = controller?.let { c ->
        val meta = c.metadata
        val playback = c.playbackState
        "${c.packageName} state=${playback?.state} '${meta?.getString(MediaMetadata.METADATA_KEY_TITLE)}' " +
            "pos=${playback?.position} at=${playback?.lastPositionUpdateTime}"
    } ?: "none"

    private fun describe(c: CarMediaState?): String = c?.let {
        "${it.source} (type ${it.type}) ${it.status} '${it.track?.title}' / '${it.track?.artist}' ${it.positionMs}/${it.durationMs} ms"
    } ?: "none"

    private fun noteChanges() {
        val carLine = car?.let { "${it.source} ${it.status} '${it.track?.title}'" } ?: "none"
        if (carLine != lastCarLine) {
            events.add("car media: $carLine")
            lastCarLine = carLine
        }
        val c = controller
        val sessionLine = c?.let { "${it.packageName} state=${it.playbackState?.state} '${it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)}'" } ?: "none"
        if (sessionLine != lastSessionLine) {
            events.add("app session: $sessionLine")
            lastSessionLine = sessionLine
        }
    }

    /** [SystemClock.elapsedRealtime] of the last complete refresh; 0 before the first. */
    @Volatile var lastRefreshAt = 0L
        private set

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

    fun refresh() {
        scope.launch { refreshNow() }
    }

    /**
     * Reads both feeds once: the overlay's heartbeat calls this (neither feed pushes everything;
     * the car's own sources push nothing).
     */
    suspend fun refreshNow() {
        readSessions()
        // A playing app session always wins the card (showCarSource), so the car's media service
        // is only asked while no app plays: four binder calls a beat saved while one does.
        val appPlaying = controller?.playbackState?.state == PlaybackState.STATE_PLAYING
        carMedia?.takeUnless { appPlaying }?.let { media ->
            // Binder calls into the car's media service: off the main thread, and abandoned
            // after a while, since a call that never returns cannot be cancelled.
            // (null is a real answer here, "no car source", so wait on completion, not the value.)
            val pending = carRead?.takeIf { it.isActive } ?: scope.async(Dispatchers.IO) { media.read() }.also { carRead = it }
            val finished = withTimeoutOrNull(READ_TIMEOUT_MS) { pending.join() } != null
            val reading = if (finished) {
                pending.await()
            } else {
                Log.w(TAG, "car media read timed out")
                car
            }
            val key = artKey(reading?.track)
            if (key != carArtKey) {
                carArt = withContext(Dispatchers.IO) { decodeArt(reading?.track) }
                carArtKey = key
            }
            car = reading
        }
        publish()
        noteChanges()
        lastRefreshAt = SystemClock.elapsedRealtime()
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
        // Every app that publishes a session joins the history; the list is newest first, so the newest goes in last.
        val packages = active.map { it.packageName }.distinct()
        packages.reversed().filter { it !in sessionPackages }.forEach(::noteSession)
        sessionPackages = packages.toSet()
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
        // Playing moves an app that already had a session back to the front.
        if (sessionPlaying) c?.packageName?.let(::noteSession)
        val shown = if (carNow != null) carState(carNow) else sessionState(c)
        _state.value = shown.copy(recent = recentApps(except = shown.openPackage)).withVolume()
    }

    /** Package names of the apps that had a media session lately, newest first; kept across restarts. */
    private val recentPrefs = context.getSharedPreferences("media", Context.MODE_PRIVATE)
    private var recentPackages = recentPrefs.getString(KEY_RECENT, null)?.split(',')?.filter { it.isNotBlank() }.orEmpty()

    /** The apps with a session at the last read, so only a session that appears counts as new. */
    private var sessionPackages = emptySet<String>()

    private fun noteSession(pkg: String) {
        if (recentPackages.firstOrNull() == pkg) return
        if (!isMediaApp(pkg)) return
        recentPackages = bumpRecent(recentPackages, pkg, RECENT_KEEP)
        recentPrefs.edit().putString(KEY_RECENT, recentPackages.joinToString(",")).apply()
    }

    /**
     * An app the card can bring up: one with a launcher entry, or an installed one that only
     * offers a media browser (YouTube Music on the car OS, played in the car's media screen).
     * Never a system session without either, such as Bluetooth or the phone.
     */
    private fun isMediaApp(pkg: String): Boolean {
        val pm = context.packageManager
        if (pm.getLaunchIntentForPackage(pkg) != null) return true
        val system = runCatching { pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0 }.getOrDefault(true)
        return !system && browserService(pkg) != null
    }

    private fun browserService(pkg: String): ComponentName? =
        context.packageManager.queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0)
            .firstOrNull()?.serviceInfo?.let { ComponentName(it.packageName, it.name) }

    /** Brings [pkg] up: its launcher entry, or else the car's media screen switched to its browser. */
    private fun bringUp(pkg: String): Boolean {
        if (runCatching { launch(pkg) }.getOrDefault(false)) return true
        val browser = browserService(pkg) ?: return false
        val intent = Intent(CAR_MEDIA_TEMPLATE).putExtra(CAR_MEDIA_COMPONENT, browser.flattenToString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.onFailure { Log.w(TAG, "no media screen for $browser", it) }.isSuccess
    }

    /** Connection kept until the next [playApp]: some apps stop their service once the last client leaves. */
    private var playBrowser: MediaBrowser? = null

    /** Plays [component]'s session through its media browser, which reaches it even while the session is inactive. */
    private fun playThroughBrowser(component: ComponentName) {
        playBrowser?.disconnect()
        lateinit var browser: MediaBrowser
        browser = MediaBrowser(context, component, object : MediaBrowser.ConnectionCallback() {
            override fun onConnected() {
                runCatching { MediaController(context, browser.sessionToken).transportControls.play() }
                    .onFailure { Log.w(TAG, "play through $component failed", it) }
            }

            // The app only lets known media centres connect: the session poll in [playApp] still plays it once it is up.
            override fun onConnectionFailed() {
                Log.i(TAG, "$component refused the media browser connection")
            }
        }, null)
        playBrowser = browser
        runCatching { browser.connect() }.onFailure { Log.w(TAG, "media browser $component", it) }
    }

    private fun recentApps(except: String?): List<RecentApp> = recentPackages.asSequence()
        .filter { it != except }
        // Uninstalled since: label lookup fails.
        .mapNotNull { pkg -> AppInfo.label(context, pkg)?.let { RecentApp(pkg, it, AppInfo.icon(context, pkg)) } }
        .take(RECENT_SHOWN)
        .toList()

    /**
     * A recent app's icon: bring [pkg] to the front and play it, through its media browser if it
     * has one, and through its session as soon as that is active (right away, or once a cold-started
     * app publishes it). False when it can't be opened.
     */
    fun playApp(pkg: String): Boolean {
        if (!bringUp(pkg)) return false
        browserService(pkg)?.let(::playThroughBrowser)
        scope.launch {
            repeat((PLAY_WAIT_MS / PLAY_RETRY_MS).toInt()) {
                val session = runCatching { sessions.getActiveSessions(listener) }.getOrNull()?.firstOrNull { it.packageName == pkg }
                if (session != null) {
                    session.transportControls.play()
                    delay(CAR_SETTLE_MS)
                    refreshNow()
                    return@launch
                }
                delay(PLAY_RETRY_MS)
            }
            Log.i(TAG, "$pkg published no media session: opened, not played")
        }
        return true
    }

    private fun carState(c: CarMediaState): MediaState {
        val label = context.getString(c.source.labelRes())
        // Installed (cached lookup: this runs on every publish).
        val app = c.source.appPackage()?.takeIf { AppInfo.label(context, it) != null }
        return MediaState(
            openPackage = app,
            appLabel = label,
            appIcon = app?.let { AppInfo.icon(context, it) },
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
            openPackage = c?.packageName,
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
            positionAt = playback?.lastPositionUpdateTime ?: 0,
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

    /**
     * Voice search in the playing app. YouTube and YouTube Music (and their patched builds) ignore
     * `playFromSearch`, so they get their own search page as a link instead (checked on the car:
     * YouTube's `ACTION_SEARCH` only brings back the open video). True when the query reached an
     * app (the link opened, or the session took `playFromSearch`): the drawer then closes. Car
     * sources have no search, and the hidden background session must not get it either.
     */
    fun playFromSearch(query: String): Boolean {
        val pkg = _state.value.packageName ?: return false
        val url = youtubeSearchUrl(pkg, query)
        if (url != null) {
            val opened = runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Log.w(TAG, "voice search link in $pkg failed", it) }.isSuccess
            if (opened) return true
        }
        val controls = controller?.takeIf { it.packageName == pkg }?.transportControls ?: return false
        controls.playFromSearch(query, Bundle())
        // The session plays the result in the background (Motion does): bring its app up so the
        // video shows. The launch intent resumes the app's task, it doesn't restart it.
        val shown = runCatching { launch(pkg) }
            .onFailure { Log.w(TAG, "voice search: could not bring $pkg up", it) }.getOrDefault(false)
        Log.i(TAG, "voice search: playFromSearch in $pkg, brought up: $shown")
        return true
    }

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

    /**
     * The level of [channel], for the side sliders. On the head unit the stream volumes are fixed,
     * but its audio service keeps each stream's level in step with the car's volume group (media,
     * navigation guidance), so these read and set the car's own levels. Debug builds on a fixed-
     * volume emulator simulate them.
     */
    fun level(channel: VolumeChannel): VolumeLevel {
        val max = audio.getStreamMaxVolume(channel.stream)
        val value = if (simulateVolume) simulatedLevels[channel] ?: (max / 2) else audio.getStreamVolume(channel.stream)
        return VolumeLevel(value.coerceIn(0, max), max)
    }

    /**
     * One step of [channel] up or down. Media goes through `adjustVolume`, the call the media
     * card's - / + buttons use and that works on the car; the navigation voice through the same
     * adjust on its own stream. No flag: no car volume bar, the slider is the feedback.
     */
    private fun stepLevel(channel: VolumeChannel, direction: Int) {
        val adjust = if (direction > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        if (simulateVolume) {
            val now = simulatedLevels[channel] ?: (audio.getStreamMaxVolume(channel.stream) / 2)
            simulatedLevels[channel] = (now + direction).coerceIn(0, audio.getStreamMaxVolume(channel.stream))
            if (channel == VolumeChannel.MEDIA) simulatedVolume = simulatedLevels[channel]
        } else {
            runCatching {
                if (channel == VolumeChannel.MEDIA) audio.adjustVolume(adjust, 0)
                else audio.adjustStreamVolume(channel.stream, adjust, 0)
            }
        }
        if (channel == VolumeChannel.MEDIA) publish()
    }

    private val simulatedLevels = HashMap<VolumeChannel, Int>()

    private val volumeSteppers = VolumeChannel.entries.associateWith { channel ->
        LevelStepper(scope, read = { level(channel).value }, step = { stepLevel(channel, it) })
    }

    /** True while [moveLevel] is still taking [channel] to its target. */
    fun moving(channel: VolumeChannel): Boolean = volumeSteppers.getValue(channel).moving

    /** Takes [channel] to [value] the way the climate sliders go: debounced, one step per command. */
    fun moveLevel(channel: VolumeChannel, value: Int) =
        volumeSteppers.getValue(channel).moveTo(value.coerceIn(0, level(channel).max))

    /** Brings the media app to the front. */
    fun openApp(): Boolean = _state.value.openPackage?.let(::bringUp) ?: false

    private fun launch(pkg: String?): Boolean {
        val intent = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) } ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    companion object {
        private const val READ_TIMEOUT_MS = 5_000L
        private const val TAG = "QsMedia"
        private const val CAR_SETTLE_MS = 300L
        private const val KEY_RECENT = "recent_apps"
        private const val RECENT_SHOWN = 3
        /** One more than shown, so the card's own app can be left out and three remain. */
        private const val RECENT_KEEP = RECENT_SHOWN + 1
        /** How long a cold-started app gets to publish its session before the play is dropped. */
        private const val PLAY_WAIT_MS = 8_000L
        private const val PLAY_RETRY_MS = 400L
        /** The car OS's media screen, opened on one app's media browser (`Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE`). */
        private const val CAR_MEDIA_TEMPLATE = "android.car.intent.action.MEDIA_TEMPLATE"
        private const val CAR_MEDIA_COMPONENT = "android.car.intent.extra.MEDIA_COMPONENT"
    }
}

/**
 * The search page for [query] in a YouTube app, or null when [pkg] is not one. Matches by name so
 * patched builds (ReVanced, Morphe: `app.morphe.android.youtube`, ...) count too.
 */
fun youtubeSearchUrl(pkg: String?, query: String): String? {
    val name = pkg?.lowercase() ?: return null
    if ("youtube" !in name) return null
    val q = java.net.URLEncoder.encode(query, "UTF-8")
    return if ("music" in name) "https://music.youtube.com/search?q=$q" else "https://www.youtube.com/results?search_query=$q"
}
