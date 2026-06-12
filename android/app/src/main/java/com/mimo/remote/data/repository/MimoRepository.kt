package com.mimo.remote.data.repository

import com.mimo.remote.data.model.*
import com.mimo.remote.data.remote.WebSocketClient
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MimoRepository @Inject constructor(
    val webSocketClient: WebSocketClient
) {
    val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState
    val terminalOutput: SharedFlow<MimoOutput> = webSocketClient.terminalOutput
    val mimoStatus: StateFlow<MimoStatus> = webSocketClient.mimoStatus
    val memoryUpdates: SharedFlow<MimoMemoryUpdate> = webSocketClient.memoryUpdates
    val taskUpdates: SharedFlow<TaskUpdate> = webSocketClient.taskUpdates
    val sessionStart: SharedFlow<SessionStart> = webSocketClient.sessionStart

    // Terminal output buffer
    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines: StateFlow<List<TerminalLine>> = _terminalLines.asStateFlow()

    // Memory files cache
    private val _memoryFiles = MutableStateFlow<List<MemoryFile>>(emptyList())
    val memoryFiles: StateFlow<List<MemoryFile>> = _memoryFiles.asStateFlow()

    // Task tree cache
    private val _taskTree = MutableStateFlow<List<TaskNode>>(emptyList())
    val taskTree: StateFlow<List<TaskNode>> = _taskTree.asStateFlow()

    init {
        // Collect terminal output into buffer
        terminalOutput.onEach { output ->
            val current = _terminalLines.value.toMutableList()
            current.add(TerminalLine(text = output.data, timestamp = output.timestamp))
            // Keep last 5000 lines
            if (current.size > 5000) {
                current.removeRange(0, current.size - 5000)
            }
            _terminalLines.value = current
        }.launchIn(kotlinx.coroutines.GlobalScope)

        // Collect memory updates
        memoryUpdates.onEach { update ->
            _memoryFiles.value = update.files
        }.launchIn(kotlinx.coroutines.GlobalScope)

        // Collect task updates
        taskUpdates.onEach { update ->
            _taskTree.value = update.tasks
        }.launchIn(kotlinx.coroutines.GlobalScope)
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

    fun clearTerminal() {
        _terminalLines.value = emptyList()
    }
}
