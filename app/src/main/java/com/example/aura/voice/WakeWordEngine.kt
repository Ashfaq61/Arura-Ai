package com.example.aura.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.example.aura.ai.AuraLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min

data class WakeWordState(
    val enabled: Boolean = false,
    val wakePhrase: String = "Hey AURA",
    val sensitivity: Float = 0.72f, // 0.3..1.0
    val batterySaverMode: Boolean = true,
    val isMonitoring: Boolean = false,
    val micIndicatorActive: Boolean = false,
    val pausedForLowBattery: Boolean = false,
    val recognitionAvailable: Boolean = false,
    val detectionCount: Int = 0,
    val lastDetectedPhrase: String = "",
    val lastDetectedAt: Long? = null,
    val statusMessage: String = "Wake Word standby. Enable to listen for 'Hey AURA' or use manual orb."
)

/**
 * Battery-aware, privacy-visible Wake Word Engine supporting configurable "Hey AURA"
 * activation, visible microphone indicator, low-battery gating, and manual fallback.
 */
class WakeWordEngine(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var activeCallback: ((String) -> Unit)? = null
    private var activeLanguage: AuraLanguage = AuraLanguage.ENGLISH

    private val _state = MutableStateFlow(
        WakeWordState(
            recognitionAvailable = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        )
    )
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    fun updateConfig(enabled: Boolean, phrase: String, sensitivity: Float, batterySaver: Boolean) {
        val cleanPhrase = phrase.trim().ifBlank { "Hey AURA" }
        _state.value = _state.value.copy(
            enabled = enabled,
            wakePhrase = cleanPhrase,
            sensitivity = sensitivity.coerceIn(0.3f, 1.0f),
            batterySaverMode = batterySaver,
            statusMessage = if (enabled) {
                "Wake Word configured for '$cleanPhrase'."
            } else {
                "Wake Word disabled (mic off)."
            }
        )
        if (!enabled) {
            stopMonitoring()
        }
    }

    fun startMonitoring(
        language: AuraLanguage = AuraLanguage.ENGLISH,
        onWakeWordDetected: (remainderCommand: String) -> Unit
    ) {
        activeCallback = onWakeWordDetected
        activeLanguage = language

        val cur = _state.value
        if (!cur.enabled) {
            _state.value = cur.copy(isMonitoring = false, micIndicatorActive = false, statusMessage = "Enable Wake Word toggle first.")
            return
        }

        val hasMicPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!hasMicPerm) {
            _state.value = cur.copy(
                isMonitoring = false,
                micIndicatorActive = false,
                statusMessage = "RECORD_AUDIO permission required for Wake Word. Use Manual Fallback button anytime."
            )
            return
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
        val charging = bm?.isCharging ?: false
        if (cur.batterySaverMode && pct in 1..14 && !charging) {
            _state.value = cur.copy(
                isMonitoring = false,
                micIndicatorActive = false,
                pausedForLowBattery = true,
                statusMessage = "Battery saver paused Wake Word at $pct% battery (connect charger or disable Battery Saver)."
            )
            return
        }

        val available = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        if (!available) {
            _state.value = cur.copy(
                isMonitoring = false,
                micIndicatorActive = false,
                recognitionAvailable = false,
                statusMessage = "On-device SpeechRecognizer unavailable here. Use Manual Trigger Fallback."
            )
            return
        }

        mainHandler.post { launchRecognizerCycle() }
    }

    private fun launchRecognizerCycle() {
        val cur = _state.value
        if (!cur.enabled) return
        runCatching {
            recognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(
                        isMonitoring = true,
                        micIndicatorActive = true,
                        pausedForLowBattery = false,
                        recognitionAvailable = true,
                        statusMessage = "Mic active — listening for '${_state.value.wakePhrase}'..."
                    )
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(micIndicatorActive = false)
                }

                override fun onError(error: Int) {
                    _state.value = _state.value.copy(micIndicatorActive = false)
                    if (_state.value.enabled && error != SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        // Battery-efficient 1.8s backoff before restarting spotter cycle
                        mainHandler.postDelayed({
                            if (_state.value.enabled) launchRecognizerCycle()
                        }, 1800L)
                    } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        stopMonitoring()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val candidates = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val matchedRemainder = findWakeMatchInCandidates(candidates)
                    if (matchedRemainder != null) {
                        recordDetection(_state.value.wakePhrase, matchedRemainder)
                    } else if (_state.value.enabled) {
                        mainHandler.postDelayed({
                            if (_state.value.enabled) launchRecognizerCycle()
                        }, 900L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    val matchedRemainder = findWakeMatchInCandidates(partials)
                    if (matchedRemainder != null) {
                        runCatching { sr.cancel() }
                        recordDetection(_state.value.wakePhrase, matchedRemainder)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, activeLanguage.localeTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            sr.startListening(intent)
        }.onFailure { err ->
            _state.value = _state.value.copy(
                isMonitoring = false,
                micIndicatorActive = false,
                statusMessage = "Wake monitoring stopped: ${err.localizedMessage ?: "unavailable"}"
            )
        }
    }

    private fun findWakeMatchInCandidates(candidates: List<String>): String? {
        val cfg = _state.value
        for (c in candidates) {
            val remainder = matchWakePhrase(c, cfg.wakePhrase, cfg.sensitivity)
            if (remainder != null) return remainder
        }
        return null
    }

    private fun recordDetection(phrase: String, remainder: String) {
        _state.value = _state.value.copy(
            isMonitoring = false,
            micIndicatorActive = false,
            detectionCount = _state.value.detectionCount + 1,
            lastDetectedPhrase = phrase,
            lastDetectedAt = System.currentTimeMillis(),
            statusMessage = "Wake phrase detected! Triggering AURA turn..."
        )
        activeCallback?.invoke(remainder)
    }

    fun triggerManualWakeWord(command: String = "", onTriggered: (String) -> Unit) {
        _state.value = _state.value.copy(
            detectionCount = _state.value.detectionCount + 1,
            lastDetectedPhrase = "${_state.value.wakePhrase} (Manual)",
            lastDetectedAt = System.currentTimeMillis(),
            statusMessage = "Manual wake activation triggered."
        )
        onTriggered(command.trim())
    }

    fun stopMonitoring() {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.post {
            runCatching {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
        }
        _state.value = _state.value.copy(
            isMonitoring = false,
            micIndicatorActive = false,
            statusMessage = if (_state.value.enabled) "Wake monitoring paused." else "Wake Word disabled (mic off)."
        )
    }

    companion object {
        private val builtInAliases = listOf("hey aura", "ok aura", "hey ora", "aura suno", "aey aura", "اورا")

        /**
         * Returns the trailing spoken command (empty string if only wake phrase was spoken),
         * or null if [transcript] does not match [configuredPhrase].
         */
        fun matchWakePhrase(
            transcript: String,
            configuredPhrase: String = "Hey AURA",
            sensitivity: Float = 0.72f
        ): String? {
            val cleanTranscript = transcript.trim()
            if (cleanTranscript.isBlank()) return null
            val lower = cleanTranscript.lowercase()
            val target = configuredPhrase.trim().lowercase().ifBlank { "hey aura" }
            val candidates = (listOf(target) + builtInAliases).distinct()

            for (alias in candidates) {
                val idx = lower.indexOf(alias)
                if (idx >= 0) {
                    val after = cleanTranscript.substring((idx + alias.length).coerceAtMost(cleanTranscript.length)).trim()
                        .trimStart(',', ':', '-', ' ')
                    return after
                }
            }

            // Fuzzy token-window match for phonetic drift (e.g. "hey laura" when sensitivity <= 0.8)
            val targetTokens = target.split(Regex("\\s+")).filter { it.isNotBlank() }
            val words = lower.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (targetTokens.isNotEmpty() && words.size >= targetTokens.size) {
                val maxDistance = if (sensitivity >= 0.85f) 1 else 2
                val window = words.take(targetTokens.size).joinToString(" ")
                val dist = levenshtein(window, target)
                if (dist <= maxDistance) {
                    val rest = cleanTranscript.split(Regex("\\s+")).drop(targetTokens.size).joinToString(" ")
                    return rest
                }
            }
            return null
        }

        private fun levenshtein(a: String, b: String): Int {
            val dp = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) dp[i][0] = i
            for (j in 0..b.length) dp[0][j] = j
            for (i in 1..a.length) {
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
                }
            }
            return dp[a.length][b.length]
        }
    }
}
