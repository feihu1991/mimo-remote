package com.feihu.phoneinput

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhoneInputBridgeApp()
                }
            }
        }
    }
}

private enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATED,
    ERROR
}

private class RemoteClient(
    private val onStateChanged: (ConnectionState, String) -> Unit
) {
    private val ioPool = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val authenticated = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val writeLock = Any()

    @Volatile
    private var socket: Socket? = null

    @Volatile
    private var writer: BufferedWriter? = null

    fun connect(host: String, port: Int, pin: String) {
        val currentGeneration = generation.incrementAndGet()
        closeSocket()
        authenticated.set(false)
        publish(ConnectionState.CONNECTING, "正在连接 $host:$port")

        ioPool.execute {
            try {
                val newSocket = Socket()
                newSocket.tcpNoDelay = true
                newSocket.connect(InetSocketAddress(host, port), 5_000)

                if (currentGeneration != generation.get()) {
                    newSocket.close()
                    return@execute
                }

                socket = newSocket
                writer = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))

                writeLine(
                    JSONObject()
                        .put("type", "auth")
                        .put("pin", pin)
                        .put("client", "android")
                        .toString()
                )

                while (currentGeneration == generation.get()) {
                    val line = reader.readLine() ?: break
                    handleServerMessage(line)
                }
            } catch (error: Exception) {
                if (currentGeneration == generation.get()) {
                    publish(ConnectionState.ERROR, error.message ?: "连接失败")
                }
            } finally {
                if (currentGeneration == generation.get()) {
                    authenticated.set(false)
                    closeSocket()
                    publish(ConnectionState.DISCONNECTED, "连接已断开")
                }
            }
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        authenticated.set(false)
        closeSocket()
        publish(ConnectionState.DISCONNECTED, "未连接")
    }

    fun shutdown() {
        disconnect()
        ioPool.shutdownNow()
    }

    fun moveMouse(dx: Int, dy: Int) {
        send(
            JSONObject()
                .put("type", "mouse_move")
                .put("dx", dx)
                .put("dy", dy)
        )
    }

    fun mouseButton(button: String, action: String = "press") {
        send(
            JSONObject()
                .put("type", "mouse_button")
                .put("button", button)
                .put("action", action)
        )
    }

    fun scroll(delta: Int) {
        send(JSONObject().put("type", "scroll").put("delta", delta))
    }

    fun key(key: String, action: String = "press") {
        send(
            JSONObject()
                .put("type", "key")
                .put("key", key)
                .put("action", action)
        )
    }

    fun shortcut(vararg keys: String) {
        send(
            JSONObject()
                .put("type", "shortcut")
                .put("keys", JSONArray(keys.toList()))
        )
    }

    fun text(text: String) {
        if (text.isNotEmpty()) {
            send(JSONObject().put("type", "text").put("text", text))
        }
    }

    private fun send(message: JSONObject) {
        if (!authenticated.get()) return
        ioPool.execute {
            try {
                writeLine(message.toString())
            } catch (error: Exception) {
                publish(ConnectionState.ERROR, error.message ?: "发送失败")
            }
        }
    }

    private fun handleServerMessage(line: String) {
        val message = JSONObject(line)
        when (message.optString("type")) {
            "auth_ok" -> {
                authenticated.set(true)
                publish(ConnectionState.AUTHENTICATED, message.optString("message", "已连接"))
            }

            "error" -> publish(ConnectionState.ERROR, message.optString("message", "电脑端返回错误"))
        }
    }

    private fun writeLine(line: String) {
        synchronized(writeLock) {
            val activeWriter = writer ?: throw IllegalStateException("连接尚未建立")
            activeWriter.write(line)
            activeWriter.newLine()
            activeWriter.flush()
        }
    }

    private fun closeSocket() {
        synchronized(writeLock) {
            runCatching { writer?.close() }
            runCatching { socket?.close() }
            writer = null
            socket = null
        }
    }

    private fun publish(state: ConnectionState, message: String) {
        mainHandler.post { onStateChanged(state, message) }
    }
}

@Composable
private fun PhoneInputBridgeApp() {
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var statusMessage by remember { mutableStateOf("未连接") }

    val client = remember {
        RemoteClient { state, message ->
            connectionState = state
            statusMessage = message
        }
    }

    DisposableEffect(Unit) {
        onDispose { client.shutdown() }
    }

    if (connectionState == ConnectionState.AUTHENTICATED) {
        ControllerScreen(
            statusMessage = statusMessage,
            client = client,
            onDisconnect = client::disconnect
        )
    } else {
        ConnectScreen(
            state = connectionState,
            statusMessage = statusMessage,
            onConnect = client::connect
        )
    }
}

