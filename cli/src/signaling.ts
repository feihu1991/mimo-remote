// cli/src/signaling.ts — WebSocket signaling client/server

import { WebSocketServer, WebSocket } from 'ws';
import { EventEmitter } from 'events';
import type { ServerMessage, DeviceInfo } from './protocol.js';
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
  private connectedDevices: Map<string, { ws: WebSocket; device: DeviceInfo }> = new Map();
  private wsDeviceMap: Map<WebSocket, string> = new Map();
  private handlers: Map<string, MessageHandler[]> = new Map();
  private options: SignalingOptions;
  private currentDeviceId: string | null = null;

  constructor(options: SignalingOptions) {
    super();
    this.options = options;
  }

  async start(): Promise<void> {
    this.wss = new WebSocketServer({ port: this.options.localPort });

    this.wss.on('connection', (ws, req) => {
      const ip = req.socket.remoteAddress;
      console.log(`[signaling] New connection from ${ip}`);

      ws.on('message', async (data) => {
        try {
          const msg = JSON.parse(data.toString()) as any;
          const deviceId = this.wsDeviceMap.get(ws);

          switch (msg.type) {
            case 'register':
              this.handleRegister(ws, msg);
              break;

            case 'pair:request':
              this.handlePairRequest(ws, msg);
              break;

            default:
              this.dispatchMessage(msg, deviceId);
              break;
          }
        } catch (err) {
          console.error('[signaling] Failed to parse message:', err);
        }
      });

      ws.on('close', () => {
        const deviceId = this.wsDeviceMap.get(ws);
        if (deviceId) {
          this.connectedDevices.delete(deviceId);
          this.wsDeviceMap.delete(ws);
          console.log(`[signaling] Device disconnected: ${deviceId}`);
        }
      });
    });

    console.log(`[signaling] Listening on ws://0.0.0.0:${this.options.localPort}`);

    if (!this.options.lanOnly) {
      await this.connectToRelay();
    }
  }

  stop(): void {
    this.wss?.close();
    this.remoteWs?.close();
    this.connectedDevices.clear();
    this.wsDeviceMap.clear();
  }

  broadcast(msg: ServerMessage): void {
    const payload = JSON.stringify(msg);
    for (const [_, entry] of this.connectedDevices) {
      if (entry.ws.readyState === WebSocket.OPEN) {
        entry.ws.send(payload);
      }
    }
    if (this.remoteWs?.readyState === WebSocket.OPEN) {
      this.remoteWs.send(payload);
    }
  }

  sendToCurrent(msg: ServerMessage): void {
    if (this.currentDeviceId) {
      const entry = this.connectedDevices.get(this.currentDeviceId);
      if (entry?.ws.readyState === WebSocket.OPEN) {
        entry.ws.send(JSON.stringify(msg));
      }
    }
  }

  onMessage(type: string, handler: MessageHandler): void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, []);
    }
    this.handlers.get(type)!.push(handler);
  }

  private handleRegister(ws: WebSocket, msg: any): void {
    const device: DeviceInfo = {
      id: msg.deviceId,
      name: msg.deviceId,
      type: msg.role,
      platform: 'unknown',
      version: '0.1.0',
    };
    this.connectedDevices.set(msg.deviceId, { ws, device });
    this.wsDeviceMap.set(ws, msg.deviceId);
    console.log(`[signaling] Registered device: ${msg.deviceId} (${msg.role})`);
  }

  private handlePairRequest(ws: WebSocket, msg: any): void {
    const sessionToken = this.options.crypto.generateToken();
    const os = require('os');

    const response = {
      type: 'pair:response' as const,
      device: {
        id: this.options.deviceId,
        name: os.hostname(),
        type: 'cli' as const,
        platform: `${process.platform} ${process.arch}`,
        version: '0.1.0',
      },
      publicKey: this.options.crypto.getPublicKey(),
      sessionToken,
      accepted: true,
    };

    ws.send(JSON.stringify(response));

    const mobileDevice: DeviceInfo = msg.device;
    this.connectedDevices.set(msg.device.id, { ws, device: mobileDevice });
    this.wsDeviceMap.set(ws, msg.device.id);
    this.currentDeviceId = msg.device.id;
    console.log(`[signaling] Paired with: ${mobileDevice.name} (${mobileDevice.id})`);

    const sessionStart = {
      type: 'session:start' as const,
      sessionId: `session_${Date.now()}`,
      cliDevice: response.device,
      workingDirectory: process.cwd(),
      mimoVersion: 'demo',
    };
    setTimeout(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(sessionStart));
      }
    }, 50);
  }

  private dispatchMessage(msg: any, deviceId?: string): void {
    const handlers = this.handlers.get(msg.type);
    if (handlers) {
      for (const handler of handlers) {
        handler(msg, deviceId);
      }
    }
    if (deviceId) {
      this.currentDeviceId = deviceId;
    }
  }

  private async connectToRelay(): Promise<void> {
    try {
      this.remoteWs = new WebSocket(this.options.serverUrl);

      this.remoteWs.on('open', () => {
        console.log('[signaling] Connected to cloud relay');
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

      this.remoteWs.on('error', () => {});
    } catch {}
  }
}
