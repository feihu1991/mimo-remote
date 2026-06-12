package com.mimo.remote.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mimo.remote.data.ConnectionService
import com.mimo.remote.data.model.ConnectionState
import com.mimo.remote.data.repository.MimoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MimoRepository,
    private val application: Application
) : AndroidViewModel(application) {

    val connectionState: StateFlow<ConnectionState> = repository.connectionState

    fun connect(url: String) {
        viewModelScope.launch {
            repository.connect(url)
            // Start foreground service to keep connection alive
            ConnectionService.start(application, url)
        }
    }

    fun disconnect() {
        repository.disconnect()
        ConnectionService.stop(application)
    }

    fun startQRScan() {
        // TODO: Launch camera for QR scanning
        // For now, use manual input
    }
}
