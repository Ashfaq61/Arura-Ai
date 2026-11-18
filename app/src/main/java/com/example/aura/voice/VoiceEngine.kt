package com.example.aura.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.aura.ai.AuraLanguage
import com.example.aura.ai.AuraVoiceCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

enum class VoiceOrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    STOPPED,
    ERROR
}

class VoiceEngine(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var focusRequest: AudioFocusRequest? = null

    private val _orbState = MutableStateFlow(VoiceOrbState.IDLE)
    val orbState: StateFlow<VoiceOrbState> = _orbState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _voiceLogs = MutableStateFlow<List<String>>(emptyList())
    val voiceLogs: StateFlow<List<String>> = _voiceLogs.asStateFlow()

    private val _continuousConversation = MutableStateFlow(false)
    val continuousConversation: StateFlow<Boolean> = _continuousConversation.asStateFlow()

    private var lastSpokenDigest: Int = 0
    private var lastSpokenAtMs: Long = 0L

    init {
        initTts()
    }

    fun setContinuousConversation(enabled: Boolean) {
        _continuousConversation.value = enabled
        log("Continuous voice conversation: ${if (enabled) "ON" else "OFF"}")
    }

    private fun initTts() {
        runCatching {
            tts = TextToSpeech(context.applicationContext) { status ->
                ttsReady = (status == TextToSpeech.SUCCESS)
                if (ttsReady) {
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _orbState.value = VoiceOrbState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            abandonAudioFocus()
                            if (_orbState.value == VoiceOrbState.SPEAKING) {
                                _orbState.value = VoiceOrbState.IDLE
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            abandonAudioFocus()
                            _orbState.value = VoiceOrbState.ERROR
                        }
                    })
                    log("TTS engine initialized.")
                } else {
                    log("TTS initialization failed (status=$status).")
                }
            }
        }.onFailure {
            log("TTS unavailable in this environment: ${it.localizedMessage}")
        }
    }

    fun isRecognitionAvailable(): Boolean =
        runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)

    fun startListening(
        language: AuraLanguage,
        onFinalTranscript: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        // Immediate barge-in interruption of ongoing TTS
        stopSpeaking()

        if (!isRecognitionAvailable()) {
            val msg = "Android SpeechRecognizer service is unavailable on this device/emulator. Type your message or enable Google Speech Services."
            log(msg)
            _orbState.value = VoiceOrbState.ERROR
            onError(msg)
            return
        }

        runCatching {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _orbState.value = VoiceOrbState.LISTENING
                    _partialTranscript.value = ""
                    log("Listening (${language.localeTag}) — silence & turn-end detection active...")
                }

                override fun onBeginningOfSpeech() {
                    _orbState.value = VoiceOrbState.LISTENING
                }

                override fun onRmsChanged(rmsdB: Float) {
                    _rmsLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    _rmsLevel.value = 0f
                    _orbState.value = VoiceOrbState.THINKING
                }

                override fun onError(error: Int) {
                    _rmsLevel.value = 0f
                    val desc = mapSpeechError(error)
                    log("SpeechRecognizer error: $desc")
                    _orbState.value = VoiceOrbState.ERROR
                    onError(desc)
                }

                override fun onResults(results: Bundle?) {
                    _rmsLevel.value = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull().orEmpty().trim()
                    _partialTranscript.value = text
                    if (text.isNotBlank()) {
                        log("Recognized: \"$text\"")
                        _orbState.value = VoiceOrbState.THINKING
                        onFinalTranscript(text)
                    } else {
                        _orbState.value = VoiceOrbState.IDLE
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) _partialTranscript.value = partial
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        }.onFailure { e ->
            log("Failed to start SpeechRecognizer: ${e.localizedMessage}")
            _orbState.value = VoiceOrbState.ERROR
            onError(e.localizedMessage ?: "Speech recognition failed to start.")
        }
    }

    fun stopListening() {
        runCatching { speechRecognizer?.stopListening() }
        _rmsLevel.value = 0f
        if (_orbState.value == VoiceOrbState.LISTENING) {
            _orbState.value = VoiceOrbState.IDLE
        }
    }

    fun speak(text: String, voicePresetName: String, language: AuraLanguage) {
        val clean = text.trim()
        if (clean.isBlank()) return

        // De-duplicate repeated responses within 6 seconds
        val digest = clean.hashCode()
        val now = System.currentTimeMillis()
        if (digest == lastSpokenDigest && now - lastSpokenAtMs < 6_000L) {
            log("Suppressed duplicate TTS response.")
            return
        }
        lastSpokenDigest = digest
        lastSpokenAtMs = now

        if (!ttsReady || tts == null) {
            log("TTS not ready; skipped spoken output.")
            return
        }
        val preset = AuraVoiceCatalog.findPreset(voicePresetName)
        requestAudioFocus()
        tts?.apply {
            val locale = if (language == AuraLanguage.URDU) Locale("ur", "PK") else Locale.US
            setLanguage(locale)
            setPitch(preset.ttsPitch)
            setSpeechRate(preset.ttsRate)
            speak(clean.take(1500), TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        }
        log("Speaking with voice preset '${preset.name}' (${preset.genderStyle})")
    }

    fun stopSpeaking() {
        runCatching { tts?.stop() }
        abandonAudioFocus()
        if (_orbState.value == VoiceOrbState.SPEAKING) {
            _orbState.value = VoiceOrbState.IDLE
        }
    }

    fun emergencyStopAll() {
        _continuousConversation.value = false
        runCatching { speechRecognizer?.cancel() }
        runCatching { tts?.stop() }
        abandonAudioFocus()
        _rmsLevel.value = 0f
        _orbState.value = VoiceOrbState.STOPPED
        log("Emergency STOP invoked on voice pipeline.")
    }

    fun setOrbState(state: VoiceOrbState) {
        _orbState.value = state
    }

    fun release() {
        runCatching { speechRecognizer?.destroy() }
        runCatching { tts?.shutdown() }
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .build()
            focusRequest = req
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission (RECORD_AUDIO) is required."
        SpeechRecognizer.ERROR_NETWORK -> "Speech network error."
        SpeechRecognizer.ERROR_NO_MATCH -> "Silence or no speech recognized."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy; session reset."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "End-of-turn silence timeout."
        else -> "Speech error code $code"
    }

    private fun log(msg: String) {
        Log.d(TAG, msg)
        _voiceLogs.value = (listOf(msg) + _voiceLogs.value).take(40)
    }

    companion object {
        private const val TAG = "AuraVoiceEngine"
    }
}
