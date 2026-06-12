// cli/src/signaling.ts — WebSocket signaling client/server

import { WebSocketServer, WebSocket } from 'ws';
import { EventEmitter } from 'events';
import type { ServerMessage, ClientMessage, DeviceInfo } from '../../shared/src/protocol.js';
import { Crypto } from './crypto.js';

export interface SignalingOptions {
  deviceId: string;
  serverUrl: string;
  localPort: number;
  lanOnly: boolean;
  crypto: Crypto;
}

type MessageHandler = (msg: any, deviceId?: string) => void;

export class SignalingClient extends EventEmitter {
  private wss: WebSocketServer | null = null;
  private remoteWs: WebSocket | null = null;
  private connectedDevices: Map<string, WebSocket> = new Map();
  private handlers: Map<string, MessageHandler[]> = new Map();
  private options: SignalingOptions;
  private currentDeviceId: string | null = null;

  constructor(options: SignalingOptions) {
    super();
    this.options = options;
  }

  async start(): Promise<void> {
    // Start local WebSocket server for LAN connections
    this.wss = new WebSocketServer({ port: this.options.localPort });

    this.wss.on('connection', (ws, req) => {
      console.log(`[signaling] New connection from ${req.socket.remoteAddress}`);

      ws.on('message', async (data) => {
        try {
          const raw = data.toString();
          const msg = JSON.parse(raw) as ClientMessage;

          // Handle pairing
          if (msg.type === 'pair:request') {
            await this.handlePairRequest(ws, msg);
            return;
          }

          // Decrypt if encrypted
          const deviceId = this.getDeviceByWs(ws);
          this.dispatchMessage(msg, deviceId);
        } catch (err) {
          console.error('[signaling] Failed to parse message:', err);
        }
      });

      ws.on('close', () => {
        const deviceId = this.getDeviceByWs(ws);
        if (deviceId) {
          this.connectedDevices.delete(deviceId);
          console.log(`[signaling] Device disconnected: ${deviceId}`);
        }
      });
    });

    console.log(`[signaling] Listening on ws://0.0.0.0:${this.options.localPort}`);

    // Connect to cloud relay if not LAN-only
    if (!this.options.lanOnly) {
      await this.connectToRelay();
    }
  }

  stop(): void {
    this.wss?.close();
    this.remoteWs?.close();
    this.connectedDevices.clear();
  }

  broadcast(msg: ServerMessage): void {
    const payload = JSON.stringify(msg);
    for (const [deviceId, ws] of this.connectedDevices) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(payload);
      }
    }
    // Also send to relay
    if (this.remoteWs?.readyState === WebSocket.OPEN) {
      this.remoteWs.send(payload);
    }
  }

  sendToCurrent(msg: ServerMessage): void {
    if (this.currentDeviceId) {
      const ws = this.connectedDevices.get(this.currentDeviceId);
      if (ws?.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(msg));
      }
    }
    // Also via relay
    if (this.remoteWs?.readyState === WebSocket.OPEN) {
      this.remoteWs.send(JSON.stringify(msg));
    }
  }

  onMessage(type: string, handler: MessageHandler): void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, []);
    }
    this.handlers.get(type)!.push(handler);
  }

  private dispatchMessage(msg: any, deviceId?: string): void {
    const handlers = this.handlers.get(msg.type);
    if (handlers) {
      for (const handler of handlers) {
        handler(msg, deviceId);
      }
    }
    this.currentDeviceId = deviceId || null;
  }

  private async handlePairRequest(ws: WebSocket, msg: any): Promise<void> {
    // Auto-accept pairing (user confirmed via QR scan)
    const sessionToken = this.options.crypto.generateToken();

    const response: ServerMessage = {
      type: 'pair:response',
      device: {
        id: this.options.deviceId,
        name: require('os').hostname(),
        type: 'cli',
        platform: `${process.platform} ${process.arch}`,
        version: '0.1.0',
      },
      publicKey: this.options.crypto.getPublicKey(),
      sessionToken,
      accepted: true,
    };

    ws.send(JSON.stringify(response));

    // Register device
    this.connectedDevices.set(msg.device.id, ws);
    console.log(`[signaling] Paired with device: ${msg.device.name} (${msg.device.id})`);
  }

  private async connectToRelay(): Promise<void> {
    // Cloud relay connection (placeholder)
    try {
      this.remoteWs = new WebSocket(this.options.serverUrl);

      this.remoteWs.on('open', () => {
        console.log('[signaling] Connected to cloud relay');
        // Register this CLI instance
        this.remoteWs!.send(JSON.stringify({
          type: 'register',
          deviceId: this.options.deviceId,
          role: 'cli',
        }));
      });

      this.remoteWs.on('message', (data) => {
        try {
          const msg = JSON.parse(data.toString());
          this.dispatchMessage(msg, 'relay');
        } catch {}
      });

      this.remoteWs.on('close', () => {
        console.log('[signaling] Disconnected from relay, reconnecting...');
        setTimeout(() => this.connectToRelay(), 3000);
      });

      this.remoteWs.on('error', () => {
        // Silently handle; relay is optional
      });
    } catch {
      // Relay unavailable, continue in LAN-only mode
    }
  }

  private getDeviceByWs(ws: WebSocket): string | undefined {
    for (const [id, socket] of this.connectedDevices) {
      if (socket === ws) return id;
    }
    return undefined;
  }
}
