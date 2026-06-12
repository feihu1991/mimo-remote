package com.mimo.remote.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var serverUrl by remember { mutableStateOf(viewModel.getServerUrl()) }
    var autoReconnect by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection settings
            Text(
                text = "Connection",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    viewModel.setServerUrl(it)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Default Server URL") },
                placeholder = { Text("ws://192.168.1.100:9821") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            SettingsSwitch(
                title = "Auto Reconnect",
                subtitle = "Reconnect automatically when connection drops",
                checked = autoReconnect,
                onCheckedChange = { autoReconnect = it }
            )

            HorizontalDivider()

            // Display settings
            Text(
                text = "Display",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            SettingsSwitch(
                title = "Keep Screen On",
                subtitle = "Prevent screen from turning off during sessions",
                checked = keepScreenOn,
                onCheckedChange = { keepScreenOn = it }
            )

            SettingsSwitch(
                title = "Notifications",
                subtitle = "Get notified for approval requests and errors",
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
            )

            HorizontalDivider()

            // About
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Version", style = MaterialTheme.typography.bodyMedium)
                        Text("0.1.0", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Protocol", style = MaterialTheme.typography.bodyMedium)
                        Text("v1", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
