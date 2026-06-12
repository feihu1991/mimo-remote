package com.mimo.remote.data.repository

import com.mimo.remote.data.model.*
import com.mimo.remote.data.remote.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MimoRepository @Inject constructor(
    val webSocketClient: WebSocketClient
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState
    val terminalOutput: SharedFlow<MimoOutput> = webSocketClient.terminalOutput
    val mimoStatus: StateFlow<MimoStatus> = webSocketClient.mimoStatus
    val memoryUpdates: SharedFlow<MimoMemoryUpdate> = webSocketClient.memoryUpdates
    val taskUpdates: SharedFlow<TaskUpdate> = webSocketClient.taskUpdates
    val sessionStart: SharedFlow<SessionStart> = webSocketClient.sessionStart

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    private val _memoryFiles = MutableStateFlow<List<MemoryFile>>(emptyList())
    val memoryFiles: StateFlow<List<MemoryFile>> = _memoryFiles.asStateFlow()

    private val _taskTree = MutableStateFlow<List<TaskNode>>(emptyList())
    val taskTree: StateFlow<List<TaskNode>> = _taskTree.asStateFlow()

    init {
        terminalOutput.onEach { output ->
            val current = _terminalLines.value.toMutableList()
            current.add(TerminalLine(text = output.data, timestamp = output.timestamp))
            if (current.size > 5000) current.removeRange(0, current.size - 5000)
            _terminalLines.value = current
        }.launchIn(scope)

        memoryUpdates.onEach { update ->
            _memoryFiles.value = update.files
        }.launchIn(scope)

        taskUpdates.onEach { update ->
            _taskTree.value = update.tasks
        }.launchIn(scope)
    }

    fun connect(url: String) {
        webSocketClient.connect(url)
    }

    fun disconnect() {
        webSocketClient.disconnect()
        _terminalLines.value = emptyList()
        _memoryFiles.value = emptyList()
        _taskTree.value = emptyList()
    }

    fun sendInput(text: String) {
        webSocketClient.sendInput(text)
    }

    fun sendCommand(command: String, args: String? = null) {
        webSocketClient.sendCommand(command, args)
    }

    fun switchAgent(agent: String) {
        webSocketClient.switchAgent(agent)
    }

    fun sendMediaControl(action: String) {
        webSocketClient.sendMediaControl(action)
    }

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }
}
