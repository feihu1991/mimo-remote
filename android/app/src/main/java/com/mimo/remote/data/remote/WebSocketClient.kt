package com.mimo.remote.data.remote

import android.util.Log
import com.mimo.remote.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    private var lastUrl: String = ""

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

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

    private val _mediaMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 32)
    val mediaMessages: SharedFlow<JSONObject> = _mediaMessages.asSharedFlow()

    private val _sessionStart = MutableSharedFlow<SessionStart>(extraBufferCapacity = 4)
    val sessionStart: SharedFlow<SessionStart> = _sessionStart.asSharedFlow()

    // ─── Connect ─────────────────────────────────────────────

    fun connect(url: String) {
        shouldReconnect = true
        reconnectAttempts = 0
        lastUrl = url
        _connectionState.value = ConnectionState(isConnecting = true)

        val request = Request.Builder().url(url).build()

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
                val registerMsg = buildJsonObject {
                    put("type", "register")
                    put("deviceId", getDeviceId())
                    put("role", "android")
                }
                ws.send(registerMsg.toString())

                // Send pair request
                val pairReq = buildJsonObject {
                    put("type", "pair:request")
                    put("device", buildJsonObject {
                        put("id", getDeviceId())
                        put("name", android.os.Build.MODEL)
                        put("type", "android")
                        put("platform", "android-${android.os.Build.VERSION.SDK_INT}")
                        put("version", "0.1.0")
                    })
                    put("publicKey", "")
                }
                ws.send(pairReq.toString())
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
                attemptReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Connection failed: ${t.message}")
                _connectionState.value = _connectionState.value.copy(
                    isConnected = false,
                    isConnecting = false,
                    error = t.message
                )
                attemptReconnect()
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
        send(buildJsonObject {
            put("type", "user:input")
            put("data", text)
            put("timestamp", System.currentTimeMillis())
        })
    }

    fun sendCommand(command: String, args: String? = null) {
        send(buildJsonObject {
            put("type", "user:command")
            put("command", command)
            if (args != null) put("args", args)
        })
    }

    fun switchAgent(agent: String) {
        send(buildJsonObject {
            put("type", "user:agent_switch")
            put("agent", agent)
        })
    }

    fun sendMediaOffer(sdp: String, mediaType: String) {
        send(buildJsonObject {
            put("type", "media:offer")
            put("sdp", sdp)
            put("mediaType", mediaType)
        })
    }

    fun sendMediaAnswer(sdp: String) {
        send(buildJsonObject {
            put("type", "media:answer")
            put("sdp", sdp)
        })
    }

    fun sendMediaCandidate(candidate: String, sdpMid: String, sdpMLineIndex: Int) {
        send(buildJsonObject {
            put("type", "media:candidate")
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        })
    }

    fun sendMediaControl(action: String) {
        send(buildJsonObject {
            put("type", "media:control")
            put("action", action)
        })
    }

    // ─── Internal ────────────────────────────────────────────

    private fun send(obj: JsonObject) {
        webSocket?.send(obj.toString())
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
                    sessionId = session.sessionId
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
                scope.launch { _taskUpdates.emit(TaskUpdate(tasks = emptyList())) }
            }

            "media:offer", "media:answer", "media:candidate", "media:control" -> {
                scope.launch { _mediaMessages.emit(json) }
            }

            "pair:response" -> {
                val accepted = json.optBoolean("accepted")
                if (accepted) {
                    Log.i(TAG, "Pairing accepted")
                    // Extract CLI device info
                    val deviceJson = json.optJSONObject("device")
                    if (deviceJson != null) {
                        val cliDevice = DeviceInfo(
                            id = deviceJson.optString("id"),
                            name = deviceJson.optString("name"),
                            type = deviceJson.optString("type"),
                            platform = deviceJson.optString("platform"),
                            version = deviceJson.optString("version")
                        )
                        _connectionState.value = _connectionState.value.copy(
                            cliDevice = cliDevice
                        )
                    }
                }
            }

            "ping" -> {
                send(buildJsonObject {
                    put("type", "pong")
                    put("timestamp", System.currentTimeMillis())
                })
            }
        }
    }

    private fun attemptReconnect() {
        if (!shouldReconnect || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        val delay = RECONNECT_DELAY_MS * reconnectAttempts
        Log.i(TAG, "Reconnecting in ${delay}ms (attempt $reconnectAttempts)")

        scope.launch {
            delay(delay)
            if (shouldReconnect && !_connectionState.value.isConnected) {
                connect(lastUrl)
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