@Composable
private fun ConnectScreen(
    state: ConnectionState,
    statusMessage: String,
    onConnect: (String, Int, String) -> Unit
) {
    var host by remember { mutableStateOf("192.168.1.100") }
    var portText by remember { mutableStateOf("9527") }
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Phone Input Bridge",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text("把 Android 手机变成 Windows 触控板和键盘。")
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = host,
            onValueChange = { host = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("电脑局域网 IP") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = portText,
            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("端口") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.filter(Char::isDigit).take(6) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("电脑端显示的 6 位 PIN") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                val port = portText.toIntOrNull() ?: 9527
                onConnect(host, port, pin)
            },
            enabled = state != ConnectionState.CONNECTING && host.isNotBlank() && pin.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state == ConnectionState.CONNECTING) "连接中……" else "连接电脑")
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = statusMessage,
            color = if (state == ConnectionState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ControllerScreen(
    statusMessage: String,
    client: RemoteClient,
    onDisconnect: () -> Unit
) {
    var keyboardValue by remember { mutableStateOf(TextFieldValue("")) }
    var committedMirror by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("已连接", fontWeight = FontWeight.Bold)
                Text(statusMessage, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDisconnect) { Text("断开") }
        }

        Spacer(Modifier.height(8.dp))
        Touchpad(client)
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { client.mouseButton("left") },
                modifier = Modifier.weight(1f)
            ) { Text("左键") }
            OutlinedButton(
                onClick = { client.mouseButton("right") },
                modifier = Modifier.weight(1f)
            ) { Text("右键") }
            FilledTonalButton(
                onClick = { client.mouseButton("middle") },
                modifier = Modifier.weight(1f)
            ) { Text("中键") }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = { client.scroll(120) }, modifier = Modifier.weight(1f)) {
                Text("滚轮↑")
            }
            OutlinedButton(onClick = { client.scroll(-120) }, modifier = Modifier.weight(1f)) {
                Text("滚轮↓")
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = keyboardValue,
            onValueChange = { newValue ->
                keyboardValue = newValue
                if (newValue.composition == null) {
                    syncCommittedText(committedMirror, newValue.text, client)
                    committedMirror = newValue.text
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("点这里打开手机输入法") },
            placeholder = { Text("中文、英文和表情会输入到电脑当前光标处") },
            minLines = 2,
            maxLines = 3
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                keyboardValue = TextFieldValue("")
                committedMirror = ""
            }) {
                Text("清空手机输入区")
            }
        }

        KeyRow(client, listOf("ESC", "TAB", "BACKSPACE", "ENTER"))
        Spacer(Modifier.height(6.dp))
        KeyRow(client, listOf("LEFT", "UP", "DOWN", "RIGHT"))
        Spacer(Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShortcutButton("复制", Modifier.weight(1f)) { client.shortcut("CTRL", "C") }
            ShortcutButton("粘贴", Modifier.weight(1f)) { client.shortcut("CTRL", "V") }
            ShortcutButton("切换窗口", Modifier.weight(1f)) { client.shortcut("ALT", "TAB") }
        }
    }
}

@Composable
private fun Touchpad(client: RemoteClient) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(client) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        var lastPosition: Offset = down.position
                        var moved = false

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val delta = change.position - lastPosition

                            if (change.pressed && delta.getDistance() >= 0.5f) {
                                val dx = delta.x.roundToInt()
                                val dy = delta.y.roundToInt()
                                if (dx != 0 || dy != 0) {
                                    client.moveMouse(dx, dy)
                                    moved = true
                                }
                                lastPosition = change.position
                                change.consume()
                            }
                        } while (change.pressed)

                        if (!moved) {
                            client.mouseButton("left")
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("触控板", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("滑动移动鼠标 · 轻点左键")
            }
        }
    }
}

@Composable
private fun KeyRow(client: RemoteClient, keys: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        keys.forEach { key ->
            OutlinedButton(
                onClick = { client.key(key) },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    when (key) {
                        "BACKSPACE" -> "⌫"
                        "ENTER" -> "回车"
                        "LEFT" -> "←"
                        "RIGHT" -> "→"
                        "UP" -> "↑"
                        "DOWN" -> "↓"
                        else -> key
                    }
                )
            }
        }
    }
}

@Composable
private fun ShortcutButton(
    label: String,
    modifier: Modifier = Modifier,
    action: () -> Unit
) {
    FilledTonalButton(onClick = action, modifier = modifier) {
        Text(label)
    }
}

private fun syncCommittedText(oldText: String, newText: String, client: RemoteClient) {
    when {
        newText.startsWith(oldText) -> {
            client.text(newText.substring(oldText.length))
        }

        oldText.startsWith(newText) -> {
            val removed = oldText.substring(newText.length)
            repeat(removed.codePointCount(0, removed.length)) {
                client.key("BACKSPACE")
            }
        }

        else -> {
            repeat(oldText.codePointCount(0, oldText.length)) {
                client.key("BACKSPACE")
            }
            client.text(newText)
        }
    }
}
