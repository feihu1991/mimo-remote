// cli/src/webrtc.ts — Media bridge for voice/video calls
// Uses wrtc (node-webrtc) for server-side WebRTC

import { EventEmitter } from 'events';

export interface MediaSession {
  id: string;
  peerConnection: any; // RTCPeerConnection
  audioTrack?: any;
  videoTrack?: any;
  screenTrack?: any;
}

export class MediaBridge extends EventEmitter {
  private sessions: Map<string, MediaSession> = new Map();
  private audioSink: NodeJS.WritableStream | null = null;

  constructor() {
    super();
  }

  /**
   * Handle incoming WebRTC offer from mobile client
   * Returns SDP answer
   */
  async handleOffer(sdp: string, mediaType: string): Promise<string> {
    try {
      // Dynamic import wrtc (optional dependency)
      const wrtc = await import('wrtc').catch(() => null);
      if (!wrtc) {
        console.warn('[media] wrtc not installed, voice/video disabled');
        console.warn('[media] Install with: npm install wrtc');
        return '';
      }

      const sessionId = `session_${Date.now()}`;
      const pc = new wrtc.RTCPeerConnection({
        iceServers: [
          { urls: 'stun:stun.l.google.com:19302' },
          { urls: 'stun:stun1.l.google.com:19302' },
        ],
      });

      // Handle ICE candidates
      pc.onicecandidate = (event: any) => {
        if (event.candidate) {
          this.emit('ice_candidate', {
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid,
            sdpMLineIndex: event.candidate.sdpMLineIndex,
          });
        }
      };

      // Handle incoming tracks (audio from phone)
      pc.ontrack = (event: any) => {
        console.log(`[media] Received track: ${event.track.kind}`);
        this.emit('remote_track', {
          sessionId,
          kind: event.track.kind,
          track: event.track,
        });

        if (event.track.kind === 'audio') {
          // Route phone audio to speakers or to MiMo ASR
          this.handleRemoteAudio(sessionId, event.track);
        }
      };

      // Add local audio capture (system audio / microphone)
      if (mediaType === 'audio' || mediaType === 'video') {
        try {
          const audioSource = await this.createAudioCapture();
          if (audioSource) {
            pc.addTrack(audioSource, new wrtc.MediaStream());
          }
        } catch (err) {
          console.warn('[media] Could not capture audio:', err);
        }
      }

      // Set remote description (offer)
      await pc.setRemoteDescription(new wrtc.RTCSessionDescription({
        type: 'offer',
        sdp,
      }));

      // Create answer
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);

      this.sessions.set(sessionId, {
        id: sessionId,
        peerConnection: pc,
      });

      return pc.localDescription?.sdp ?? '';
    } catch (err) {
      console.error('[media] Failed to handle offer:', err);
      throw err;
    }
  }

  async addIceCandidate(candidate: string, sdpMid: string, sdpMLineIndex: number): Promise<void> {
    // Add to the most recent session
    const session = Array.from(this.sessions.values()).pop();
    if (session) {
      await session.peerConnection.addIceCandidate({
        candidate,
        sdpMid,
        sdpMLineIndex,
      });
    }
  }

  handleControl(action: string): void {
    switch (action) {
      case 'end_call':
        this.endAllSessions();
        break;
      case 'mute_audio':
        this.setAudioMuted(true);
        break;
      case 'unmute_audio':
        this.setAudioMuted(false);
        break;
    }
  }

  destroy(): void {
    this.endAllSessions();
  }

  private async createAudioCapture(): Promise<any> {
    // Platform-specific audio capture
    // On macOS: use CoreAudio via node-native-audio
    // On Linux: use PulseAudio/PipeWire
    // Placeholder: return null, real impl needs native bindings
    return null;
  }

  private handleRemoteAudio(sessionId: string, track: any): void {
    // Route remote audio to system output
    // In future: pipe to MiMo ASR for voice-to-text
    console.log(`[media] Remote audio active for session ${sessionId}`);
  }

  private endAllSessions(): void {
    for (const [id, session] of this.sessions) {
      try {
        session.peerConnection.close();
      } catch {}
      this.sessions.delete(id);
    }
    console.log('[media] All sessions ended');
  }

  private setAudioMuted(muted: boolean): void {
    for (const session of this.sessions.values()) {
      if (session.audioTrack) {
        session.audioTrack.enabled = !muted;
      }
    }
  }
}
