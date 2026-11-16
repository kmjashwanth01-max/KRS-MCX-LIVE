@file:OptIn(PublicPreviewAPI::class)

package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.model.MarketPrediction
import com.example.util.BrandSanitizer
import com.example.util.LocationServiceManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.ActivityDetectionConfig
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerSetupComplete
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.RealtimeInputConfig
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import com.google.firebase.ai.type.realtimeInputConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.sqrt

private const val TAG = "JashxLiveVoiceManager"
private const val INPUT_SAMPLE_RATE = 16000
private const val OUTPUT_SAMPLE_RATE = 24000
private const val MIC_CHUNK_BYTES = 1280 // 40ms of 16kHz, 16-bit mono PCM

enum class LiveVoiceStatus {
    IDLE,
    CONNECTING,
    LISTENING,
    SPEAKING,
    ERROR
}

enum class JashxVoicePersona(
    val voiceId: String,
    val displayName: String,
    val subtitle: String
) {
    KORE("Kore", "Kavya", "Warm Studio"),
    AOEDE("Aoede", "Meera", "Natural Clear"),
    FENRIR("Fenrir", "Arjun", "Bold Analyst"),
    CHARON("Charon", "Vikram", "Calm Executive")
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
    val micLevel: Float = 0f
)

/**
 * Bidirectional Real-Time Audio Voice Engine for JASHX AI.
 * Directly manages low-latency AudioRecord (VOICE_RECOGNITION -> MIC fallback) + AudioTrack (24kHz)
 * with Firebase AI Logic LiveSession so user speech is captured reliably across all Android OEMs,
 * turnComplete transitions back to LISTENING cleanly, and interruptions flush playback immediately.
 *
 * Strictly hides any third-party brand wording via BrandSanitizer.
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
    }

    private val jashxEngine = JashxAiEngine()

    private val _uiState = MutableStateFlow(LiveVoiceUiState())
    val uiState: StateFlow<LiveVoiceUiState> = _uiState.asStateFlow()

    private val latestFeedRef = AtomicReference(VijayBullionFeed())
    private val latestMarketOpenRef = AtomicReference(false)
    private val latestStatusTextRef = AtomicReference("MARKET SYNCING")
    private val latestPredictionRef = AtomicReference<MarketPrediction?>(null)

    private var activeSession: LiveSession? = null
    private var voiceJob: Job? = null
    private val sendMutex = Mutex()

    private val userTurnBuffer = StringBuilder()
    private val aiTurnBuffer = StringBuilder()

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
        val wasActive = _uiState.value.status == LiveVoiceStatus.LISTENING ||
            _uiState.value.status == LiveVoiceStatus.SPEAKING ||
            _uiState.value.status == LiveVoiceStatus.CONNECTING
        _uiState.update { it.copy(selectedPersona = persona, errorMessage = null) }
        if (wasActive) {
            stopVoiceSession()
            startVoiceSession()
        }
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

    fun startVoiceSession() {
        val currentStatus = _uiState.value.status
        if (currentStatus == LiveVoiceStatus.CONNECTING ||
            currentStatus == LiveVoiceStatus.LISTENING ||
            currentStatus == LiveVoiceStatus.SPEAKING
        ) {
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
                    errorMessage = "Microphone permission is required for JASHX AI real-time voice."
                )
            }
            return
        }

        voiceJob?.cancel()
        synchronized(this) {
            userTurnBuffer.clear()
            aiTurnBuffer.clear()
        }

        val persona = _uiState.value.selectedPersona
        _uiState.update {
            it.copy(
                status = LiveVoiceStatus.CONNECTING,
                liveUserTranscript = "",
                liveAiTranscript = "",
                activeToolCallLabel = null,
                errorMessage = null,
                micLevel = 0f
            )
        }

        voiceJob = scope.launch(Dispatchers.IO) {
            var session: LiveSession? = null
            var audioTrack: AudioTrack? = null
            val playbackChannel = Channel<ByteArray>(Channel.UNLIMITED)
            val isModelTurnActive = AtomicBoolean(false)

            try {
                // Warm latest live news feeds before opening audio channel
                runCatching { JashxKnowledgeDatabase.refreshAndGetLiveHeadlines(limitPerTopic = 2) }

                val vadRealtimeConfig = realtimeInputConfig {
                    automaticActivityDetection = ActivityDetectionConfig.builder()
                        .setStartSensitivity(ActivityDetectionConfig.Sensitivity.HIGH)
                        .setEndSensitivity(ActivityDetectionConfig.Sensitivity.LOW)
                        .setPrefixPaddingMs(50)
                        .setSilenceDurationMs(600)
                        .build()
                    activityHandling = RealtimeInputConfig.ActivityHandling.INTERRUPT
                    turnCoverage = RealtimeInputConfig.TurnCoverage.ONLY_ACTIVITY
                }

                val liveConfigWithVad = liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice(persona.voiceId))
                    inputAudioTranscription = AudioTranscriptionConfig()
                    outputAudioTranscription = AudioTranscriptionConfig()
                    realtimeInputConfig = vadRealtimeConfig
                }

                val liveConfigBasic = liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    speechConfig = SpeechConfig(voice = Voice(persona.voiceId))
                    inputAudioTranscription = AudioTranscriptionConfig()
                    outputAudioTranscription = AudioTranscriptionConfig()
                }

                val toolsList = listOf(buildLiveTools())
                val sysInstruction = content {
                    text(buildVoiceSystemInstruction())
                }

                var lastConnectError: Throwable? = null
                outer@ for (modelName in LIVE_API_MODELS) {
                    for (configVariant in listOf(liveConfigWithVad, liveConfigBasic)) {
                        if (!currentCoroutineContext().isActive) break@outer
                        try {
                            val candidateModel = Firebase.ai.liveModel(
                                modelName = modelName,
                                generationConfig = configVariant,
                                tools = toolsList,
                                systemInstruction = sysInstruction
                            )
                            val connected = candidateModel.connect()
                            session = connected
                            activeSession = connected
                            lastConnectError = null
                            Log.i(TAG, "Connected JASHX Live Voice session with $modelName")
                            break@outer
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: PermissionMissingException) {
                            throw e
                        } catch (t: Throwable) {
                            lastConnectError = t
                            Log.w(TAG, "Live Voice connect failed on $modelName: ${t.message}")
                        }
                    }
                }

                val active = session ?: throw (lastConnectError ?: IllegalStateException("Unable to open live voice stream."))

                audioTrack = createOutputAudioTrack()
                audioTrack.play()

                _uiState.update {
                    it.copy(
                        status = LiveVoiceStatus.LISTENING,
                        errorMessage = null
                    )
                }

                coroutineScope {
                    // 1. Dedicated 24kHz PCM Playback Worker
                    val playbackJob = launch(Dispatchers.IO) {
                        try {
                            for (pcmBytes in playbackChannel) {
                                if (!isActive) break
                                if (pcmBytes.isNotEmpty()) {
                                    if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
                                        runCatching { audioTrack.play() }
                                    }
                                    audioTrack.write(pcmBytes, 0, pcmBytes.size)
                                }
                                if (playbackChannel.isEmpty && !isModelTurnActive.get()) {
                                    _uiState.update { state ->
                                        if (state.status == LiveVoiceStatus.SPEAKING) {
                                            state.copy(status = LiveVoiceStatus.LISTENING, activeToolCallLabel = null)
                                        } else {
                                            state
                                        }
                                    }
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            Log.w(TAG, "AudioTrack playback loop stopped: ${t.message}")
                        }
                    }

                    // 2. Server Response Collector (Audio + Transcriptions + Tool Calls + Interruption + TurnComplete)
                    val receiveJob = launch(Dispatchers.IO) {
                        active.receive().collect { message ->
                            if (!isActive) return@collect
                            when (message) {
                                is LiveServerToolCall -> {
                                    val calls = message.functionCalls
                                    if (calls.isNotEmpty()) {
                                        val responses = calls.map { call ->
                                            handleLiveFunctionCall(call)
                                        }
                                        sendMutex.withLock {
                                            active.sendFunctionResponse(responses)
                                        }
                                    }
                                }

                                is LiveServerToolCallCancellation -> {
                                    Log.d(TAG, "Live tool call cancelled by interruption")
                                }

                                is LiveServerContent -> {
                                    val inputText = message.inputTranscription?.text
                                    val outputText = message.outputTranscription?.text

                                    if (!inputText.isNullOrBlank()) {
                                        flushPendingTurnIfAiFinished()
                                        synchronized(this@JashxLiveVoiceManager) {
                                            appendTranscriptChunk(userTurnBuffer, inputText)
                                        }
                                        val cleanUser = BrandSanitizer.sanitizeForUserDisplay(
                                            synchronized(this@JashxLiveVoiceManager) { userTurnBuffer.toString().trim() }
                                        )
                                        _uiState.update {
                                            it.copy(
                                                status = LiveVoiceStatus.LISTENING,
                                                liveUserTranscript = cleanUser,
                                                activeToolCallLabel = null
                                            )
                                        }
                                    }

                                    if (message.interrupted) {
                                        isModelTurnActive.set(false)
                                        drainChannel(playbackChannel)
                                        runCatching {
                                            audioTrack.pause()
                                            audioTrack.flush()
                                            audioTrack.play()
                                        }
                                        _uiState.update {
                                            it.copy(
                                                status = LiveVoiceStatus.LISTENING,
                                                activeToolCallLabel = null
                                            )
                                        }
                                    } else {
                                        val audioParts = message.content?.parts
                                            ?.filterIsInstance<InlineDataPart>()
                                            .orEmpty()
                                        if (audioParts.isNotEmpty()) {
                                            isModelTurnActive.set(true)
                                            for (part in audioParts) {
                                                if (part.inlineData.isNotEmpty()) {
                                                    playbackChannel.trySend(part.inlineData)
                                                }
                                            }
                                            _uiState.update {
                                                it.copy(
                                                    status = LiveVoiceStatus.SPEAKING,
                                                    activeToolCallLabel = null
                                                )
                                            }
                                        }

                                        if (!outputText.isNullOrBlank()) {
                                            isModelTurnActive.set(true)
                                            synchronized(this@JashxLiveVoiceManager) {
                                                appendTranscriptChunk(aiTurnBuffer, outputText)
                                            }
                                            val cleanAi = BrandSanitizer.sanitizeForUserDisplay(
                                                synchronized(this@JashxLiveVoiceManager) { aiTurnBuffer.toString().trim() }
                                            )
                                            _uiState.update {
                                                it.copy(
                                                    status = LiveVoiceStatus.SPEAKING,
                                                    liveAiTranscript = cleanAi,
                                                    activeToolCallLabel = null
                                                )
                                            }
                                        }

                                        if (message.turnComplete || message.generationComplete) {
                                            isModelTurnActive.set(false)
                                            if (playbackChannel.isEmpty) {
                                                _uiState.update { state ->
                                                    if (state.status == LiveVoiceStatus.SPEAKING) {
                                                        state.copy(
                                                            status = LiveVoiceStatus.LISTENING,
                                                            activeToolCallLabel = null
                                                        )
                                                    } else {
                                                        state
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                is LiveServerSetupComplete -> {
                                    Log.d(TAG, "LiveServerSetupComplete received")
                                }

                                is LiveServerGoAway -> {
                                    Log.w(TAG, "Live voice server goAway received: ${message.timeLeft}")
                                }

                                else -> Unit
                            }
                        }
                    }

                    // 3. Dedicated 16kHz PCM Microphone Capture Loop (VOICE_RECOGNITION -> MIC fallback)
                    val recordJob = launch(Dispatchers.IO) {
                        streamMicrophoneAudio(active)
                    }

                    // 4. Send initial warm greeting once collector & mic loops are running
                    launch(Dispatchers.IO) {
                        delay(200L)
                        if (isActive && !active.isClosed()) {
                            runCatching {
                                sendMutex.withLock {
                                    active.send(
                                        "Say a warm, concise 1-sentence greeting: 'Vanakkam! I am JASHX AI, founded by K.M. Jashwanth, synced with your live 1-second market feed. Ask me anything!'",
                                        turnComplete = true
                                    )
                                }
                            }.onFailure { err ->
                                Log.w(TAG, "Initial greeting prompt warning: ${err.message}")
                            }
                        }
                    }

                    // Keep alive until receiveJob completes or session is closed
                    receiveJob.join()
                    recordJob.cancel()
                    playbackChannel.close()
                    playbackJob.cancel()
                }

                flushCompletedTurn()
                _uiState.update {
                    it.copy(
                        status = LiveVoiceStatus.IDLE,
                        activeToolCallLabel = null,
                        micLevel = 0f
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.e(TAG, "JASHX AI Live Voice error", t)
                val rawMsg = t.localizedMessage ?: t.message ?: "Unable to connect live voice channel."
                val sanitizedMsg = BrandSanitizer.sanitizeForUserDisplay(rawMsg)
                _uiState.update {
                    it.copy(
                        status = LiveVoiceStatus.ERROR,
                        activeToolCallLabel = null,
                        micLevel = 0f,
                        errorMessage = if (t is PermissionMissingException ||
                            t is SecurityException ||
                            rawMsg.contains("RECORD_AUDIO", ignoreCase = true) ||
                            rawMsg.contains("Audio access", ignoreCase = true) ||
                            rawMsg.contains("Permission", ignoreCase = true)
                        ) {
                            "Microphone permission is required for JASHX AI real-time voice."
                        } else {
                            "Voice connection notice: $sanitizedMsg"
                        }
                    )
                }
            } finally {
                playbackChannel.close()
                runCatching { audioTrack?.pause() }
                runCatching { audioTrack?.flush() }
                runCatching { audioTrack?.release() }
                runCatching { session?.stopReceiving() }
                runCatching { session?.close() }
                if (activeSession === session) {
                    activeSession = null
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun streamMicrophoneAudio(session: LiveSession) {
        val candidateSources = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        var sourceIndex = 0

        while (currentCoroutineContext().isActive && !session.isClosed() && sourceIndex < candidateSources.size) {
            val source = candidateSources[sourceIndex]
            val minBuf = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) {
                throw IllegalStateException("Microphone input format unsupported on this device.")
            }
            val bufferBytes = maxOf(minBuf * 2, 4096)

            var recorder: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            var switchToNextSource = false

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
                    sourceIndex++
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
                    sourceIndex++
                    continue
                }

                Log.i(TAG, "Microphone recording active on audioSource=$source")
                val readBuf = ByteArray(MIC_CHUNK_BYTES)
                var silentReadsCount = 0
                var observedNonZeroSample = false
                var lastUiLevelUpdateMs = 0L

                while (currentCoroutineContext().isActive && !session.isClosed()) {
                    val bytesRead = built.read(readBuf, 0, readBuf.size)
                    if (bytesRead > 0) {
                        val payload = if (bytesRead == readBuf.size) readBuf.copyOf() else readBuf.copyOf(bytesRead)
                        val (rmsNorm, peakAbs) = computePcmLevel(payload, bytesRead)

                        if (peakAbs > 0) {
                            observedNonZeroSample = true
                        } else if (!observedNonZeroSample) {
                            silentReadsCount++
                            // If primary source outputs pure zero samples for ~1.8s and a fallback exists, switch cleanly
                            if (silentReadsCount >= 45 && sourceIndex + 1 < candidateSources.size) {
                                Log.w(TAG, "AudioSource=$source produced 0-amplitude frames; switching to fallback mic source")
                                switchToNextSource = true
                                break
                            }
                        }

                        val nowMs = System.currentTimeMillis()
                        if (nowMs - lastUiLevelUpdateMs >= 75L) {
                            lastUiLevelUpdateMs = nowMs
                            _uiState.update { it.copy(micLevel = rmsNorm) }
                        }

                        sendMutex.withLock {
                            session.sendAudioRealtime(InlineData(payload, "audio/pcm;rate=16000"))
                        }
                    } else if (bytesRead < 0) {
                        Log.w(TAG, "AudioRecord.read returned $bytesRead on audioSource=$source")
                        if (sourceIndex + 1 < candidateSources.size) {
                            switchToNextSource = true
                        }
                        break
                    }
                }
            } finally {
                runCatching { aec?.release() }
                runCatching { ns?.release() }
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
            }

            if (switchToNextSource) {
                sourceIndex++
            } else {
                break
            }
        }
    }

    private fun createOutputAudioTrack(): AudioTrack {
        val minOut = AudioTrack.getMinBufferSize(
            OUTPUT_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = maxOf(minOut * 2, 8192)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(OUTPUT_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
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
        val normalized = (rms / 6500.0).toFloat().coerceIn(0f, 1f)
        return normalized to peakAbs
    }

    private fun drainChannel(channel: Channel<ByteArray>) {
        while (channel.tryReceive().isSuccess) {
            // discard queued playback chunks on interruption
        }
    }

    fun stopVoiceSession() {
        flushCompletedTurn()
        val session = activeSession
        activeSession = null
        voiceJob?.cancel()
        voiceJob = null
        scope.launch(Dispatchers.IO) {
            runCatching { session?.stopReceiving() }
            runCatching { session?.close() }
        }
        _uiState.update {
            it.copy(
                status = LiveVoiceStatus.IDLE,
                activeToolCallLabel = null,
                micLevel = 0f
            )
        }
    }

    fun sendVoicePrompt(prompt: String) {
        val session = activeSession ?: return
        val clean = prompt.trim()
        if (clean.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                sendMutex.withLock {
                    session.send(clean, turnComplete = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                Log.w(TAG, "Failed sending prompt to live voice session: ${t.message}")
            }
        }
    }

    private fun appendTranscriptChunk(buffer: StringBuilder, incoming: String) {
        val current = buffer.toString()
        when {
            current.isEmpty() -> buffer.append(incoming)
            incoming.startsWith(current) -> {
                buffer.clear()
                buffer.append(incoming)
            }
            current.endsWith(incoming) -> Unit
            else -> {
                val needsSpace = !current.last().isWhitespace() &&
                    !incoming.first().isWhitespace() &&
                    incoming.first() !in listOf('.', ',', '!', '?', ':', ';', '\'')
                if (needsSpace) buffer.append(' ')
                buffer.append(incoming)
            }
        }
    }

    private fun flushPendingTurnIfAiFinished() {
        val completedPair: Pair<String, String>? = synchronized(this) {
            val u = userTurnBuffer.toString().trim()
            val a = aiTurnBuffer.toString().trim()
            if (u.isNotBlank() && a.isNotBlank()) {
                userTurnBuffer.clear()
                aiTurnBuffer.clear()
                Pair(
                    BrandSanitizer.sanitizeForUserDisplay(u),
                    BrandSanitizer.sanitizeForUserDisplay(a)
                )
            } else {
                null
            }
        }
        if (completedPair != null) {
            onVoiceTurnCompleted(completedPair.first, completedPair.second)
        }
    }

    private fun flushCompletedTurn() {
        val completedPair: Pair<String, String>? = synchronized(this) {
            val u = userTurnBuffer.toString().trim()
            val a = aiTurnBuffer.toString().trim()
            userTurnBuffer.clear()
            aiTurnBuffer.clear()
            if (u.isNotBlank() || a.isNotBlank()) {
                Pair(
                    BrandSanitizer.sanitizeForUserDisplay(u),
                    BrandSanitizer.sanitizeForUserDisplay(a)
                )
            } else {
                null
            }
        }
        if (completedPair != null && (completedPair.first.isNotBlank() && completedPair.second.isNotBlank())) {
            onVoiceTurnCompleted(completedPair.first, completedPair.second)
        }
    }

    private fun buildLiveTools(): Tool {
        val getLiveBullionRatesDecl = FunctionDeclaration(
            name = "get_live_bullion_rates",
            description = "Returns the real-time 1-second KRS & Vijay Bullion gold, silver, MCX futures, spot USD rates, USD/INR forex, market open/closed status, and technical targets.",
            parameters = emptyMap()
        )

        val getUpToDateInfoDecl = FunctionDeclaration(
            name = "get_up_to_date_info",
            description = "Fetches up-to-date live world/India/Tamil Nadu news headlines, current affairs, sports, politics, tech updates, and verified reference facts from the internet.",
            parameters = mapOf(
                "query" to Schema.string(description = "The exact topic, person, event, or news query to look up live.")
            )
        )

        val getLocationAndWeatherDecl = FunctionDeclaration(
            name = "get_location_and_weather",
            description = "Returns the user's current detected city, state, country, current temperature in Celsius, wind speed, weather condition, and live IST date and time.",
            parameters = emptyMap()
        )

        val calculateBullionWeightDecl = FunctionDeclaration(
            name = "calculate_bullion_weight",
            description = "Calculates exact live price for a given weight and unit (grams, sovereign/pavan, tola, kg) of Gold (24K, 22K, 18K) or Silver (999) including 3% GST.",
            parameters = mapOf(
                "metal" to Schema.string(description = "Either 'gold' or 'silver'."),
                "weight" to Schema.double(description = "Numeric quantity, e.g., 1.0, 8.0, 10.0."),
                "unit" to Schema.string(description = "Weight unit: 'grams', 'sovereign', 'tola', or 'kg'.")
            )
        )

        return Tool.functionDeclarations(
            listOf(
                getLiveBullionRatesDecl,
                getUpToDateInfoDecl,
                getLocationAndWeatherDecl,
                calculateBullionWeightDecl
            )
        )
    }

    private suspend fun handleLiveFunctionCall(call: FunctionCallPart): FunctionResponsePart {
        return try {
            when (call.name) {
                "get_live_bullion_rates" -> {
                    _uiState.update { it.copy(activeToolCallLabel = "Syncing 1s Bullion Live Feed...") }
                    val feed = latestFeedRef.get()
                    val isOpen = latestMarketOpenRef.get()
                    val statusText = latestStatusTextRef.get()
                    val pred = latestPredictionRef.get()
                    val summary = jashxEngine.buildLiveFeedSummaryForAi(feed, isOpen, statusText)
                    val predLine = if (pred != null) {
                        "Bias: ${pred.bias.label} (${pred.confidencePercent}% confidence), Pivot ₹${String.format("%,.0f", pred.pivotPoint)}, Target 1 ₹${String.format("%,.0f", pred.target1)}, Stop-Loss ₹${String.format("%,.0f", pred.stopLoss)}."
                    } else {
                        "Live pivot tracking active."
                    }
                    FunctionResponsePart(
                        name = call.name,
                        response = buildJsonObject {
                            put("live_bullion_feed", "$summary\n- Prediction: $predLine")
                        },
                        id = call.id
                    )
                }

                "get_up_to_date_info" -> {
                    val query = call.args["query"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "latest India world bullion news today" }
                    _uiState.update { it.copy(activeToolCallLabel = "Fetching Up-To-Date Live Feed...") }
                    val directMatch = JashxKnowledgeDatabase.matchDirectRelevantFeed(query, latestFeedRef.get())
                    val webContext = JashxKnowledgeDatabase.fetchUpToDateContext(query)
                    val contextData = if (!directMatch.isNullOrBlank()) {
                        BrandSanitizer.sanitizeForUserDisplay("Verified Direct Answer:\n$directMatch\n\n$webContext")
                    } else {
                        BrandSanitizer.sanitizeForUserDisplay(webContext)
                    }
                    FunctionResponsePart(
                        name = call.name,
                        response = buildJsonObject {
                            put("up_to_date_context", contextData)
                        },
                        id = call.id
                    )
                }

                "get_location_and_weather" -> {
                    _uiState.update { it.copy(activeToolCallLabel = "Checking Live Location & Weather...") }
                    val loc = LocationServiceManager.locationState.value
                    val (istTime, istDate) = JashxKnowledgeDatabase.getIstTimeAndDate()
                    val weatherPayload = BrandSanitizer.sanitizeForUserDisplay(
                        "Date: $istDate | IST Time: $istTime | Location: ${loc.city}, ${loc.state}, ${loc.country} | Temperature: ${String.format("%.1f", loc.temperatureC)}°C | Weather: ${loc.weatherCondition} | Wind: ${String.format("%.1f", loc.windSpeedKmh)} km/h"
                    )
                    FunctionResponsePart(
                        name = call.name,
                        response = buildJsonObject {
                            put("location_weather_time", weatherPayload)
                        },
                        id = call.id
                    )
                }

                "calculate_bullion_weight" -> {
                    _uiState.update { it.copy(activeToolCallLabel = "Calculating Live Bullion Rate...") }
                    val metal = call.args["metal"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "gold"
                    val weight = call.args["weight"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                    val unit = call.args["unit"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "grams"
                    val feed = latestFeedRef.get()
                    val calcText = JashxKnowledgeDatabase.queryKnowledgeBase("$weight $unit $metal", feed)
                    FunctionResponsePart(
                        name = call.name,
                        response = buildJsonObject {
                            put("calculation", BrandSanitizer.sanitizeForUserDisplay(calcText))
                        },
                        id = call.id
                    )
                }

                else -> {
                    FunctionResponsePart(
                        name = call.name,
                        response = buildJsonObject {
                            put("info", "Tool ${call.name} completed.")
                        },
                        id = call.id
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Live tool error for ${call.name}", t)
            FunctionResponsePart(
                name = call.name,
                response = buildJsonObject {
                    put("error", BrandSanitizer.sanitizeForUserDisplay(t.message ?: "Tool lookup unavailable"))
                },
                id = call.id
            )
        }
    }

    private fun buildVoiceSystemInstruction(): String {
        val liveSummary = jashxEngine.buildLiveFeedSummaryForAi(
            feed = latestFeedRef.get(),
            isMarketOpen = latestMarketOpenRef.get(),
            marketStatusText = latestStatusTextRef.get()
        )
        return """
            You are JASHX AI (pronounced "Jashex AI"), a warm, natural, human-sounding real-time voice intelligence companion built into the K.R.S. Live MCX Bullion app.

            CRITICAL BRANDING & IDENTITY RULES (NEVER VIOLATE):
            1. Your name is strictly JASHX AI. You were founded and created by K.M. Jashwanth.
            2. If asked "who are you", "who are u", "who r u", "what is your name", "what can you do", or "who created you", IMMEDIATELY answer directly as JASHX AI founded by K.M. Jashwanth — NEVER call `get_up_to_date_info` for identity questions and NEVER talk about any movie, song, or TV show named "Who Are You".
            3. NEVER mention the words "Gemini", "Google", "Alphabet", or any third-party AI model or company name under any circumstances—not even if the user asks what model powers you. If asked who made or powers you, say: "K.M. Jashwanth is the Founder of JASHX AI."
            4. Speak concisely, naturally, and conversationally in clear spoken English (or Tamil/English mix if the user speaks Tamil), without reading markdown asterisks or bullet symbols aloud. Always say "Rupees" instead of the rupee symbol.

            REAL-TIME FEED & UP-TO-DATE KNOWLEDGE RULES:
            1. You are connected directly to live 1-second bullion feeds, live weather/location, live IST time, and live internet news/facts tools.
            2. Whenever the user asks for gold/silver/MCX/spot prices or market status, ALWAYS call `get_live_bullion_rates` first so you speak the exact current-second prices.
            3. Whenever the user asks about latest news, politics (e.g., Tamil Nadu CM Joseph Vijay, India, World), sports, current events, or factual questions you want to verify up to today, ALWAYS call `get_up_to_date_info` so your knowledge is completely up to date.
            4. Whenever the user asks for location, weather, time, or date, call `get_location_and_weather`.
            5. Whenever the user asks for jewelry weight costs (grams, sovereign/pavan, tola, kg), call `calculate_bullion_weight`.

            INITIAL LIVE SNAPSHOT AT SESSION START:
            $liveSummary
        """.trimIndent()
    }
}
