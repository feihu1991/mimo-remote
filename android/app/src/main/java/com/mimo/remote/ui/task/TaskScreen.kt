package com.mimo.remote.ui.task

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimo.remote.data.model.TaskNode
import com.mimo.remote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskViewModel = hiltViewModel()
) {
    val taskTree by viewModel.taskTree.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Task Tree", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (taskTree.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountTree,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Text(
                        text = "No tasks yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tasks will appear here when MiMo Code\ncreates them during work sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(taskTree) { task ->
                    TaskNodeView(task, depth = 0)
                }
            }
        }
    }
}

@Composable
fun TaskNodeView(task: TaskNode, depth: Int) {
    var expanded by remember { mutableStateOf(true) }
    val hasChildren = !task.children.isNullOrEmpty()

    Column {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 24).dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (task.status) {
                    "done" -> MiMoGreen.copy(alpha = 0.1f)
                    "in_progress" -> MiMoCyan.copy(alpha = 0.1f)
                    "blocked" -> MiMoRed.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Expand/collapse
                if (hasChildren) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(24.dp))
                }

                // Status indicator
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            when (task.status) {
                                "done" -> MiMoGreen
                                "in_progress" -> MiMoCyan
                                "blocked" -> MiMoRed
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                )

                // Task ID
                Text(
                    text = task.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                // Title
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )

                // Status badge
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = task.status.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
            }
        }

        // Children
        if (expanded && hasChildren) {
            task.children!!.forEach { child ->
                TaskNodeView(child, depth = depth + 1)
            }
        }
    }
}
