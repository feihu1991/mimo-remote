package com.mimo.remote.ui.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimo.remote.data.model.MemoryFile
import com.mimo.remote.ui.theme.MiMoCyan
import com.mimo.remote.ui.theme.MiMoGreen
import com.mimo.remote.ui.theme.MiMoPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: MemoryViewModel = hiltViewModel()
) {
    val memoryFiles by viewModel.memoryFiles.collectAsState()
    var selectedFile by remember { mutableStateOf<MemoryFile?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = selectedFile?.path ?: "Memory Browser",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedFile != null) {
                            selectedFile = null
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (selectedFile == null) {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    } else {
                        IconButton(onClick = { /* Share */ }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (selectedFile != null) {
            // File content viewer
            FileContentView(
                file = selectedFile!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else {
            // File list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search memory files...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                // Category headers + files
                val filteredFiles = if (searchQuery.isBlank()) memoryFiles
                else memoryFiles.filter {
                    it.path.contains(searchQuery, ignoreCase = true) ||
                    it.content.contains(searchQuery, ignoreCase = true)
                }

                val grouped = filteredFiles.groupBy { file ->
                    when {
                        file.path.contains("MEMORY") -> "📋 Project Memory"
                        file.path.contains("checkpoint") -> "📸 Checkpoints"
                        file.path.contains("notes") -> "📝 Notes"
                        file.path.contains("tasks") || file.path.contains("progress") -> "📊 Task Progress"
                        file.path.endsWith(".json") -> "⚙️ Config"
                        else -> "📄 Other"
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (category, files) ->
                        item {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(files) { file ->
                            MemoryFileCard(
                                file = file,
                                onClick = { selectedFile = file }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MemoryFileCard(file: MemoryFile, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            file.path.contains("MEMORY") -> Icons.Default.AutoAwesome
                            file.path.contains("checkpoint") -> Icons.Default.CameraAlt
                            file.path.contains("notes") -> Icons.Default.EditNote
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = when {
                            file.path.contains("MEMORY") -> MiMoPurple
                            file.path.contains("checkpoint") -> MiMoCyan
                            file.path.contains("notes") -> MiMoGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = file.path,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = formatFileSize(file.content.length),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (file.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = file.content.take(200),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun FileContentView(file: MemoryFile, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        item {
            // File metadata
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${file.content.lines().size} lines",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatFileSize(file.content.length),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        item {
            Text(
                text = file.content,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

private fun formatFileSize(chars: Int): String {
    return when {
        chars > 1024 -> "${chars / 1024}KB"
        else -> "${chars}B"
    }
}
