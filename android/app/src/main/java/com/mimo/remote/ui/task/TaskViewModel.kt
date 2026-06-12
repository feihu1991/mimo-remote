package com.mimo.remote.ui.task

import androidx.lifecycle.ViewModel
import com.mimo.remote.data.model.TaskNode
import com.mimo.remote.data.repository.MimoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TaskViewModel @Inject constructor(
    repository: MimoRepository
) : ViewModel() {
    val taskTree: StateFlow<List<TaskNode>> = repository.taskTree
}
