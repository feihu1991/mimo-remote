// server/src/index.ts — Signaling relay server for MiMo Remote

import express from 'express';
import { WebSocketServer, WebSocket } from 'ws';
import { createServer } from 'http';

const PORT = parseInt(process.env.PORT || '9822');

interface RegisteredDevice {
  id: string;
  role: 'cli' | 'android' | 'web';
  ws: WebSocket;
  pairedWith: string | null;
  registeredAt: number;
}

const devices = new Map<string, RegisteredDevice>();

// ─── HTTP Server ──────────────────────────────────────────────

const app = express();
app.use(express.json());

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    devices: devices.size,
    uptime: process.uptime(),
  });
});

app.get('/api/stats', (_req, res) => {
  res.json({
    connectedDevices: Array.from(devices.values()).map(d => ({
      id: d.id,
      role: d.role,
      paired: !!d.pairedWith,
    })),
  });
});

const server = createServer(app);

// ─── WebSocket Server ─────────────────────────────────────────

const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress;
  console.log(`[ws] Connection from ${ip}`);

  let deviceId: string | null = null;

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());

      switch (msg.type) {
        case 'register':
          deviceId = msg.deviceId;
          devices.set(msg.deviceId, {
            id: msg.deviceId,
            role: msg.role,
            ws,
            pairedWith: null,
            registeredAt: Date.now(),
          });
          console.log(`[ws] Registered: ${msg.deviceId} (${msg.role})`);

          if (msg.role === 'android') {
            autoPair(msg.deviceId);
          }
          break;

        case 'pair:request':
          handlePairRequest(msg);
          break;

        case 'pair:response':
          handlePairResponse(msg);
          break;

        default:
          relayMessage(msg, deviceId);
          break;
      }
    } catch (err) {
      console.error('[ws] Parse error:', err);
    }
  });

  ws.on('close', () => {
    if (deviceId) {
      const device = devices.get(deviceId);
      if (device?.pairedWith) {
        const peer = devices.get(device.pairedWith);
        if (peer?.ws.readyState === WebSocket.OPEN) {
          peer.ws.send(JSON.stringify({
            type: 'session:end',
            sessionId: 'current',
            reason: 'peer_disconnected',
          }));
          peer.pairedWith = null;
        }
      }
      devices.delete(deviceId);
      console.log(`[ws] Disconnected: ${deviceId}`);
    }
  });
});

function autoPair(mobileDeviceId: string): void {
  for (const [id, device] of devices) {
    if (device.role === 'cli' && !device.pairedWith) {
      device.pairedWith = mobileDeviceId;
      const mobile = devices.get(mobileDeviceId);
      if (mobile) mobile.pairedWith = id;

      console.log(`[pair] Paired: ${id} <-> ${mobileDeviceId}`);

      const cliInfo = { id, role: 'cli' as const };
      const sessionMsg = JSON.stringify({
        type: 'session:start',
        sessionId: `room_${Date.now()}`,
        cliDevice: cliInfo,
      });

      if (device.ws.readyState === WebSocket.OPEN) device.ws.send(sessionMsg);
      if (mobile?.ws.readyState === WebSocket.OPEN) mobile.ws.send(sessionMsg);
      return;
    }
  }
  console.log(`[pair] No CLI available for ${mobileDeviceId}`);
}

function handlePairRequest(msg: any): void {
  const target = devices.get(msg.targetDeviceId);
  if (target?.ws.readyState === WebSocket.OPEN) {
    target.ws.send(JSON.stringify(msg));
  }
}

function handlePairResponse(msg: any): void {
  const requester = devices.get(msg.targetDeviceId);
  if (requester?.ws.readyState === WebSocket.OPEN) {
    requester.ws.send(JSON.stringify(msg));
  }
}

function relayMessage(msg: any, fromDeviceId: string | null): void {
  if (!fromDeviceId) return;
  const sender = devices.get(fromDeviceId);
  if (!sender?.pairedWith) return;
  const peer = devices.get(sender.pairedWith);
  if (peer?.ws.readyState === WebSocket.OPEN) {
    peer.ws.send(JSON.stringify(msg));
  }
}

// ─── Start ────────────────────────────────────────────────────

server.listen(PORT, () => {
  console.log(`\n  ╔══════════════════════════════════╗`);
  console.log(`  ║   MiMo Remote Relay Server       ║`);
  console.log(`  ║   Port: ${PORT}                     ║`);
  console.log(`  ╚══════════════════════════════════╝\n`);
  console.log(`  Health: http://localhost:${PORT}/health`);
  console.log(`  Stats:  http://localhost:${PORT}/api/stats\n`);
});
