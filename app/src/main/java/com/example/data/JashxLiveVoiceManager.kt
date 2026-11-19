package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.ChatMessage
import com.example.model.MarketPrediction
import com.example.util.BrandSanitizer
import com.example.util.LocationServiceManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

private const val TAG = "JashxLiveVoiceManager"
private const val INPUT_SAMPLE_RATE = 16000
private const val MIC_CHUNK_BYTES = 1280 // 40ms of 16kHz 16-bit mono PCM

enum class LiveVoiceStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class JashxVoicePersona(
    val voiceId: String,
    val displayName: String,
    val subtitle: String,
    val pitch: Float,
    val speechRate: Float,
    val preferFemaleVoice: Boolean
) {
    KORE("Kore", "Kavya", "Warm Studio", 1.05f, 1.00f, true),
    AOEDE("Aoede", "Meera", "Natural Clear", 1.12f, 1.03f, true),
    FENRIR("Fenrir", "Arjun", "Bold Analyst", 0.90f, 1.04f, false),
    CHARON("Charon", "Vikram", "Calm Executive", 0.86f, 0.96f, false)
}

data class LiveVoiceUiState(
    val status: LiveVoiceStatus = LiveVoiceStatus.IDLE,
    val selectedPersona: JashxVoicePersona = JashxVoicePersona.KORE,
    val liveUserTranscript: String = "",
    val liveAiTranscript: String = "",
    val activeToolCallLabel: String? = null,
    val errorMessage: String? = null,
    val isLiveFeedAttached: Boolean = true,
    val lastTelemetrySyncTime: String = "",
    val micLevel: Float = 0f,
    val isMuted: Boolean = false,
    val isVoiceModalOpen: Boolean = false,
    val showCaptions: Boolean = true,
    val captureModeLabel: String = "Auto Speech"
)

/**
 * Universal Cross-Device Real-Time Conversational Voice Engine for JASHX AI.
 *
 * Engineered for natural real-time studio voice conversations on every Android phone & emulator:
 * 1. Layer 1: Native Android SpeechRecognizer (streaming partial captions + RMS orb energy).
 * 2. Layer 2: Automatic fallback to direct AudioRecord 16kHz PCM + Smart RMS VAD -> 44-byte WAV
 *    streamed directly into JASHX AI multimodal audio intelligence (`chatWithVoiceAudio`), so devices
 *    lacking a system speech service still recognize voice accurately.
 * 3. Sentence-by-sentence streaming Text-to-Speech with utterance watchdog, crisp natural voice,
 *    mute toggle, live captions, and instant tap-to-interrupt (barge-in).
 *
 * Strictly hides any external brand wording via BrandSanitizer.
 */
