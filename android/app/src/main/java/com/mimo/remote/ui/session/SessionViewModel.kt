package com.mimo.remote.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mimo.remote.data.model.*
import com.mimo.remote.data.repository.MimoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val repository: MimoRepository
) : ViewModel() {

    val terminalLines: StateFlow<List<TerminalLine>> = repository.terminalLines
    val mimoStatus: StateFlow<MimoStatus> = repository.mimoStatus
    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    private val _callState = MutableStateFlow(CallState())
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    init {
        // Sync call state from repository
        viewModelScope.launch {
            repository.callActive.collect { active ->
                if (!active && _callState.value.isInCall) {
                    _callState.value = CallState()
                }
            }
        }
    }

    fun sendInput(text: String) {
        repository.sendInput(text)
    }

    fun sendCommand(command: String, args: String? = null) {
        repository.sendCommand(command, args)
    }

    fun switchAgent(agent: String) {
        repository.switchAgent(agent)
    }

    fun startCall(type: String) {
        _callState.value = CallState(
            isInCall = true,
            callType = type,
            isVideoMuted = type == "audio"
        )
        when (type) {
            "audio" -> repository.startAudioCall()
            "video" -> repository.startVideoCall()
        }
    }

    fun endCall() {
        _callState.value = CallState()
        repository.endCall()
    }

    fun toggleMute() {
        val current = _callState.value
        val newMuted = !current.isAudioMuted
        _callState.value = current.copy(isAudioMuted = newMuted)
        repository.toggleMute(newMuted)
    }

    fun toggleVideo() {
        val current = _callState.value
        val newMuted = !current.isVideoMuted
        _callState.value = current.copy(isVideoMuted = newMuted)
        repository.toggleVideo(newMuted)
    }

    fun clearTerminal() {
        repository.clearTerminal()
    }
}
