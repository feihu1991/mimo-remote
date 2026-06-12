package com.mimo.remote.ui.session

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mimo.remote.data.model.CallState
import com.mimo.remote.ui.theme.*

@Composable
fun CallOverlay(
    callState: CallState,
    onStartAudioCall: () -> Unit,
    onStartVideoCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.4f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Handle bar
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
            )

            if (!callState.isInCall) {
                // Not in call - show call options
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Voice & Video",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Talk to MiMo Code in real-time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Audio call button
                        CallOptionButton(
                            icon = Icons.Default.Call,
                            label = "Voice",
                            gradient = Brush.linearGradient(listOf(MiMoCyan, MiMoPurple)),
                            onClick = onStartAudioCall
                        )

                        // Video call button
                        CallOptionButton(
                            icon = Icons.Default.Videocam,
                            label = "Video",
                            gradient = Brush.linearGradient(listOf(MiMoPurple, MiMoCyan)),
                            onClick = onStartVideoCall
                        )
                    }
                }
            } else {
                // In call - show controls
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Call status
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(MiMoCyan, MiMoPurple))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (callState.callType == "video") Icons.Default.Videocam else Icons.Default.Call,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (callState.callType == "video") "Video Call" else "Voice Call",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            text = "Connected to MiMo Code",
                            style = MaterialTheme.typography.bodySmall,
                            color = MiMoGreen
                        )
                    }

                    // Call controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Mute
                        CallControlButton(
                            icon = if (callState.isAudioMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label = if (callState.isAudioMuted) "Unmute" else "Mute",
                            isActive = callState.isAudioMuted,
                            onClick = onToggleMute
                        )

                        // End call
                        CallControlButton(
                            icon = Icons.Default.CallEnd,
                            label = "End",
                            isActive = false,
                            containerColor = MiMoRed,
                            onClick = onEndCall
                        )

                        // Speaker
                        CallControlButton(
                            icon = Icons.Default.VolumeUp,
                            label = "Speaker",
                            isActive = false,
                            onClick = { /* TODO */ }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CallOptionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    gradient: Brush,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(gradient)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    containerColor: Color = if (isActive) Color.White else MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) MiMoRed else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
        )
    }
}