class JashxLiveVoiceManager(
    private val scope: CoroutineScope,
    private val application: Application? = null,
    private val onVoiceTurnCompleted: (userText: String, aiText: String) -> Unit = { _, _ -> }
) {
    companion object {
        val LIVE_API_MODELS: List<String> = listOf(
            "gemini-3.1-flash-live-preview",
            "gemini-2.5-flash-native-audio-preview-12-2025"
        )

        /**
         * Wraps 16-bit mono PCM audio in a standard 44-byte RIFF/WAVE header for universal multimodal audio upload.
         */
        fun buildWavByteArray(
            pcmData: ByteArray,
            sampleRate: Int = INPUT_SAMPLE_RATE,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            val totalDataLen = pcmData.size + 36
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val blockAlign = channels * bitsPerSample / 8
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte()
            header[1] = 'I'.code.toByte()
            header[2] = 'F'.code.toByte()
            header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xff).toByte()
            header[5] = ((totalDataLen shr 8) and 0xff).toByte()
            header[6] = ((totalDataLen shr 16) and 0xff).toByte()
            header[7] = ((totalDataLen shr 24) and 0xff).toByte()
            header[8] = 'W'.code.toByte()
            header[9] = 'A'.code.toByte()
            header[10] = 'V'.code.toByte()
            header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte()
            header[13] = 'm'.code.toByte()
            header[14] = 't'.code.toByte()
            header[15] = ' '.code.toByte()
            header[16] = 16 // Subchunk1Size = 16 (PCM)
            header[17] = 0
            header[18] = 0
            header[19] = 0
            header[20] = 1 // AudioFormat = 1 (PCM)
            header[21] = 0
            header[22] = channels.toByte()
            header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xff).toByte()
            header[29] = ((byteRate shr 8) and 0xff).toByte()
            header[30] = ((byteRate shr 16) and 0xff).toByte()
            header[31] = ((byteRate shr 24) and 0xff).toByte()
            header[32] = blockAlign.toByte()
            header[33] = 0
            header[34] = bitsPerSample.toByte()
            header[35] = 0
            header[36] = 'd'.code.toByte()
            header[37] = 'a'.code.toByte()
            header[38] = 't'.code.toByte()
            header[39] = 'a'.code.toByte()
            header[40] = (pcmData.size and 0xff).toByte()
            header[41] = ((pcmData.size shr 8) and 0xff).toByte()
            header[42] = ((pcmData.size shr 16) and 0xff).toByte()
            header[43] = ((pcmData.size shr 24) and 0xff).toByte()

            val out = ByteArray(44 + pcmData.size)
            System.arraycopy(header, 0, out, 0, 44)
            System.arraycopy(pcmData, 0, out, 44, pcmData.size)
            return out
        }
    }

    private val jashxEngine = JashxAiEngine()

    private val _uiState = MutableStateFlow(LiveVoiceUiState())
    val uiState: StateFlow<LiveVoiceUiState> = _uiState.asStateFlow()

    private val latestFeedRef = AtomicReference(VijayBullionFeed())
    private val latestMarketOpenRef = AtomicReference(false)
    private val latestStatusTextRef = AtomicReference("MARKET SYNCING")
    private val latestPredictionRef = AtomicReference<MarketPrediction?>(null)

    private val voiceHistory = mutableListOf<ChatMessage>()
    private var voiceLoopJob: Job? = null
    private var activeTurnJob: Job? = null

    private val isSessionActive = AtomicBoolean(false)
    private val preferDirectAudioMode = AtomicBoolean(false)
    private val finishCurrentRecordingSignal = AtomicBoolean(false)
    private val interruptRequestedSignal = AtomicBoolean(false)

    @Volatile
    private var activeSpeechRecognizer: SpeechRecognizer? = null

    @Volatile
    private var ttsEngine: TextToSpeech? = null

    @Volatile
    private var isTtsReady = false

    private val utteranceWaiters = ConcurrentHashMap<String, CompletableDeferred<Unit>>()

    fun updateLiveTelemetry(
        feed: VijayBullionFeed,
        isMarketOpen: Boolean,
        marketStatusText: String,
        prediction: MarketPrediction?
    ) {
        val safeFeed = jashxEngine.sanitizeAndCorrectFeed(feed)
        latestFeedRef.set(safeFeed)
        latestMarketOpenRef.set(isMarketOpen)
        latestStatusTextRef.set(marketStatusText)
        latestPredictionRef.set(prediction)
        val (istTime, _) = JashxKnowledgeDatabase.getIstTimeAndDate()
        _uiState.update {
            it.copy(
                isLiveFeedAttached = true,
                lastTelemetrySyncTime = istTime
            )
        }
    }

    fun selectPersona(persona: JashxVoicePersona) {
        _uiState.update { it.copy(selectedPersona = persona, errorMessage = null) }
        applyPersonaToTts(persona)
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update {
            it.copy(
                isMuted = newMuted,
                micLevel = if (newMuted) 0f else it.micLevel
            )
        }
        if (newMuted) {
            scope.launch(Dispatchers.Main) {
                runCatching { activeSpeechRecognizer?.cancel() }
            }
        }
    }

    fun toggleCaptions() {
        _uiState.update { it.copy(showCaptions = !it.showCaptions) }
    }

    fun toggleCaptureMode() {
        val nextDirect = !preferDirectAudioMode.get()
        preferDirectAudioMode.set(nextDirect)
        _uiState.update {
            it.copy(captureModeLabel = if (nextDirect) "Direct Audio" else "Auto Speech")
        }
        // Cancel current recognizer cleanly so loop switches immediately on next pass
        scope.launch(Dispatchers.Main) {
            runCatching { activeSpeechRecognizer?.cancel() }
        }
        finishCurrentRecordingSignal.set(true)
    }

    fun openVoiceModal() {
        _uiState.update { it.copy(isVoiceModalOpen = true) }
        val status = _uiState.value.status
        if (status == LiveVoiceStatus.IDLE || status == LiveVoiceStatus.ERROR) {
            startVoiceSession(openModal = true)
        }
    }

    fun closeVoiceModal() {
        stopVoiceSession()
        _uiState.update { it.copy(isVoiceModalOpen = false) }
    }

    fun interruptAndListen() {
        if (!isSessionActive.get()) {
            startVoiceSession(openModal = _uiState.value.isVoiceModalOpen)
            return
        }
        interruptRequestedSignal.set(true)
        finishCurrentRecordingSignal.set(true)
        stopTtsPlayback()
        activeTurnJob?.cancel()
        _uiState.update {
            it.copy(
                status = LiveVoiceStatus.LISTENING,
                activeToolCallLabel = null,
                isMuted = false,
                micLevel = 0f
            )
        }
    }

    fun submitDirectAudioNow() {
        finishCurrentRecordingSignal.set(true)
    }

    fun clearError() {
        _uiState.update {
            if (it.status == LiveVoiceStatus.ERROR) {
                it.copy(status = LiveVoiceStatus.IDLE, errorMessage = null)
            } else {
                it.copy(errorMessage = null)
            }
        }
    }

    fun startVoiceSession(openModal: Boolean = true) {
        val currentStatus = _uiState.value.status
        if (currentStatus == LiveVoiceStatus.CONNECTING ||
            currentStatus == LiveVoiceStatus.LISTENING ||
            currentStatus == LiveVoiceStatus.THINKING ||
            currentStatus == LiveVoiceStatus.SPEAKING
        ) {
            if (openModal) {
                _uiState.update { it.copy(isVoiceModalOpen = true) }
            }
            return
        }

        val appCtx = application
        if (appCtx != null && ContextCompat.checkSelfPermission(
                appCtx,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            _uiState.update {
                it.copy(
                    status = LiveVoiceStatus.ERROR,
                    micLevel = 0f,
                    isVoiceModalOpen = openModal,
                    errorMessage = "Microphone permission is required for JASHX AI real-time voice."
                )
            }
            return
        }

        voiceLoopJob?.cancel()
        activeTurnJob?.cancel()
        isSessionActive.set(true)
        interruptRequestedSignal.set(false)
        finishCurrentRecordingSignal.set(false)

        val speechAvailable = appCtx != null && runCatching {
            SpeechRecognizer.isRecognitionAvailable(appCtx)
        }.getOrDefault(false)
        if (!speechAvailable) {
            preferDirectAudioMode.set(true)
        }

        _uiState.update {
            it.copy(
                status = LiveVoiceStatus.CONNECTING,
                liveUserTranscript = "",
                liveAiTranscript = "",
                activeToolCallLabel = "Initializing Voice Channel...",
                errorMessage = null,
                micLevel = 0f,
                isMuted = false,
                isVoiceModalOpen = openModal,
                captureModeLabel = if (preferDirectAudioMode.get()) "Direct Audio" else "Auto Speech"
            )
        }

        voiceLoopJob = scope.launch(Dispatchers.IO) {
            try {
                // Warm live trending headlines & TTS engine simultaneously
                launch(Dispatchers.IO) {
                    runCatching { JashxKnowledgeDatabase.refreshAndGetLiveHeadlines(limitPerTopic = 2) }
                }
                ensureTtsInitialized()

                // Speak warm intro greeting (user can interrupt anytime)
                val introGreeting =
                    "Vanakkam! I am JASHX AI, founded by K.M. Jashwanth, synced with your live 1-second market feed. Ask me anything!"
                _uiState.update {
                    it.copy(
                        status = LiveVoiceStatus.SPEAKING,
                        liveAiTranscript = introGreeting,
                        activeToolCallLabel = null
                    )
                }
                speakTextSuspend(introGreeting)

                // Continuous Real-Time Studio Voice Conversation Loop
                while (isActive && isSessionActive.get()) {
                    interruptRequestedSignal.set(false)
                    finishCurrentRecordingSignal.set(false)

                    if (_uiState.value.isMuted) {
                        _uiState.update {
                            it.copy(
                                status = LiveVoiceStatus.LISTENING,
                                micLevel = 0f,
                                activeToolCallLabel = "Microphone Muted"
                            )
                        }
                        delay(250L)
                        continue
                    }

                    _uiState.update {
                        it.copy(
                            status = LiveVoiceStatus.LISTENING,
                            activeToolCallLabel = if (preferDirectAudioMode.get()) "Direct Audio Capture Active" else null,
                            micLevel = 0f
                        )
                    }

                    val captureOutcome = captureUserTurn()
                    if (!isActive || !isSessionActive.get()) break
                    if (interruptRequestedSignal.getAndSet(false)) continue

                    when (captureOutcome) {
                        is CaptureOutcome.TextTranscript -> {
                            val cleanText = BrandSanitizer.sanitizeForUserDisplay(captureOutcome.text).trim()
                            if (cleanText.isNotBlank()) {
                                processTextTurnAndSpeak(cleanText)
                            }
                        }
                        is CaptureOutcome.RawWavAudio -> {
                            if (captureOutcome.wavBytes.size > 44) {
                                processAudioTurnAndSpeak(captureOutcome.wavBytes)
                            }
                        }
                        is CaptureOutcome.RetryQuietly -> {
                            delay(150L)
                        }
                        is CaptureOutcome.FatalMicError -> {
                            _uiState.update {
                                it.copy(
                                    status = LiveVoiceStatus.ERROR,
                                    micLevel = 0f,
                                    activeToolCallLabel = null,
                                    errorMessage = BrandSanitizer.sanitizeForUserDisplay(captureOutcome.message)
                                )
                            }
                            break
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "Voice session error", t)
                _uiState.update {
                    it.copy(
                        status = LiveVoiceStatus.ERROR,
                        activeToolCallLabel = null,
                        micLevel = 0f,
                        errorMessage = BrandSanitizer.sanitizeForUserDisplay(
                            t.localizedMessage ?: t.message ?: "Voice channel interrupted. Tap to retry."
                        )
                    )
                }
            } finally {
                cleanupCaptureResources()
            }
        }
    }

    fun stopVoiceSession() {
        isSessionActive.set(false)
        interruptRequestedSignal.set(true)
        finishCurrentRecordingSignal.set(true)
        activeTurnJob?.cancel()
        activeTurnJob = null
        voiceLoopJob?.cancel()
        voiceLoopJob = null
        stopTtsPlayback()
        cleanupCaptureResources()
        _uiState.update {
            it.copy(
                status = LiveVoiceStatus.IDLE,
                activeToolCallLabel = null,
                micLevel = 0f,
                isMuted = false
            )
        }
    }

    fun sendVoicePrompt(prompt: String) {
        val clean = BrandSanitizer.sanitizeForUserDisplay(prompt).trim()
        if (clean.isBlank()) return
        if (!isSessionActive.get()) {
            startVoiceSession(openModal = _uiState.value.isVoiceModalOpen)
        }
        scope.launch(Dispatchers.IO) {
            stopTtsPlayback()
            activeTurnJob?.cancel()
            processTextTurnAndSpeak(clean)
        }
    }

    // ==================== LAYER 1 & LAYER 2 SPEECH CAPTURE ====================

    private sealed interface CaptureOutcome {
        data class TextTranscript(val text: String) : CaptureOutcome
        data class RawWavAudio(val wavBytes: ByteArray) : CaptureOutcome
        data object RetryQuietly : CaptureOutcome
        data class FatalMicError(val message: String) : CaptureOutcome
    }

    private suspend fun captureUserTurn(): CaptureOutcome {
        val appCtx = application ?: return CaptureOutcome.FatalMicError("Audio context unavailable.")
        if (!preferDirectAudioMode.get() && runCatching { SpeechRecognizer.isRecognitionAvailable(appCtx) }.getOrDefault(false)) {
            val srOutcome = captureViaSpeechRecognizer(appCtx)
            if (srOutcome != null) {
                return srOutcome
            }
            // Recognizer unavailable/busy/errored -> transparently switch to Direct Audio fallback!
            preferDirectAudioMode.set(true)
            _uiState.update { it.copy(captureModeLabel = "Direct Audio") }
        }
        return captureViaDirectAudioRecord()
    }

    /**
     * Layer 1: System SpeechRecognizer on Main looper.
     * Returns null if system recognizer hits an OEM/service/busy failure so caller falls back to Layer 2 AudioRecord.
     */
    private suspend fun captureViaSpeechRecognizer(appCtx: Application): CaptureOutcome? {
        val resultDeferred = CompletableDeferred<CaptureOutcome?>()
        val readyReceived = AtomicBoolean(false)
        var partialTextSnapshot = ""

        val readyWatchdogJob = scope.launch(Dispatchers.IO) {
            delay(3_500L)
            if (!readyReceived.get() && !resultDeferred.isCompleted) {
                Log.w(TAG, "SpeechRecognizer onReadyForSpeech timed out after 3.5s; switching to Direct Audio")
                resultDeferred.complete(null)
            }
        }

        withContext(Dispatchers.Main) {
            try {
                activeSpeechRecognizer?.destroy()
                val recognizer = SpeechRecognizer.createSpeechRecognizer(appCtx)
                activeSpeechRecognizer = recognizer

                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        readyReceived.set(true)
                        _uiState.update {
                            it.copy(
                                status = LiveVoiceStatus.LISTENING,
                                activeToolCallLabel = "Listening (Tap logo to interrupt or finish)"
                            )
                        }
                    }

                    override fun onBeginningOfSpeech() {
                        readyReceived.set(true)
                        partialTextSnapshot = ""
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val norm = ((rmsdB + 2f) / 12f).coerceIn(0.04f, 1f)
                        _uiState.update { it.copy(micLevel = norm) }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        _uiState.update { it.copy(micLevel = 0.08f) }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()?.trim().orEmpty()
                        if (partial.isNotBlank()) {
                            partialTextSnapshot = partial
                            _uiState.update {
                                it.copy(liveUserTranscript = BrandSanitizer.sanitizeForUserDisplay(partial))
                            }
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val finalText = matches?.firstOrNull()?.trim().orEmpty().ifBlank { partialTextSnapshot }
                        if (finalText.isNotBlank()) {
                            resultDeferred.complete(CaptureOutcome.TextTranscript(finalText))
                        } else {
                            resultDeferred.complete(CaptureOutcome.RetryQuietly)
                        }
                    }

                    override fun onError(error: Int) {
                        when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                                if (partialTextSnapshot.isNotBlank()) {
                                    resultDeferred.complete(CaptureOutcome.TextTranscript(partialTextSnapshot))
                                } else {
                                    resultDeferred.complete(CaptureOutcome.RetryQuietly)
                                }
                            }
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                                resultDeferred.complete(
                                    CaptureOutcome.FatalMicError("Microphone permission is required for JASHX AI live voice.")
                                )
                            }
                            else -> {
                                // Client, server, busy, audio, or OEM offline recognition error -> trigger Layer 2 AudioRecord!
                                Log.w(TAG, "SpeechRecognizer fallback triggered on error code=$error")
                                if (partialTextSnapshot.isNotBlank()) {
                                    resultDeferred.complete(CaptureOutcome.TextTranscript(partialTextSnapshot))
                                } else {
                                    resultDeferred.complete(null)
                                }
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                recognizer.startListening(intent)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to start SpeechRecognizer, switching to Direct Audio: ${t.message}")
                resultDeferred.complete(null)
            }
        }

        return try {
            val outcome = withTimeoutOrNull(18_000L) {
                resultDeferred.await()
            }
            when {
                outcome != null -> outcome
                partialTextSnapshot.isNotBlank() -> CaptureOutcome.TextTranscript(partialTextSnapshot)
                !readyReceived.get() -> null
                else -> CaptureOutcome.RetryQuietly
            }
        } finally {
            readyWatchdogJob.cancel()
            withContext(Dispatchers.Main) {
                runCatching { activeSpeechRecognizer?.destroy() }
                activeSpeechRecognizer = null
            }
        }
    }

    /**
     * Layer 2: Universal Direct AudioRecord Capture (works on 100% of Android ROMs, OEMs, and Emulators).
     * Records 16kHz 16-bit mono PCM with Smart RMS Voice Activity Detection and packages as WAV.
     */
    @SuppressLint("MissingPermission")
    private suspend fun captureViaDirectAudioRecord(): CaptureOutcome {
        val candidateSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        val minBuf = AudioRecord.getMinBufferSize(
            INPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            return CaptureOutcome.FatalMicError("Microphone input format is unsupported on this device.")
        }
        val bufferBytes = maxOf(minBuf * 2, 4096)

        for (source in candidateSources) {
            var recorder: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            try {
                val built = AudioRecord.Builder()
                    .setAudioSource(source)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(INPUT_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .build()

                if (built.state != AudioRecord.STATE_INITIALIZED) {
                    built.release()
                    continue
                }
                recorder = built

                if (AcousticEchoCanceler.isAvailable()) {
                    runCatching {
                        AcousticEchoCanceler.create(built.audioSessionId)?.apply {
                            enabled = true
                            aec = this
                        }
                    }
                }
                if (NoiseSuppressor.isAvailable()) {
                    runCatching {
                        NoiseSuppressor.create(built.audioSessionId)?.apply {
                            enabled = true
                            ns = this
                        }
                    }
                }

                built.startRecording()
                if (built.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    continue
                }

                val pcmBuffer = ByteArrayOutputStream()
                val readBuf = ByteArray(MIC_CHUNK_BYTES)
                val startMs = System.currentTimeMillis()
                var lastSpeechMs = 0L
                var speechDetected = false
                var zeroFrameCount = 0
                var seenNonZero = false
                var lastUiTickMs = 0L
                var switchSource = false

                while (currentCoroutineContext().isActive &&
                    isSessionActive.get() &&
                    !_uiState.value.isMuted &&
                    !interruptRequestedSignal.get()
                ) {
                    if (finishCurrentRecordingSignal.getAndSet(false)) {
                        break
                    }

                    val bytesRead = built.read(readBuf, 0, readBuf.size)
                    if (bytesRead <= 0) {
                        if (bytesRead < 0) switchSource = true
                        break
                    }

                    val (rmsNorm, peakAbs) = computePcmLevel(readBuf, bytesRead)
                    if (peakAbs > 0) {
                        seenNonZero = true
                    } else if (!seenNonZero) {
                        zeroFrameCount++
                        if (zeroFrameCount >= 40) {
                            switchSource = true
                            break
                        }
                    }

                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastUiTickMs >= 70L) {
                        lastUiTickMs = nowMs
                        _uiState.update { it.copy(micLevel = rmsNorm) }
                    }

                    pcmBuffer.write(readBuf, 0, bytesRead)

                    if (rmsNorm > 0.025f) {
                        speechDetected = true
                        lastSpeechMs = nowMs
                    }

                    // Auto-submit ~1.3s after user stops speaking
                    if (speechDetected && (nowMs - lastSpeechMs) >= 1300L && (nowMs - startMs) >= 900L) {
                        break
                    }
                    // Hard safety cap per turn (12 seconds)
                    if ((nowMs - startMs) >= 12_000L) {
                        break
                    }
                    // Idle timeout if no voice activity after 7 seconds
                    if (!speechDetected && (nowMs - startMs) >= 7_000L) {
                        return CaptureOutcome.RetryQuietly
                    }
                }

                if (switchSource) {
                    continue
                }
                if (!speechDetected && pcmBuffer.size() < 3200) {
                    return CaptureOutcome.RetryQuietly
                }
                val wavBytes = buildWavByteArray(pcmBuffer.toByteArray(), INPUT_SAMPLE_RATE)
                return CaptureOutcome.RawWavAudio(wavBytes)
            } catch (se: SecurityException) {
                return CaptureOutcome.FatalMicError("Microphone permission is required for JASHX AI live voice.")
            } catch (t: Throwable) {
                Log.w(TAG, "AudioRecord source $source failed: ${t.message}")
            } finally {
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
            }
        }
        return CaptureOutcome.FatalMicError("No active microphone input could be opened on this device.")
    }

    // ==================== STREAMING AI THINKING + SENTENCE TTS ====================

    private suspend fun processTextTurnAndSpeak(userText: String) {
        coroutineScope {
            val cleanUser = BrandSanitizer.sanitizeForUserDisplay(userText).trim()
            _uiState.update {
                it.copy(
                    status = LiveVoiceStatus.THINKING,
                    liveUserTranscript = cleanUser,
                    liveAiTranscript = "",
                    activeToolCallLabel = "JASHX AI is thinking...",
                    micLevel = 0f
                )
            }

            val feed = latestFeedRef.get()
            val fullReply = StringBuilder()
            var spokenOffset = 0
            var ttsStarted = false

            val job = launch {
                jashxEngine.chatWithJashxAi(
                    history = voiceHistory.toList(),
                    userMessage = cleanUser,
                    feed = feed,
                    forVoice = true,
                    isMarketOpen = latestMarketOpenRef.get(),
                    marketStatusText = latestStatusTextRef.get()
                ).collect { streamed ->
                    val sanitized = BrandSanitizer.sanitizeForUserDisplay(streamed).trim()
                    if (sanitized.isNotBlank()) {
                        fullReply.setLength(0)
                        fullReply.append(sanitized)
                        _uiState.update {
                            it.copy(
                                status = if (ttsStarted) LiveVoiceStatus.SPEAKING else LiveVoiceStatus.THINKING,
                                liveAiTranscript = sanitized,
                                activeToolCallLabel = null
                            )
                        }

                        // Stream completed sentences to TTS immediately so first voice starts fast
                        while (spokenOffset < sanitized.length) {
                            val remaining = sanitized.substring(spokenOffset)
                            val boundaryIdx = findSentenceBoundary(remaining)
                            if (boundaryIdx >= 0) {
                                val sentence = remaining.substring(0, boundaryIdx + 1).trim()
                                spokenOffset += boundaryIdx + 1
                                if (sentence.isNotBlank()) {
                                    ttsStarted = true
                                    _uiState.update { it.copy(status = LiveVoiceStatus.SPEAKING) }
                                    queueTtsSentence(sentence)
                                }
                            } else {
                                break
                            }
                        }
                    }
                }
            }
            activeTurnJob = job
            job.join()

            var finalText = BrandSanitizer.sanitizeForUserDisplay(fullReply.toString()).trim()
            if (finalText.isBlank()) {
                val fallbackAnswer = BrandSanitizer.sanitizeForUserDisplay(
                    JashxKnowledgeDatabase.queryKnowledgeBaseAsync(cleanUser, feed)
                ).trim()
                if (fallbackAnswer.isNotBlank()) {
                    finalText = fallbackAnswer
                    _uiState.update {
                        it.copy(
                            status = LiveVoiceStatus.SPEAKING,
                            liveAiTranscript = fallbackAnswer,
                            activeToolCallLabel = null
                        )
                    }
                    speakTextSuspend(fallbackAnswer)
                }
            } else if (spokenOffset < finalText.length) {
                val tail = finalText.substring(spokenOffset).trim()
                if (tail.isNotBlank()) {
                    _uiState.update { it.copy(status = LiveVoiceStatus.SPEAKING) }
                    speakTextSuspend(tail)
                } else {
                    awaitAllQueuedUtterances()
                }
            } else {
                awaitAllQueuedUtterances()
            }

            if (finalText.isNotBlank()) {
                recordTurnHistory(cleanUser, finalText)
            }
        }
    }

    private suspend fun processAudioTurnAndSpeak(wavBytes: ByteArray) {
        coroutineScope {
            _uiState.update {
                it.copy(
                    status = LiveVoiceStatus.THINKING,
                    activeToolCallLabel = "Understanding voice audio...",
                    micLevel = 0f
                )
            }

            val feed = latestFeedRef.get()
            var latestUserText = ""
            var latestAiText = ""
            var spokenOffset = 0
            var isUnclear = false

            val job = launch {
                jashxEngine.chatWithVoiceAudio(
                    history = voiceHistory.toList(),
                    wavAudioBytes = wavBytes,
                    feed = feed,
                    isMarketOpen = latestMarketOpenRef.get(),
                    marketStatusText = latestStatusTextRef.get()
                ).collect { chunk ->
                    if (chunk.userTranscript.isNotBlank()) {
                        latestUserText = BrandSanitizer.sanitizeForUserDisplay(chunk.userTranscript).trim()
                        _uiState.update { it.copy(liveUserTranscript = latestUserText) }
                    }
                    val aiReply = BrandSanitizer.sanitizeForUserDisplay(chunk.aiReply).trim()
                    if (aiReply.equals("UNCLEAR_AUDIO", ignoreCase = true)) {
                        isUnclear = true
                        return@collect
                    }
                    if (aiReply.isNotBlank()) {
                        latestAiText = aiReply
                        _uiState.update {
                            it.copy(
                                status = if (spokenOffset > 0) LiveVoiceStatus.SPEAKING else LiveVoiceStatus.THINKING,
                                liveAiTranscript = aiReply,
                                activeToolCallLabel = null
                            )
                        }
                        while (spokenOffset < aiReply.length) {
                            val remaining = aiReply.substring(spokenOffset)
                            val boundaryIdx = findSentenceBoundary(remaining)
                            if (boundaryIdx >= 0) {
                                val sentence = remaining.substring(0, boundaryIdx + 1).trim()
                                spokenOffset += boundaryIdx + 1
                                if (sentence.isNotBlank()) {
                                    _uiState.update { it.copy(status = LiveVoiceStatus.SPEAKING) }
                                    queueTtsSentence(sentence)
                                }
                            } else {
                                break
                            }
                        }
                    }
                }
            }
            activeTurnJob = job
            job.join()

            if (isUnclear) {
                return@coroutineScope
            }

            if (latestAiText.isBlank() && latestUserText.isNotBlank()) {
                val fallbackAnswer = BrandSanitizer.sanitizeForUserDisplay(
                    JashxKnowledgeDatabase.queryKnowledgeBaseAsync(latestUserText, feed)
                ).trim()
                if (fallbackAnswer.isNotBlank()) {
                    latestAiText = fallbackAnswer
                    _uiState.update {
                        it.copy(
                            status = LiveVoiceStatus.SPEAKING,
                            liveAiTranscript = fallbackAnswer,
                            activeToolCallLabel = null
                        )
                    }
                    speakTextSuspend(fallbackAnswer)
                }
            } else if (latestAiText.isNotBlank() && spokenOffset < latestAiText.length) {
                val tail = latestAiText.substring(spokenOffset).trim()
                if (tail.isNotBlank()) {
                    _uiState.update { it.copy(status = LiveVoiceStatus.SPEAKING) }
                    speakTextSuspend(tail)
                } else {
                    awaitAllQueuedUtterances()
                }
            } else {
                awaitAllQueuedUtterances()
            }

            if (latestUserText.isNotBlank() || latestAiText.isNotBlank()) {
                recordTurnHistory(latestUserText, latestAiText)
            }
        }
    }

    private fun recordTurnHistory(userText: String, aiText: String) {
        val cleanUser = BrandSanitizer.sanitizeForUserDisplay(userText).trim()
        val cleanAi = BrandSanitizer.sanitizeForUserDisplay(aiText).trim()
        if (cleanUser.isBlank() || cleanAi.isBlank()) {
            onVoiceTurnCompleted(cleanUser, cleanAi)
            return
        }

        synchronized(voiceHistory) {
            voiceHistory.add(ChatMessage(text = cleanUser, isFromUser = true))
            voiceHistory.add(ChatMessage(text = cleanAi, isFromUser = false))
            while (voiceHistory.size > 12) {
                voiceHistory.removeAt(0)
            }
            while (voiceHistory.firstOrNull()?.isFromUser == false) {
                voiceHistory.removeAt(0)
            }
        }
        onVoiceTurnCompleted(cleanUser, cleanAi)
    }

    // ==================== TEXT-TO-SPEECH WITH PERSONA & WATCHDOG ====================

    private suspend fun ensureTtsInitialized() {
        if (isTtsReady && ttsEngine != null) return
        val appCtx = application ?: return
        val readyDeferred = CompletableDeferred<Boolean>()
        withContext(Dispatchers.Main) {
            try {
                if (ttsEngine != null && isTtsReady) {
                    readyDeferred.complete(true)
                    return@withContext
                }
                var created: TextToSpeech? = null
                created = TextToSpeech(appCtx) { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        val engine = created
                        if (engine != null) {
                            val res = engine.setLanguage(Locale("en", "IN"))
                            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                                engine.setLanguage(Locale.US)
                            }
                            engine.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(utteranceId: String?) = Unit
                                override fun onDone(utteranceId: String?) {
                                    if (utteranceId != null) {
                                        utteranceWaiters.remove(utteranceId)?.complete(Unit)
                                    }
                                }

                                @Deprecated("Deprecated in Java")
                                override fun onError(utteranceId: String?) {
                                    if (utteranceId != null) {
                                        utteranceWaiters.remove(utteranceId)?.complete(Unit)
                                    }
                                }

                                override fun onError(utteranceId: String?, errorCode: Int) {
                                    if (utteranceId != null) {
                                        utteranceWaiters.remove(utteranceId)?.complete(Unit)
                                    }
                                }
                            })
                            ttsEngine = engine
                            isTtsReady = true
                            applyPersonaToTts(_uiState.value.selectedPersona)
                        }
                        readyDeferred.complete(true)
                    } else {
                        readyDeferred.complete(false)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "TTS init skipped: ${t.message}")
                readyDeferred.complete(false)
            }
        }
        withTimeoutOrNull(3_000L) { readyDeferred.await() }
    }

    private fun applyPersonaToTts(@Suppress("UNUSED_PARAMETER") persona: JashxVoicePersona) {
        val engine = ttsEngine ?: return
        scope.launch(Dispatchers.Main) {
            runCatching {
                // Single crisp natural studio voice (voice-changer removed)
                engine.setPitch(1.0f)
                engine.setSpeechRate(1.02f)
                val voices = engine.voices?.toList().orEmpty()
                val preferredLocale = Locale("en", "IN")
                val matchingVoice = voices.firstOrNull { v ->
                    v.locale == preferredLocale && !v.isNetworkConnectionRequired
                } ?: voices.firstOrNull { v ->
                    v.locale.language == "en" && !v.isNetworkConnectionRequired
                }
                if (matchingVoice != null) {
                    engine.voice = matchingVoice
                }
            }
        }
    }

    private suspend fun queueTtsSentence(rawText: String) {
        val speechText = JashxKnowledgeDatabase.toNaturalSpeechText(
            BrandSanitizer.sanitizeForUserDisplay(rawText)
        ).trim()
        if (speechText.isBlank()) return
        val engine = ttsEngine ?: return
        if (!isTtsReady) return
        val utteranceId = "jashx_utt_${UUID.randomUUID()}"
        val deferred = CompletableDeferred<Unit>()
        utteranceWaiters[utteranceId] = deferred
        val queuedResult = withContext(Dispatchers.Main) {
            runCatching {
                engine.speak(speechText, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }.getOrDefault(TextToSpeech.ERROR)
        }
        if (queuedResult != TextToSpeech.SUCCESS) {
            utteranceWaiters.remove(utteranceId)?.complete(Unit)
        }
    }

    private suspend fun speakTextSuspend(rawText: String) {
        val speechText = JashxKnowledgeDatabase.toNaturalSpeechText(
            BrandSanitizer.sanitizeForUserDisplay(rawText)
        ).trim()
        if (speechText.isBlank()) return
        val engine = ttsEngine
        if (engine == null || !isTtsReady) {
            // Even if TTS fails on an unsupported headless device, briefly display captions then return cleanly
            delay(1500L)
            return
        }
        val utteranceId = "jashx_utt_${UUID.randomUUID()}"
        val deferred = CompletableDeferred<Unit>()
        utteranceWaiters[utteranceId] = deferred
        val queuedResult = withContext(Dispatchers.Main) {
            runCatching {
                engine.speak(speechText, TextToSpeech.QUEUE_ADD, null, utteranceId)
            }.getOrDefault(TextToSpeech.ERROR)
        }
        if (queuedResult != TextToSpeech.SUCCESS) {
            utteranceWaiters.remove(utteranceId)?.complete(Unit)
            return
        }
        val safetyMs = ((speechText.length * 85L).coerceIn(2500L, 28_000L))
        withTimeoutOrNull(safetyMs) {
            deferred.await()
        }
        utteranceWaiters.remove(utteranceId)
    }

    private suspend fun awaitAllQueuedUtterances() {
        val pending = utteranceWaiters.entries.toList()
        if (pending.isEmpty()) return
        withTimeoutOrNull(25_000L) {
            pending.forEach { (id, def) ->
                withTimeoutOrNull(12_000L) { def.await() }
                utteranceWaiters.remove(id)
            }
        }
    }

    private fun stopTtsPlayback() {
        utteranceWaiters.values.forEach { it.complete(Unit) }
        utteranceWaiters.clear()
        val engine = ttsEngine
        if (engine != null) {
            scope.launch(Dispatchers.Main) {
                runCatching { engine.stop() }
            }
        }
    }

    private fun cleanupCaptureResources() {
        scope.launch(Dispatchers.Main) {
            runCatching { activeSpeechRecognizer?.cancel() }
            runCatching { activeSpeechRecognizer?.destroy() }
            activeSpeechRecognizer = null
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            if ((ch == '.' || ch == '!' || ch == '?' || ch == '\n') && i >= 12) {
                val nextChar = text.getOrNull(i + 1)
                // Avoid splitting inside numbers like ₹1,46,000.50 or $52.01
                val prevIsDigit = i > 0 && text[i - 1].isDigit()
                val nextIsDigit = nextChar != null && nextChar.isDigit()
                if (ch == '.' && prevIsDigit && nextIsDigit) continue
                return i
            }
        }
        return -1
    }

    private fun computePcmLevel(pcm: ByteArray, length: Int): Pair<Float, Int> {
        val sampleCount = length / 2
        if (sampleCount <= 0) return 0f to 0
        var sumSq = 0.0
        var peakAbs = 0
        var i = 0
        while (i + 1 < length) {
            val low = pcm[i].toInt() and 0xFF
            val high = pcm[i + 1].toInt()
            val sample = (high shl 8) or low
            val abs = if (sample < 0) -sample else sample
            if (abs > peakAbs) peakAbs = abs
            sumSq += sample.toDouble() * sample.toDouble()
            i += 2
        }
        val rms = sqrt(sumSq / sampleCount)
        val normalized = (rms / 6000.0).toFloat().coerceIn(0f, 1f)
        return normalized to peakAbs
    }
}
