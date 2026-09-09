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
    val error: String? = null
)

class GeminiLiveSession(
    private val config: LiveSessionConfig,
    private val onTurnComplete: suspend (userText: String, aiText: String) -> Unit
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
    private var resumptionHandle: String? = null
    private var reconnectJob: Job? = null
    private val stopped = AtomicBoolean(false)

    fun start() {
        if (socket != null || stopped.get()) return
        connect(resuming = false)
    }

    private fun connect(resuming: Boolean) {
        _state.update {
            it.copy(
                phase = if (resuming) LivePhase.RECONNECTING else LivePhase.CONNECTING,
                connected = false,
                error = null
            )
        }
        val request = Request.Builder()
            .url("wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${config.apiKey}")
            .build()
        socket = http.newWebSocket(request, listener)
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
            scheduleReconnect("Live connection ပိတ်သွားသည်")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket || stopped.get()) return
            socket = null
            scheduleReconnect(t.message ?: "Live connection မရပါ")
        }
    }

    private fun setupMessage(): JsonObject = buildJsonObject {
        put("setup", buildJsonObject {
            put("model", JsonPrimitive("models/${config.model}"))
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray { add(JsonPrimitive("AUDIO")) })
                put("temperature", JsonPrimitive(0.7))
                put("speechConfig", buildJsonObject {
                    put("voiceConfig", buildJsonObject {
                        put("prebuiltVoiceConfig", buildJsonObject {
                            put("voiceName", JsonPrimitive(config.voiceName))
                        })
                    })
                })
            })
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", JsonPrimitive(config.systemInstruction)) })
                })
            })
            put("inputAudioTranscription", buildJsonObject { put("mode", JsonPrimitive("VERBATIM")) })
            put("outputAudioTranscription", buildJsonObject { })
            put("realtimeInputConfig", buildJsonObject {
                put("activityHandling", JsonPrimitive("START_OF_ACTIVITY_INTERRUPTS"))
                put("automaticActivityDetection", buildJsonObject {
                    put("disabled", JsonPrimitive(false))
                    put("prefixPaddingMs", JsonPrimitive(40))
                    put("silenceDurationMs", JsonPrimitive(650))
                })
            })
            put("contextWindowCompression", buildJsonObject {
                put("slidingWindow", buildJsonObject { put("targetTokens", JsonPrimitive(16_000)) })
            })
            put("sessionResumption", buildJsonObject {
                resumptionHandle?.let { put("handle", JsonPrimitive(it)) }
            })
        })
    }

    private fun handleMessage(root: JsonObject) {
        if (root["setupComplete"] != null) {
            _state.update { it.copy(phase = LivePhase.LISTENING, connected = true, error = null) }
            startRecorderIfNeeded()
        }
        root["sessionResumptionUpdate"]?.jsonObject?.let { update ->
            if (update["resumable"]?.jsonPrimitive?.content == "true") {
                resumptionHandle = update["newHandle"]?.jsonPrimitive?.contentOrNull ?: resumptionHandle
            }
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
            _state.update { it.copy(userCaption = mergeTranscript(it.userCaption, text), phase = LivePhase.THINKING) }
        }
        server["outputTranscription"]?.jsonObject?.transcript()?.let { text ->
            _state.update { it.copy(aiCaption = mergeTranscript(it.aiCaption, text), phase = LivePhase.SPEAKING) }
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
        if (server["turnComplete"]?.jsonPrimitive?.content == "true") completeTurn()
    }

    private fun JsonObject.transcript(): String? = this["text"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

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
        if (user.isNotBlank() || ai.isNotBlank()) scope.launch { onTurnComplete(user, ai) }
    }

    private fun startRecorderIfNeeded() {
        if (recorder.isRunning) return
        recorder.start { bytes, level ->
            val current = _state.value
            if (!current.connected || !current.micEnabled || stopped.get()) return@start
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
        player.interrupt()
        _state.update { it.copy(phase = LivePhase.RECONNECTING, connected = false, error = reason) }
        reconnectJob = scope.launch {
            delay(900)
            if (!stopped.get()) connect(resuming = resumptionHandle != null)
        }
    }

    private fun fail(message: String, reconnect: Boolean) {
        _state.update { it.copy(phase = LivePhase.ERROR, connected = false, error = message) }
        if (reconnect) scheduleReconnect(message)
    }

    private fun mergeTranscript(current: String, next: String): String = when {
        current.isBlank() -> next
        next.startsWith(current) -> next
        current.endsWith(next) -> current
        else -> "$current $next"
    }.trim()
}
