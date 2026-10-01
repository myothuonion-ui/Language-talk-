package com.myothuonion.languagetalk.network

import android.util.Base64
import com.myothuonion.languagetalk.model.LiveSessionConfig
import com.myothuonion.languagetalk.util.LivePcmPlayer
import com.myothuonion.languagetalk.util.LivePcmRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.myothuonion.languagetalk.util.pcm16Wave
import java.util.ArrayDeque
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class LivePhase { DISCONNECTED, CONNECTING, LISTENING, THINKING, SPEAKING, RECONNECTING, ERROR }
enum class LiveSpeaker { USER, AI }

data class LiveLine(val speaker: LiveSpeaker, val text: String, val timestamp: Long = System.currentTimeMillis())

data class LiveState(
    val phase: LivePhase = LivePhase.DISCONNECTED,
    val connected: Boolean = false,
    val micEnabled: Boolean = true,
    val inputLevel: Float = 0f,
    val outputLevel: Float = 0f,
    val userCaption: String = "",
    val aiCaption: String = "",
    val lines: List<LiveLine> = emptyList(),
    val activeModel: String = "",
    val fallbackUsed: Boolean = false,
    val diagnostic: String = "AI core idle",
    val error: String? = null
)

class GeminiLiveSession(
    private val config: LiveSessionConfig,
    private val onTurnComplete: suspend (userText: String, aiText: String, audio: AudioPayload?) -> Unit,
    private val onLearningTool: suspend (JsonObject) -> JsonObject,
    private val onTerminalFailure: (reason: String) -> Unit = {}
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private val recorder = LivePcmRecorder()
    private val player = LivePcmPlayer(
        onLevel = { level -> _state.update { it.copy(outputLevel = level) } },
        onIdle = {
            _state.update { state ->
                if (state.phase == LivePhase.SPEAKING) state.copy(phase = LivePhase.LISTENING) else state
            }
        }
    )
    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state

    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var setupTimeoutJob: Job? = null
    private var modelIndex = 0
    private var sessionHandle: String? = null
    private var reconnectCount = 0
    private var latestLesson = ""
    private val continuation = config.initialTurns.toMutableList()
    private val audioLock = Any()
    private val capturedAudio = ArrayDeque<ByteArray>()
    private var finalizing: Job? = null
    private var toolsInTurn = 0
    private val stopped = AtomicBoolean(false)
    private val terminalFallbackStarted = AtomicBoolean(false)

    fun start() {
        if (socket != null || stopped.get()) return
        if (config.models.isEmpty()) {
            switchToReliableVoice("ဒီ key အတွက် native Live model မရှိပါ။ Gemini text + voice သို့ပြောင်းနေသည်။")
            return
        }
        connect(resuming = false)
    }

    private fun connect(resuming: Boolean) {
        _state.update {
            it.copy(
                phase = if (resuming) LivePhase.RECONNECTING else LivePhase.CONNECTING,
                connected = false,
                activeModel = config.models[modelIndex],
                fallbackUsed = modelIndex > 0,
                diagnostic = if (resuming) "Restoring live context" else "Linking ${config.models[modelIndex]}",
                error = null
            )
        }
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${config.apiKey}")
            .build()
        socket = http.newWebSocket(request, listener)
        setupTimeoutJob?.cancel()
        setupTimeoutJob = scope.launch {
            delay(15_000)
            if (!stopped.get() && !_state.value.connected) {
                tryNextModel("Live setup timeout: ${config.models[modelIndex]}")
            }
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        _state.update { it.copy(micEnabled = enabled, inputLevel = if (enabled) it.inputLevel else 0f) }
        if (!enabled) {
            send(buildJsonObject {
                put("realtimeInput", buildJsonObject { put("audioStreamEnd", JsonPrimitive(true)) })
            })
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        reconnectJob?.cancel()
        setupTimeoutJob?.cancel()
        send(buildJsonObject {
            put("realtimeInput", buildJsonObject { put("audioStreamEnd", JsonPrimitive(true)) })
        })
        recorder.stop()
        player.stop()
        socket?.close(1000, "User ended live session")
        socket = null
        http.dispatcher.executorService.shutdown()
        scope.cancel()
        _state.value = _state.value.copy(phase = LivePhase.DISCONNECTED, connected = false, inputLevel = 0f, outputLevel = 0f)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (webSocket !== socket || stopped.get()) return
            _state.update { it.copy(diagnostic = "Authenticating Live session") }
            webSocket.send(setupMessage().toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket || stopped.get()) return
            runCatching { handleMessage(json.parseToJsonElement(text).jsonObject) }
                .onFailure { fail("Live response ကိုဖတ်၍မရပါ: ${it.message}", reconnect = false) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket || stopped.get()) return
            socket = null
            if (!_state.value.connected && code != 1000) {
                tryNextModel("Live setup ပိတ်သွားသည် ($code): $reason")
            } else scheduleReconnect("Live connection ပိတ်သွားသည် ($code)")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket || stopped.get()) return
            socket = null
            val reason = response?.let { "Live handshake failed (${it.code})" }
                ?: t.message ?: "Live connection မရပါ"
            if (response?.code == 403) {
                switchToReliableVoice(reason)
            } else if (!_state.value.connected && response?.code != 401) {
                tryNextModel(reason)
            } else fail(reason, reconnect = _state.value.connected)
        }
    }

    private fun setupMessage(): JsonObject = LiveProtocol.setup(config, config.models[modelIndex], sessionHandle)

    private fun handleMessage(root: JsonObject) {
        root["error"]?.jsonObject?.let { error ->
            val code = error["code"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val message = error["message"]?.jsonPrimitive?.contentOrNull ?: "Gemini Live setup error"
            if (code == 403) switchToReliableVoice(message)
            else if (!_state.value.connected && code != 401) tryNextModel(message)
            else fail(message, reconnect = false)
            return
        }
        root["sessionResumptionUpdate"]?.jsonObject?.let { update ->
            if (update["resumable"]?.jsonPrimitive?.content == "true") {
                sessionHandle = update["newHandle"]?.jsonPrimitive?.contentOrNull ?: sessionHandle
            }
        }
        root["toolCall"]?.jsonObject?.get("functionCalls")?.jsonArray?.let { calls ->
            val connectedSocket = socket
            scope.launch {
                val responses = buildJsonArray {
                    calls.forEach { raw ->
                        val call = raw.jsonObject
                        val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        val response = if (name == "update_learning_progress" && toolsInTurn++ == 0) {
                            runCatching { onLearningTool(call["args"]?.jsonObject ?: buildJsonObject {}) }
                                .getOrElse { buildJsonObject { put("error", JsonPrimitive("Progress could not be saved; repeat the current step.")) } }
                        } else buildJsonObject { put("notice", JsonPrimitive("Progress was already recorded for this turn. Wait for the learner.")) }
                        latestLesson = response.toString()
                        add(buildJsonObject {
                            call["id"]?.let { put("id", it) }
                            put("name", JsonPrimitive(name))
                            put("response", response)
                        })
                    }
                }
                if (connectedSocket === socket && !stopped.get()) send(buildJsonObject {
                    put("toolResponse", buildJsonObject { put("functionResponses", responses) })
                })
            }
        }
        if (root["setupComplete"] != null) {
            setupTimeoutJob?.cancel()
            _state.update {
                it.copy(
                    phase = LivePhase.LISTENING,
                    connected = true,
                    activeModel = config.models[modelIndex],
                    fallbackUsed = modelIndex > 0,
                    diagnostic = "Voice channel ready",
                    error = null
                )
            }
            if (sessionHandle == null) send(LiveProtocol.continuation(continuation,
                config.openingPrompt + if (latestLesson.isBlank()) "" else " Latest authoritative lesson state: $latestLesson"))
            startRecorderIfNeeded()
        }
        if (root["goAway"] != null) {
            socket?.close(1001, "Server requested reconnect")
            return
        }
        val server = root["serverContent"]?.jsonObject ?: return
        server["interimInputTranscription"]?.jsonObject?.transcript()?.let { text ->
            _state.update { it.copy(userCaption = text, phase = LivePhase.LISTENING) }
        }
        server["inputTranscription"]?.jsonObject?.transcript()?.let { text ->
            _state.update { it.copy(userCaption = LiveProtocol.mergeTranscript(it.userCaption, text), phase = LivePhase.THINKING) }
        }
        server["outputTranscription"]?.jsonObject?.transcript()?.let { text ->
            _state.update { it.copy(aiCaption = LiveProtocol.mergeTranscript(it.aiCaption, text), phase = LivePhase.SPEAKING) }
        }
        server["modelTurn"]?.jsonObject?.get("parts")?.jsonArray?.forEach { element ->
            val inline = element.jsonObject["inlineData"]?.jsonObject ?: return@forEach
            val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            player.enqueue(Base64.decode(data, Base64.DEFAULT))
            _state.update { it.copy(phase = LivePhase.SPEAKING) }
        }
        if (server["interrupted"]?.jsonPrimitive?.content == "true") {
            player.interrupt()
            _state.update { it.copy(phase = LivePhase.LISTENING, aiCaption = "") }
        }
        if (server["turnComplete"]?.jsonPrimitive?.content == "true") {
            finalizing?.cancel()
            finalizing = scope.launch { delay(250); completeTurn() }
        }
    }

    private fun JsonObject.transcript(): String? = this["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun completeTurn() {
        val snapshot = _state.value
        val user = snapshot.userCaption.trim()
        val ai = snapshot.aiCaption.trim()
        val newLines = buildList {
            addAll(snapshot.lines)
            if (user.isNotBlank()) add(LiveLine(LiveSpeaker.USER, user))
            if (ai.isNotBlank()) add(LiveLine(LiveSpeaker.AI, ai))
        }.takeLast(40)
        _state.update { it.copy(lines = newLines, userCaption = "", aiCaption = "", phase = LivePhase.LISTENING) }
        toolsInTurn = 0
        reconnectCount = 0
        if (user.isNotBlank()) continuation += "user" to user
        if (ai.isNotBlank()) continuation += "model" to ai
        while (continuation.size > 16) continuation.removeAt(0)
        val pcm = synchronized(audioLock) {
            val bytes = ByteArray(capturedAudio.sumOf { it.size })
            var offset = 0
            capturedAudio.forEach { chunk -> chunk.copyInto(bytes, offset); offset += chunk.size }
            capturedAudio.clear()
            bytes
        }
        val audio = if (config.recordAudio && user.isNotBlank() && pcm.isNotEmpty()) AudioPayload(pcm16Wave(pcm), "audio/wav") else null
        if (user.isNotBlank() || ai.isNotBlank()) CoroutineScope(Dispatchers.IO).launch { onTurnComplete(user, ai, audio) }
    }

    private fun startRecorderIfNeeded() {
        if (recorder.isRunning) return
        recorder.start { bytes, level ->
            val current = _state.value
            if (!current.connected || !current.micEnabled || stopped.get()) return@start
            if (config.recordAudio) synchronized(audioLock) {
                capturedAudio.addLast(bytes.copyOf())
                while (capturedAudio.size > 600) capturedAudio.removeFirst()
            }
            _state.update { it.copy(inputLevel = level) }
            send(buildJsonObject {
                put("realtimeInput", buildJsonObject {
                    put("audio", buildJsonObject {
                        put("data", JsonPrimitive(Base64.encodeToString(bytes, Base64.NO_WRAP)))
                        put("mimeType", JsonPrimitive("audio/pcm;rate=16000"))
                    })
                })
            })
        }
    }

    private fun send(message: JsonObject): Boolean = socket?.send(message.toString()) ?: false

    private fun scheduleReconnect(reason: String) {
        if (stopped.get() || reconnectJob?.isActive == true) return
        if (++reconnectCount > 4) {
            switchToReliableVoice("Live reconnect failed; continuing with Gemini TTS")
            return
        }
        player.interrupt()
        _state.update { it.copy(phase = LivePhase.RECONNECTING, connected = false, diagnostic = reason, error = null) }
        reconnectJob = scope.launch {
            delay(900)
            if (!stopped.get()) connect(resuming = sessionHandle != null)
        }
    }

    private fun tryNextModel(reason: String) {
        setupTimeoutJob?.cancel()
        if (modelIndex + 1 >= config.models.size) {
            switchToReliableVoice("$reason\nLive socket မရသဖြင့် reliable voice mode သို့ပြောင်းနေသည်")
            return
        }
        val oldSocket = socket
        socket = null
        oldSocket?.close(1000, "Trying fallback model")
        recorder.stop()
        player.interrupt()
        sessionHandle = null
        modelIndex += 1
        _state.update {
            it.copy(
                phase = LivePhase.CONNECTING,
                connected = false,
                activeModel = config.models[modelIndex],
                fallbackUsed = true,
                diagnostic = "Fallback → ${config.models[modelIndex]}",
                error = null
            )
        }
        connect(resuming = false)
    }

    private fun switchToReliableVoice(reason: String) {
        if (stopped.get() || !terminalFallbackStarted.compareAndSet(false, true)) return
        setupTimeoutJob?.cancel()
        reconnectJob?.cancel()
        val oldSocket = socket
        socket = null
        oldSocket?.close(1000, "Switching to text and voice")
        recorder.stop()
        player.interrupt()
        _state.update { it.copy(phase = LivePhase.RECONNECTING, connected = false, diagnostic = "Switching voice mode", error = null) }
        onTerminalFailure(reason)
    }

    private fun fail(message: String, reconnect: Boolean) {
        setupTimeoutJob?.cancel()
        _state.update { it.copy(phase = LivePhase.ERROR, connected = false, diagnostic = "Live core paused", error = message) }
        if (reconnect) scheduleReconnect(message)
    }

}
