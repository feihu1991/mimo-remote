package com.mimo.remote.data.model

import kotlinx.serialization.Serializable

// ─── Device ───────────────────────────────────────────────────

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val type: String,  // "cli", "android", "web"
    val platform: String,
    val version: String
)

// ─── Session ──────────────────────────────────────────────────

@Serializable
data class SessionStart(
    val type: String = "session:start",
    val sessionId: String,
    val cliDevice: DeviceInfo? = null,
    val workingDirectory: String = "",
    val mimoVersion: String = ""
)

@Serializable
data class SessionEnd(
    val type: String = "session:end",
    val sessionId: String,
    val reason: String
)

// ─── MiMo Messages ────────────────────────────────────────────

@Serializable
data class MimoOutput(
    val type: String = "mimo:output",
    val data: String,
    val timestamp: Long = 0
)

@Serializable
data class MimoStatus(
    val type: String = "mimo:status",
    val status: String,  // idle, thinking, executing, waiting_approval, error
    val detail: String? = null
)

@Serializable
data class MemoryFile(
    val path: String,
    val content: String,
    val lastModified: Long = 0
)

@Serializable
data class MimoMemoryUpdate(
    val type: String = "mimo:memory",
    val files: List<MemoryFile>
)

@Serializable
data class TaskNode(
    val id: String,
    val title: String,
    val status: String,  // pending, in_progress, done, blocked
    val children: List<TaskNode>? = null
)

@Serializable
data class TaskUpdate(
    val type: String = "mimo:task:update",
    val tasks: List<TaskNode>
)

// ─── User Input ───────────────────────────────────────────────

@Serializable
data class UserInput(
    val type: String = "user:input",
    val data: String,
    val timestamp: Long = 0
)

@Serializable
data class UserCommand(
    val type: String = "user:command",
    val command: String,
    val args: String? = null
)

@Serializable
data class AgentSwitch(
    val type: String = "user:agent_switch",
    val agent: String  // build, plan, compose
)

// ─── Media ────────────────────────────────────────────────────

@Serializable
data class MediaOffer(
    val type: String = "media:offer",
    val sdp: String,
    val mediaType: String  // audio, video, screen_share
)

@Serializable
data class MediaAnswer(
    val type: String = "media:answer",
    val sdp: String
)

@Serializable
data class MediaCandidate(
    val type: String = "media:candidate",
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int
)

@Serializable
data class MediaControl(
    val type: String = "media:control",
    val action: String  // mute_audio, unmute_audio, mute_video, unmute_video, end_call
)

// ─── Pairing ──────────────────────────────────────────────────

@Serializable
data class PairRequest(
    val type: String = "pair:request",
    val device: DeviceInfo,
    val publicKey: String
)

@Serializable
data class PairResponse(
    val type: String = "pair:response",
    val device: DeviceInfo,
    val publicKey: String,
    val sessionToken: String,
    val accepted: Boolean
)

// ─── UI State ─────────────────────────────────────────────────

data class ConnectionState(
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val cliDevice: DeviceInfo? = null,
    val error: String? = null,
    val sessionId: String? = null
)

data class TerminalLine(
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutput: Boolean = true
)

data class CallState(
    val isInCall: Boolean = false,
    val isAudioMuted: Boolean = false,
    val isVideoMuted: Boolean = true,
    val callType: String = "audio"  // audio, video
)
