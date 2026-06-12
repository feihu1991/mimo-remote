// shared/src/protocol.ts
// WebSocket message protocol between CLI ↔ Server ↔ Android App

// ─── Device & Auth ────────────────────────────────────────────

export interface DeviceInfo {
  id: string;
  name: string;
  type: 'cli' | 'android' | 'web';
  platform: string;         // e.g. "linux", "macos", "android-14"
  version: string;          // app version
}

export interface PairRequest {
  type: 'pair:request';
  device: DeviceInfo;
  publicKey: string;        // Curve25519 public key, base64
}

export interface PairResponse {
  type: 'pair:response';
  device: DeviceInfo;
  publicKey: string;
  sessionToken: string;     // short-lived token for this pairing
  accepted: boolean;
}

// ─── Control Messages (E2E encrypted) ─────────────────────────

export interface MimoOutput {
  type: 'mimo:output';
  data: string;             // raw terminal output (ANSI)
  timestamp: number;
}

export interface MimoStatus {
  type: 'mimo:status';
  status: 'idle' | 'thinking' | 'executing' | 'waiting_approval' | 'error';
  detail?: string;
}

export interface MimoMemoryUpdate {
  type: 'mimo:memory';
  files: MemoryFile[];
}

export interface MemoryFile {
  path: string;             // e.g. "MEMORY.md", "checkpoint.md"
  content: string;
  lastModified: number;
}

export interface TaskUpdate {
  type: 'mimo:task:update';
  tasks: TaskNode[];
}

export interface TaskNode {
  id: string;               // e.g. "T1", "T1.1"
  title: string;
  status: 'pending' | 'in_progress' | 'done' | 'blocked';
  children?: TaskNode[];
}

export interface UserInput {
  type: 'user:input';
  data: string;
  timestamp: number;
}

export interface UserCommand {
  type: 'user:command';
  command: 'goal' | 'dream' | 'distill' | 'compose' | 'cancel' | 'interrupt';
  args?: string;
}

export interface AgentSwitch {
  type: 'user:agent_switch';
  agent: 'build' | 'plan' | 'compose';
}

// ─── Media Signaling (WebRTC) ─────────────────────────────────

export interface MediaOffer {
  type: 'media:offer';
  sdp: string;
  mediaType: 'audio' | 'video' | 'screen_share';
}

export interface MediaAnswer {
  type: 'media:answer';
  sdp: string;
}

export interface MediaCandidate {
  type: 'media:candidate';
  candidate: string;
  sdpMid: string;
  sdpMLineIndex: number;
}

export interface MediaControl {
  type: 'media:control';
  action: 'mute_audio' | 'unmute_audio' | 'mute_video' | 'unmute_video' | 'end_call';
}

// ─── Session ──────────────────────────────────────────────────

export interface SessionStart {
  type: 'session:start';
  sessionId: string;
  cliDevice: DeviceInfo;
  workingDirectory: string;
  mimoVersion: string;
}

export interface SessionEnd {
  type: 'session:end';
  sessionId: string;
  reason: string;
}

export interface SessionResume {
  type: 'session:resume';
  sessionId: string;
  checkpoint?: MemoryFile;
  memory?: MemoryFile;
  recentOutput?: string;    // last N lines of terminal output
}

// ─── Heartbeat ────────────────────────────────────────────────

export interface Ping {
  type: 'ping';
  timestamp: number;
}

export interface Pong {
  type: 'pong';
  timestamp: number;
}

// ─── Union Types ──────────────────────────────────────────────

export type ServerMessage =
  | PairRequest
  | PairResponse
  | MimoOutput
  | MimoStatus
  | MimoMemoryUpdate
  | TaskUpdate
  | SessionStart
  | SessionEnd
  | SessionResume
  | MediaOffer
  | MediaAnswer
  | MediaCandidate
  | MediaControl
  | Ping
  | Pong;

export type ClientMessage =
  | PairRequest
  | PairResponse
  | UserInput
  | UserCommand
  | AgentSwitch
  | MediaOffer
  | MediaAnswer
  | MediaCandidate
  | MediaControl
  | Ping
  | Pong;

// ─── Constants ────────────────────────────────────────────────

export const PROTOCOL_VERSION = 1;
export const DEFAULT_SERVER_PORT = 9821;
export const HEARTBEAT_INTERVAL_MS = 15_000;
export const RECONNECT_DELAY_MS = 3_000;
export const MAX_RECONNECT_ATTEMPTS = 10;
