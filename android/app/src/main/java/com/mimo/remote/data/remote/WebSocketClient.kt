package com.mimo.remote.data.remote

import android.util.Log
import com.mimo.remote.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WebSocket client for connecting to MiMo Remote CLI / relay server
 */
class WebSocketClient {

    companion object {
        private const val TAG = "WebSocketClient"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
    }

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var reconnectAttempts = 0
    private var shouldReconnect = true

    // ─── State Flows ─────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState())
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _terminalOutput = MutableSharedFlow<MimoOutput>(extraBufferCapacity = 256)
    val terminalOutput: SharedFlow<MimoOutput> = _terminalOutput.asSharedFlow()

    private val _mimoStatus = MutableStateFlow(MimoStatus(status = "idle"))
    val mimoStatus: StateFlow<MimoStatus> = _mimoStatus.asStateFlow()

    private val _memoryUpdates = MutableSharedFlow<MimoMemoryUpdate>(extraBufferCapacity = 16)
    val memoryUpdates: SharedFlow<MimoMemoryUpdate> = _memoryUpdates.asSharedFlow()

    private val _taskUpdates = MutableSharedFlow<TaskUpdate>(extraBufferCapacity = 16)
    val taskUpdates: SharedFlow<TaskUpdate> = _taskUpdates.asSharedFlow()

    private val _mediaMessages = MutableSharedFlow<Any>(extraBufferCapacity = 32)
    val mediaMessages: SharedFlow<Any> = _mediaMessages.asSharedFlow()

    private val _sessionStart = MutableSharedFlow<SessionStart>(extraBufferCapacity = 4)
    val sessionStart: SharedFlow<SessionStart> = _sessionStart.asSharedFlow()

    // ─── Connect ─────────────────────────────────────────────

    fun connect(url: String) {
        shouldReconnect = true
        reconnectAttempts = 0
        _connectionState.value = ConnectionState(isConnecting = true)

        val request = Request.Builder()
            .url(url)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $url")
                reconnectAttempts = 0
                _connectionState.value = _connectionState.value.copy(
                    isConnected = true,
                    isConnecting = false,
                    error = null
                )

                // Register as Android device
                val device = DeviceInfo(
                    id = getDeviceId(),
                    name = android.os.Build.MODEL,
                    type = "android",
                    platform = "android-${android.os.Build.VERSION.SDK_INT}",
                    version = "0.1.0"
                )
                ws.send("""{"type":"register","deviceId":"${device.id}","role":"android"}""")

                // Send pair request
                val pairReq = PairRequest(
                    device = device,
                    publicKey = "" // TODO: real key exchange
                )
                ws.send(kotlinx.serialization.json.Json.encodeToString(
                    PairRequest.serializer(), pairReq
                ))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                try {
                    handleMessage(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to handle message: ${e.message}")
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Connection closed: $code $reason")
                _connectionState.value = _connectionState.value.copy(
                    isConnected = false,
                    isConnecting = false
                )
                attemptReconnect(url)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}")
                _connectionState.value = _connectionState.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    error = t.message
                )
                attemptReconnect(url)
            }
        })
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _connectionState.value = ConnectionState()
    }

    // ─── Send Messages ───────────────────────────────────────

    fun sendInput(text: String) {
        val msg = UserInput(data = text, timestamp = System.currentTimeMillis())
        send(msg)
    }

    fun sendCommand(command: String, args: String? = null) {
        val msg = UserCommand(command = command, args = args)
        send(msg)
    }

    fun switchAgent(agent: String) {
        val msg = AgentSwitch(agent = agent)
        send(msg)
    }

    fun sendMediaOffer(sdp: String, mediaType: String) {
        val msg = MediaOffer(sdp = sdp, mediaType = mediaType)
        send(msg)
    }

    fun sendMediaAnswer(sdp: String) {
        val msg = MediaAnswer(sdp = sdp)
        send(msg)
    }

    fun sendMediaCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        val msg = MediaCandidate(
            candidate = candidate,
            sdpMid = sdpMid,
            sdpMLineIndex = sdpMLineIndex
        )
        send(msg)
    }

    fun sendMediaControl(action: String) {
        val msg = MediaControl(action = action)
        send(msg)
    }

    // ─── Internal ────────────────────────────────────────────

    private fun send(msg: Any) {
        val json = kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.json.JsonObject.serializer(),
            kotlinx.serialization.json.Json.parseToJsonElement(
                kotlinx.serialization.json.Json.encodeToString(
                    kotlinx.serialization.json.JsonObject.serializer(),
                    when (msg) {
                        is UserInput -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            UserInput.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        is UserCommand -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            UserCommand.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        is AgentSwitch -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            AgentSwitch.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        is MediaOffer -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            MediaOffer.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        is MediaAnswer -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            MediaAnswer.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        is MediaCandidate -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            MediaCandidate.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        is MediaControl -> kotlinx.serialization.json.Json.encodeToJsonElement(
                            MediaControl.serializer(), msg
                        ) as kotlinx.serialization.json.JsonObject
                        else -> return
                    }
                )
            )
        )
        webSocket?.send(json)
    }

    private fun handleMessage(raw: String) {
        val json = JSONObject(raw)
        val type = json.optString("type")

        when (type) {
            "session:start" -> {
                val session = SessionStart(
                    sessionId = json.optString("sessionId"),
                    mimoVersion = json.optString("mimoVersion")
                )
                _connectionState.value = _connectionState.value.copy(
                    sessionId = session.sessionId,
                    cliDevice = session.cliDevice
                )
                scope.launch { _sessionStart.emit(session) }
            }

            "session:end" -> {
                _connectionState.value = _connectionState.value.copy(
                    isConnected = false,
                    sessionId = null
                )
            }

            "mimo:output" -> {
                val output = MimoOutput(
                    data = json.optString("data"),
                    timestamp = json.optLong("timestamp")
                )
                scope.launch { _terminalOutput.emit(output) }
            }

            "mimo:status" -> {
                val status = MimoStatus(
                    status = json.optString("status"),
                    detail = json.optString("detail").takeIf { it.isNotEmpty() }
                )
                _mimoStatus.value = status
            }

            "mimo:memory" -> {
                val filesJson = json.optJSONArray("files") ?: return
                val files = (0 until filesJson.length()).map { i ->
                    val f = filesJson.getJSONObject(i)
                    MemoryFile(
                        path = f.optString("path"),
                        content = f.optString("content"),
                        lastModified = f.optLong("lastModified")
                    )
                }
                scope.launch { _memoryUpdates.emit(MimoMemoryUpdate(files = files)) }
            }

            "mimo:task:update" -> {
                // Parse task tree
                scope.launch { _taskUpdates.emit(TaskUpdate(tasks = emptyList())) }
            }

            "media:offer", "media:answer", "media:candidate", "media:control" -> {
                scope.launch { _mediaMessages.emit(json) }
            }

            "pair:response" -> {
                val accepted = json.optBoolean("accepted")
                if (accepted) {
                    Log.i(TAG, "Pairing accepted")
                }
            }
        }
    }

    private fun attemptReconnect(url: String) {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts
        Log.i(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")

        scope.launch {
            delay(delay)
            if (shouldReconnect && _connectionState.value.isConnected.not()) {
                connect(url)
            }
        }
    }

    private fun getDeviceId(): String {
        val hash = "${android.os.Build.MODEL}${android.os.Build.SERIAL}${android.os.Build.FINGERPRINT}"
            .hashCode()
            .toString(16)
        return "android_${hash.take(12)}"
    }
}
