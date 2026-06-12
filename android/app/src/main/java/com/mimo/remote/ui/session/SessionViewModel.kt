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
        // TODO: Initiate WebRTC call via repository
    }

    fun endCall() {
        _callState.value = CallState()
        repository.sendMediaControl("end_call")
    }

    fun toggleMute() {
        val current = _callState.value
        val newMuted = !current.isAudioMuted
        _callState.value = current.copy(isAudioMuted = newMuted)
        repository.sendMediaControl(if (newMuted) "mute_audio" else "unmute_audio")
    }

    fun toggleVideo() {
        val current = _callState.value
        val newMuted = !current.isVideoMuted
        _callState.value = current.copy(isVideoMuted = newMuted)
        repository.sendMediaControl(if (newMuted) "mute_video" else "unmute_video")
    }

    fun clearTerminal() {
        repository.clearTerminal()
    }
}
