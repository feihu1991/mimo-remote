declare module 'wrtc' {
  export class RTCPeerConnection {
    constructor(config?: RTCConfiguration);
    setRemoteDescription(desc: RTCSessionDescriptionInit): Promise<void>;
    setLocalDescription(desc: RTCSessionDescriptionInit): Promise<void>;
    createAnswer(): Promise<RTCSessionDescriptionInit>;
    addIceCandidate(candidate: RTCIceCandidateInit): Promise<void>;
    addTrack(track: MediaStreamTrack, stream: MediaStream): RTCRtpSender;
    close(): void;
    localDescription: RTCSessionDescription | null;
    onicecandidate: ((event: RTCPeerConnectionIceEvent) => void) | null;
    ontrack: ((event: RTCTrackEvent) => void) | null;
  }

  export class RTCSessionDescription {
    constructor(init: RTCSessionDescriptionInit);
    type: RTCSdpType;
    sdp: string;
  }

  export class MediaStream {
    constructor();
  }

  export interface RTCIceCandidateInit {
    candidate: string;
    sdpMid: string;
    sdpMLineIndex: number;
  }
}
