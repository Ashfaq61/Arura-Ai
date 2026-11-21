package com.example.aura.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.aura.ai.AuraLanguage
import com.example.aura.ai.GEMINI_TTS_DEFAULT_MODEL
import com.example.aura.ai.GeminiRestClient
import com.example.aura.ai.VoiceEngineMode
import com.example.aura.security.GeminiKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

enum class VoiceOrbState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    STOPPED,
    ERROR
}

interface VoiceAudioPlaybackBridge {
    suspend fun playFile(file: File): Boolean
    fun stop()
}

interface DeviceTtsPlaybackBridge {
    suspend fun speakChunk(text: String, language: AuraLanguage, utteranceId: String): Boolean
    fun stop()
    fun release()
}

object TtsTextChunker {
    private val markdownNoiseRegex = Regex("""(\*\*|__|~~|`)""")
    private val headingPrefixRegex = Regex("""(?m)^\s{0,3}#{1,6}\s+""")

    fun normalizeForSpeech(text: String): String {
        return text
            .replace(markdownNoiseRegex, "")
            .replace(headingPrefixRegex, "")
            .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
            .trim()
    }

    /**
     * Splits long replies into sentence-aligned chunks (max ~[maxChunkChars] characters each)
     * so each chunk can be synthesized/cached independently without turning the full reply robotic on one error.
     */
    fun splitIntoSentences(text: String, maxChunkChars: Int = MAX_CHUNK_CHARS): List<String> {
        val normalized = normalizeForSpeech(text)
        if (normalized.isBlank()) return emptyList()
        val cap = maxChunkChars.coerceAtLeast(40)

        val rawSentences = mutableListOf<String>()
        val paragraphs = normalized.split(Regex("\\n+")).map { it.trim() }.filter { it.isNotEmpty() }
        for (paragraph in paragraphs) {
            var start = 0
            var i = 0
            while (i < paragraph.length) {
                val ch = paragraph[i]
                val isEndPunct = ch == '.' || ch == '!' || ch == '?' || ch == '۔'
                val followedBySpaceOrEnd = (i == paragraph.lastIndex) || paragraph[i + 1].isWhitespace()
                if (isEndPunct && followedBySpaceOrEnd) {
                    val segment = paragraph.substring(start, i + 1).trim()
                    if (segment.isNotEmpty()) rawSentences.add(segment)
                    start = i + 1
                }
                i++
            }
            if (start < paragraph.length) {
                val tail = paragraph.substring(start).trim()
                if (tail.isNotEmpty()) rawSentences.add(tail)
            }
        }

        val chunks = mutableListOf<String>()
        for (sentence in rawSentences) {
            if (sentence.length <= cap) {
                chunks.add(sentence)
            } else {
                chunks.addAll(splitOversizedSentence(sentence, cap))
            }
        }
        return chunks
    }

    private fun splitOversizedSentence(sentence: String, maxLen: Int): List<String> {
        val result = mutableListOf<String>()
        var remaining = sentence.trim()
        while (remaining.length > maxLen) {
            val window = remaining.substring(0, maxLen + 1)
            val clauseBreak = maxOf(
                window.lastIndexOf(", "),
                window.lastIndexOf("; "),
                window.lastIndexOf(": ")
            )
            val splitAt = when {
                clauseBreak >= maxLen / 3 -> clauseBreak + 1
                else -> {
                    val spaceBreak = window.lastIndexOf(' ')
                    if (spaceBreak > 0) spaceBreak else maxLen
                }
            }
            val piece = remaining.substring(0, splitAt).trim()
            if (piece.isNotEmpty()) result.add(piece)
            remaining = remaining.substring(splitAt).trim()
        }
        if (remaining.isNotEmpty()) {
            result.add(remaining)
        }
        return result
    }

    const val MAX_CHUNK_CHARS = 200
}

