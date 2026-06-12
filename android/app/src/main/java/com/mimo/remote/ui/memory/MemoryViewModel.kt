package com.mimo.remote.ui.memory

import androidx.lifecycle.ViewModel
import com.mimo.remote.data.model.MemoryFile
import com.mimo.remote.data.repository.MimoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MemoryViewModel @Inject constructor(
    private val repository: MimoRepository
) : ViewModel() {

    val memoryFiles: StateFlow<List<MemoryFile>> = repository.memoryFiles

    fun refresh() {
        // TODO: Request fresh memory dump from CLI
    }
}
