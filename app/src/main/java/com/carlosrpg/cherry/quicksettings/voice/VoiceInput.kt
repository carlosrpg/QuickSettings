package com.carlosrpg.cherry.quicksettings.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import com.carlosrpg.cherry.quicksettings.R
import com.carlosrpg.cherry.quicksettings.overlay.OverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot speech-to-text. Call from the main thread. When the head unit has no recognizer
 * or the microphone permission is missing, [listen] calls `onUnavailable` instead, and the
 * caller falls back to opening the target app.
 */
class VoiceInput(private val context: Context) {
    private val _listening = MutableStateFlow(false)
    val listening: StateFlow<Boolean> = _listening.asStateFlow()
    private var recognizer: SpeechRecognizer? = null

    fun listen(onText: (String) -> Unit, onUnavailable: () -> Unit) {
        val granted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted || !SpeechRecognizer.isRecognitionAvailable(context)) {
            onUnavailable()
            return
        }
        cancel()
        // Android 11 lets a foreground service use the microphone only if it was (re)started
        // while the app had a window on screen. At boot it had none; the drawer is showing now.
        OverlayService.start(context)
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                finish()
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() }?.let(onText)
            }
            override fun onError(error: Int) {
                Log.w(TAG, "speech recognition error $error")
                finish()
                // Said on screen: otherwise the card just stops listening and nothing happens.
                Toast.makeText(context, context.getString(errorMessage(error), error), Toast.LENGTH_LONG).show()
            }
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        _listening.value = true
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        )
    }

    fun cancel() {
        recognizer?.destroy()
        finish()
    }

    private fun finish() {
        _listening.value = false
        recognizer?.destroy()
        recognizer = null
    }

    private companion object {
        const val TAG = "QsVoice"

        /** The recognizer here is Google's, which needs the internet: the car offline is the usual cause. */
        @StringRes
        fun errorMessage(error: Int): Int = when (error) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
                R.string.voice_error_network
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_no_match
            SpeechRecognizer.ERROR_AUDIO, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_mic
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_error_busy
            else -> R.string.voice_error_other
        }
    }
}