class GeminiTtsAudioCache(
    cacheRootDir: File,
    private val maxFiles: Int = 80
) {
    private val dir = File(cacheRootDir, CACHE_FOLDER_NAME).apply { mkdirs() }

    fun findCachedFile(voiceName: String, textChunk: String): File? {
        val key = cacheKey(voiceName, textChunk)
        val extensions = listOf("wav", "mp3", "ogg")
        for (ext in extensions) {
            val f = File(dir, "$key.$ext")
            if (f.exists() && f.length() > 44L) {
                f.setLastModified(System.currentTimeMillis())
                return f
            }
        }
        return null
    }

    fun putAudio(voiceName: String, textChunk: String, rawBytes: ByteArray, mimeType: String): File {
        dir.mkdirs()
        val key = cacheKey(voiceName, textChunk)
        val lowerMime = mimeType.lowercase(Locale.US)
        val (finalBytes, ext) = when {
            lowerMime.contains("mpeg") || lowerMime.contains("mp3") -> rawBytes to "mp3"
            lowerMime.contains("ogg") -> rawBytes to "ogg"
            lowerMime.contains("wav") || isAlreadyWav(rawBytes) -> rawBytes to "wav"
            else -> {
                val sampleRate = parseSampleRate(mimeType)
                wrapPcmAsWav(rawBytes, sampleRate = sampleRate) to "wav"
            }
        }
        val target = File(dir, "$key.$ext")
        target.writeBytes(finalBytes)
        pruneIfNeeded()
        return target
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun pruneIfNeeded() {
        val files = dir.listFiles()?.toList().orEmpty()
        if (files.size <= maxFiles) return
        files.sortedBy { it.lastModified() }
            .take(files.size - maxFiles)
            .forEach { it.delete() }
    }

    companion object {
        const val CACHE_FOLDER_NAME = "gemini_tts_cache"
        private val rateRegex = Regex("rate=(\\d+)", RegexOption.IGNORE_CASE)

        fun cacheKey(voiceName: String, textChunk: String): String {
            val canonical = "${voiceName.trim().lowercase(Locale.US)}|${TtsTextChunker.normalizeForSpeech(textChunk)}"
            val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun parseSampleRate(mimeType: String): Int {
            val parsed = rateRegex.find(mimeType)?.groupValues?.getOrNull(1)?.toIntOrNull()
            return parsed?.takeIf { it in 8000..96000 } ?: 24000
        }

        fun isAlreadyWav(bytes: ByteArray): Boolean {
            return bytes.size >= 12 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() &&
                bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() &&
                bytes[9] == 'A'.code.toByte() &&
                bytes[10] == 'V'.code.toByte() &&
                bytes[11] == 'E'.code.toByte()
        }

        fun wrapPcmAsWav(
            pcmData: ByteArray,
            sampleRate: Int = 24000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val dataSize = pcmData.size
            val chunkSize = 36 + dataSize

            val out = ByteArrayOutputStream(44 + dataSize)
            fun writeIntLe(value: Int) {
                out.write(value and 0xFF)
                out.write((value ushr 8) and 0xFF)
                out.write((value ushr 16) and 0xFF)
                out.write((value ushr 24) and 0xFF)
            }
            fun writeShortLe(value: Int) {
                out.write(value and 0xFF)
                out.write((value ushr 8) and 0xFF)
            }

            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            writeIntLe(chunkSize)
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            writeIntLe(16) // PCM Subchunk1Size
            writeShortLe(1) // AudioFormat = PCM
            writeShortLe(channels)
            writeIntLe(sampleRate)
            writeIntLe(byteRate)
            writeShortLe(blockAlign)
            writeShortLe(bitsPerSample)
            out.write("data".toByteArray(Charsets.US_ASCII))
            writeIntLe(dataSize)
            out.write(pcmData)
            return out.toByteArray()
        }
    }
}

private class AndroidMediaPlayerBridge : VoiceAudioPlaybackBridge {
    @Volatile
    private var currentPlayer: MediaPlayer? = null

    override suspend fun playFile(file: File): Boolean = withContext(Dispatchers.Main.immediate) {
        if (!file.exists() || file.length() <= 0L) return@withContext false
        suspendCancellableCoroutine { cont ->
            val player = MediaPlayer()
            currentPlayer = player
            fun cleanup() {
                if (currentPlayer === player) {
                    currentPlayer = null
                }
                runCatching {
                    if (player.isPlaying) player.stop()
                }
                runCatching { player.release() }
            }
            cont.invokeOnCancellation { cleanup() }
            try {
                player.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                player.setDataSource(file.absolutePath)
                player.setOnCompletionListener {
                    cleanup()
                    if (cont.isActive) cont.resume(true)
                }
                player.setOnErrorListener { _, _, _ ->
                    cleanup()
                    if (cont.isActive) cont.resume(false)
                    true
                }
                player.setOnPreparedListener { mp ->
                    if (cont.isActive) {
                        mp.start()
                    } else {
                        cleanup()
                    }
                }
                player.prepareAsync()
            } catch (_: Exception) {
                cleanup()
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    override fun stop() {
        val player = currentPlayer ?: return
        currentPlayer = null
        runCatching {
            if (player.isPlaying) player.stop()
        }
        runCatching { player.release() }
    }
}

class VoiceEngine(
    private val context: Context,
    private val geminiKeyStore: GeminiKeyStore = GeminiKeyStore(context),
    private val geminiRestClient: GeminiRestClient = GeminiRestClient(),
    private val audioPlayerOverride: VoiceAudioPlaybackBridge? = null,
    private val deviceTtsOverride: DeviceTtsPlaybackBridge? = null
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val audioCache = GeminiTtsAudioCache(context.cacheDir)
    private val audioPlayer: VoiceAudioPlaybackBridge = audioPlayerOverride ?: AndroidMediaPlayerBridge()

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val pendingUtterances = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private var focusRequest: AudioFocusRequest? = null

    private var speakJob: Job? = null
    private val playbackGeneration = AtomicLong(0L)

    var selectedDeviceTtsEnginePackage: String? = null
        private set

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

    private val _activeVoiceIndicator = MutableStateFlow(computeConfiguredIndicator())
    val activeVoiceIndicator: StateFlow<String> = _activeVoiceIndicator.asStateFlow()

    private val _ttsStatusNotice = MutableStateFlow<String?>(null)
    val ttsStatusNotice: StateFlow<String?> = _ttsStatusNotice.asStateFlow()

    private var lastSpokenDigest: Int = 0
    private var lastSpokenAtMs: Long = 0L

    init {
        if (deviceTtsOverride == null) {
            initDeviceTts(preferGoogleEngine = true)
        }
    }

    fun clearTtsStatusNotice() {
        _ttsStatusNotice.value = null
    }

    fun refreshConfiguredVoiceIndicator() {
        _activeVoiceIndicator.value = computeConfiguredIndicator()
    }

    private fun computeConfiguredIndicator(): String {
        val mode = geminiKeyStore.getVoiceEngineMode()
        return if (mode == VoiceEngineMode.DEVICE_ONLY) {
            FALLBACK_DEVICE_LABEL
        } else {
            formatGeminiIndicator(geminiKeyStore.getSelectedVoice())
        }
    }

    fun setContinuousConversation(enabled: Boolean) {
        _continuousConversation.value = enabled
        log("Continuous voice conversation: ${if (enabled) "ON" else "OFF"}")
    }

    private fun initDeviceTts(preferGoogleEngine: Boolean) {
        val googleInstalled = preferGoogleEngine && isPackageInstalled(GOOGLE_TTS_PACKAGE)
        selectedDeviceTtsEnginePackage = if (googleInstalled) GOOGLE_TTS_PACKAGE else null

        runCatching {
            val listener = TextToSpeech.OnInitListener { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ttsReady = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _orbState.value = VoiceOrbState.SPEAKING
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != null) {
                                pendingUtterances.remove(utteranceId)?.complete(true)
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId != null) {
                                pendingUtterances.remove(utteranceId)?.complete(false)
                            }
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId != null) {
                                pendingUtterances.remove(utteranceId)?.complete(false)
                            }
                        }
                    })
                    log("Device TTS initialized (engine=${selectedDeviceTtsEnginePackage ?: "system-default"}, rate=1.0, pitch=1.0).")
                } else if (googleInstalled) {
                    log("Google TTS failed to init (status=$status); falling back to system default TTS engine.")
                    runCatching { tts?.shutdown() }
                    tts = null
                    ttsReady = false
                    initDeviceTts(preferGoogleEngine = false)
                } else {
                    ttsReady = false
                    log("Device TTS initialization failed (status=$status).")
                }
            }

            tts = if (googleInstalled) {
                TextToSpeech(context.applicationContext, listener, GOOGLE_TTS_PACKAGE)
            } else {
                TextToSpeech(context.applicationContext, listener)
            }
        }.onFailure {
            log("Device TTS unavailable in this environment: ${it.localizedMessage}")
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
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

    fun speak(
        text: String,
        voicePresetName: String = geminiKeyStore.getSelectedVoice(),
        language: AuraLanguage = AuraLanguage.ENGLISH
    ) {
        val clean = TtsTextChunker.normalizeForSpeech(text)
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

        val chunks = TtsTextChunker.splitIntoSentences(clean, TtsTextChunker.MAX_CHUNK_CHARS)
        if (chunks.isEmpty()) return

        speakJob?.cancel()
        val generation = playbackGeneration.incrementAndGet()
        val resolvedVoice = voicePresetName.trim().ifBlank { geminiKeyStore.getSelectedVoice() }

        speakJob = scope.launch {
            processSpeechQueue(
                chunks = chunks,
                voiceName = resolvedVoice,
                language = language,
                generation = generation
            )
        }
    }

    internal suspend fun speakQueuedAndAwait(
        text: String,
        voicePresetName: String = geminiKeyStore.getSelectedVoice(),
        language: AuraLanguage = AuraLanguage.ENGLISH
    ) {
        val chunks = TtsTextChunker.splitIntoSentences(text, TtsTextChunker.MAX_CHUNK_CHARS)
        if (chunks.isEmpty()) return
        val generation = playbackGeneration.incrementAndGet()
        val resolvedVoice = voicePresetName.trim().ifBlank { geminiKeyStore.getSelectedVoice() }
        processSpeechQueue(
            chunks = chunks,
            voiceName = resolvedVoice,
            language = language,
            generation = generation
        )
    }

    private suspend fun processSpeechQueue(
        chunks: List<String>,
        voiceName: String,
        language: AuraLanguage,
        generation: Long
    ) {
        requestAudioFocus()
        _orbState.value = VoiceOrbState.SPEAKING

        try {
            val engineMode = geminiKeyStore.getVoiceEngineMode()
            var ttsModel = geminiKeyStore.getSelectedTtsModel().ifBlank { GEMINI_TTS_DEFAULT_MODEL }

            for ((idx, chunk) in chunks.withIndex()) {
                if (playbackGeneration.get() != generation || !scope.isActive) break

                if (engineMode == VoiceEngineMode.DEVICE_ONLY) {
                    _activeVoiceIndicator.value = FALLBACK_DEVICE_LABEL
                    speakChunkWithDeviceFallback(chunk, language, generation)
                    continue
                }

                // 1. Check audio disk cache in app cache folder first (zero API calls)
                val cachedFile = audioCache.findCachedFile(voiceName, chunk)
                if (cachedFile != null) {
                    _activeVoiceIndicator.value = formatGeminiIndicator(voiceName)
                    log("TTS cache hit (${idx + 1}/${chunks.size}) [$voiceName]: ${chunk.take(40)}")
                    val played = audioPlayer.playFile(cachedFile)
                    if (played) continue
                }

                // 2. Call Gemini TTS REST API with 429 multi-key failover and 404 ListModels fallback
                val keys = geminiKeyStore.getDecryptedKeys()
                if (keys.isEmpty()) {
                    _activeVoiceIndicator.value = FALLBACK_DEVICE_LABEL
                    log("No saved Gemini API key; chunk ${idx + 1}/${chunks.size} using device TTS fallback.")
                    speakChunkWithDeviceFallback(chunk, language, generation)
                    continue
                }

                val ttsResult = geminiRestClient.synthesizeSpeechWithFailover(
                    keys = keys,
                    text = chunk,
                    voiceName = voiceName,
                    modelId = ttsModel,
                    onModelError = { errMsg ->
                        _ttsStatusNotice.value = errMsg
                        log("Gemini TTS model error: $errMsg")
                    },
                    onModelFallback = { fallbackModel ->
                        ttsModel = geminiKeyStore.saveSelectedTtsModel(fallbackModel)
                        _ttsStatusNotice.value = "TTS model 404; auto-switched to $ttsModel"
                        log("Auto-switched Gemini TTS model to $ttsModel via ListModels")
                    }
                )

                if (playbackGeneration.get() != generation || !scope.isActive) break

                if (ttsResult.isSuccess) {
                    val audio = ttsResult.getOrThrow()
                    val file = audioCache.putAudio(voiceName, chunk, audio.audioBytes, audio.mimeType)
                    _activeVoiceIndicator.value = formatGeminiIndicator(voiceName)
                    log("Speaking Gemini ($voiceName, ${audio.modelUsed}) chunk ${idx + 1}/${chunks.size} via Key #${audio.keyIndexUsed + 1}")
                    val played = audioPlayer.playFile(file)
                    if (!played && playbackGeneration.get() == generation) {
                        _activeVoiceIndicator.value = FALLBACK_DEVICE_LABEL
                        log("Audio playback failed for cached Gemini file; using device TTS fallback.")
                        speakChunkWithDeviceFallback(chunk, language, generation)
                    }
                } else {
                    val err = ttsResult.exceptionOrNull()?.message ?: "Gemini TTS failed"
                    _activeVoiceIndicator.value = FALLBACK_DEVICE_LABEL
                    log("Gemini TTS failed on chunk ${idx + 1}/${chunks.size} ($err); falling back to device TTS.")
                    speakChunkWithDeviceFallback(chunk, language, generation)
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            abandonAudioFocus()
            if (playbackGeneration.get() == generation && _orbState.value == VoiceOrbState.SPEAKING) {
                _orbState.value = VoiceOrbState.IDLE
            }
        }
    }

    private suspend fun speakChunkWithDeviceFallback(
        chunk: String,
        language: AuraLanguage,
        generation: Long
    ) {
        if (playbackGeneration.get() != generation) return
        val utteranceId = UUID.randomUUID().toString()
        if (deviceTtsOverride != null) {
            deviceTtsOverride.speakChunk(chunk, language, utteranceId)
            return
        }
        val engine = tts
        if (!ttsReady || engine == null) {
            log("Device TTS not ready; skipped chunk.")
            return
        }
        val done = CompletableDeferred<Boolean>()
        pendingUtterances[utteranceId] = done
        try {
            val locale = if (language == AuraLanguage.URDU) Locale.forLanguageTag("ur-PK") else Locale.US
            engine.language = locale
            engine.setPitch(DEVICE_FALLBACK_PITCH)
            engine.setSpeechRate(DEVICE_FALLBACK_RATE)
            val result = engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                pendingUtterances.remove(utteranceId)
                return
            }
            withTimeoutOrNull(20_000L) {
                done.await()
            }
        } finally {
            pendingUtterances.remove(utteranceId)
        }
    }

    fun stopSpeaking() {
        playbackGeneration.incrementAndGet()
        speakJob?.cancel()
        speakJob = null
        audioPlayer.stop()
        deviceTtsOverride?.stop()
        runCatching { tts?.stop() }
        pendingUtterances.values.forEach { it.complete(false) }
        pendingUtterances.clear()
        abandonAudioFocus()
        if (_orbState.value == VoiceOrbState.SPEAKING) {
            _orbState.value = VoiceOrbState.IDLE
        }
    }

    fun emergencyStopAll() {
        _continuousConversation.value = false
        runCatching { speechRecognizer?.cancel() }
        stopSpeaking()
        _rmsLevel.value = 0f
        _orbState.value = VoiceOrbState.STOPPED
        log("Emergency STOP invoked on voice pipeline.")
    }

    fun setOrbState(state: VoiceOrbState) {
        _orbState.value = state
    }

    fun release() {
        stopSpeaking()
        scope.cancel()
        runCatching { speechRecognizer?.destroy() }
        deviceTtsOverride?.release()
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
        const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
        const val DEVICE_FALLBACK_RATE = 1.0f
        const val DEVICE_FALLBACK_PITCH = 1.0f
        const val FALLBACK_DEVICE_LABEL = "Voice: Fallback (device)"

        fun formatGeminiIndicator(voiceName: String): String {
            val clean = voiceName.trim().ifBlank { "Kore" }
            return "Voice: Gemini ($clean)"
        }
    }
}
