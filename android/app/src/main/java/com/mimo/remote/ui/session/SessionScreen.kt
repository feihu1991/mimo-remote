package com.mimo.remote.ui.session

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mimo.remote.data.model.CallState
import com.mimo.remote.data.model.MimoStatus
import com.mimo.remote.data.model.TerminalLine
import com.mimo.remote.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToTasks: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel()
) {
    val terminalLines by viewModel.terminalLines.collectAsState()
    val mimoStatus by viewModel.mimoStatus.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showCommandBar by remember { mutableStateOf(false) }
    var showCallOverlay by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Auto-scroll to bottom on new output
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = connectionState.cliDevice?.name ?: "MiMo Code",
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (mimoStatus.status) {
                                                "thinking" -> MiMoOrange
                                                "executing" -> MiMoCyan
                                                "waiting_approval" -> MiMoPurple
                                                "error" -> MiMoRed
                                                else -> MiMoGreen
                                            }
                                        )
                                )
                                Text(
                                    text = mimoStatus.status.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        // Call button
                        IconButton(onClick = { showCallOverlay = true }) {
                            Icon(
                                imageVector = if (callState.isInCall) Icons.Default.CallEnd else Icons.Default.Call,
                                contentDescription = "Voice Call",
                                tint = if (callState.isInCall) MiMoRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Tasks
                        IconButton(onClick = onNavigateToTasks) {
                            Icon(Icons.Default.AccountTree, "Tasks")
                        }
                        // Memory
                        IconButton(onClick = onNavigateToMemory) {
                            Icon(Icons.Default.Memory, "Memory")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                // Input bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Quick command bar
                    AnimatedVisibility(visible = showCommandBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            QuickCommandChip("goal") { viewModel.sendCommand("goal"); showCommandBar = false }
                            QuickCommandChip("dream") { viewModel.sendCommand("dream"); showCommandBar = false }
                            QuickCommandChip("distill") { viewModel.sendCommand("distill"); showCommandBar = false }
                            QuickCommandChip("compose") { viewModel.sendCommand("compose"); showCommandBar = false }
                        }
                    }

                    // Agent switcher
                    AnimatedVisibility(visible = showCommandBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AgentChip("build", mimoStatus.status) { viewModel.switchAgent("build") }
                            AgentChip("plan", mimoStatus.status) { viewModel.switchAgent("plan") }
                            AgentChip("compose", mimoStatus.status) { viewModel.switchAgent("compose") }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Command toggle
                        IconButton(
                            onClick = { showCommandBar = !showCommandBar },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (showCommandBar) Icons.Default.Close else Icons.Default.MoreVert,
                                contentDescription = "Commands",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Text input
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp)
                        )

                        // Send button
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendInput(inputText)
                                    inputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MiMoCyan)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            // Terminal output
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                items(terminalLines) { line ->
                    TerminalLineView(line)
                }

                // Show thinking indicator
                if (mimoStatus.status == "thinking") {
                    item {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MiMoOrange
                            )
                            Text(
                                text = "Thinking...",
                                style = MaterialTheme.typography.labelLarge,
                                color = MiMoOrange
                            )
                        }
                    }
                }
            }
        }

        // Call overlay
        AnimatedVisibility(
            visible = showCallOverlay,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            CallOverlay(
                callState = callState,
                onStartAudioCall = { viewModel.startCall("audio") },
                onStartVideoCall = { viewModel.startCall("video") },
                onEndCall = { viewModel.endCall() },
                onToggleMute = { viewModel.toggleMute() },
                onDismiss = { showCallOverlay = false }
            )
        }
    }
}

@Composable
fun TerminalLineView(line: TerminalLine) {
    Text(
        text = line.text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        ),
        color = if (line.isOutput) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MiMoCyan
        },
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
fun QuickCommandChip(command: String, onClick: () -> Unit) {
    SuggestionChip(
        onClick = onClick,
        label = { Text("/$command", fontSize = 12.sp) },
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun AgentChip(agent: String, currentStatus: String, onClick: () -> Unit) {
    val isActive = when (agent) {
        "build" -> currentStatus != "idle" // simplified
        else -> false
    }
    FilterChip(
        selected = isActive,
        onClick = onClick,
        label = { Text(agent, fontSize = 12.sp) },
        shape = RoundedCornerShape(16.dp)
    )
}
