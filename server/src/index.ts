// server/src/index.ts — Signaling relay server for MiMo Remote

import express from 'express';
import { WebSocketServer, WebSocket } from 'ws';
import { createServer } from 'http';

const PORT = parseInt(process.env.PORT || '9822');

// ─── Types ────────────────────────────────────────────────────

interface RegisteredDevice {
  id: string;
  role: 'cli' | 'android' | 'web';
  ws: WebSocket;
  pairedWith: string | null;
  registeredAt: number;
}

interface Room {
  cliDeviceId: string;
  mobileDeviceId: string | null;
  createdAt: number;
}

// ─── State ────────────────────────────────────────────────────

const devices = new Map<string, RegisteredDevice>();
const rooms = new Map<string, Room>();

// ─── HTTP Server ──────────────────────────────────────────────

const app = express();
app.use(express.json());

app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    devices: devices.size,
    rooms: rooms.size,
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
    activeRooms: rooms.size,
  });
});

const server = createServer(app);

// ─── WebSocket Server ─────────────────────────────────────────

const wss = new WebSocketServer({ server });

wss.on('connection', (ws, req) => {
  const ip = req.socket.remoteAddress;
  console.log(`[ws] New connection from ${ip}`);

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
          console.log(`[ws] Registered device: ${msg.deviceId} (${msg.role})`);

          // Auto-pair with waiting counterpart
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
          // Relay message to paired device
          relayMessage(msg, deviceId);
          break;
      }
    } catch (err) {
      console.error('[ws] Failed to parse message:', err);
    }
  });

  ws.on('close', () => {
    if (deviceId) {
      const device = devices.get(deviceId);
      if (device?.pairedWith) {
        // Notify paired device
        const peer = devices.get(device.pairedWith);
        if (peer?.ws.readyState === WebSocket.OPEN) {
          peer.ws.send(JSON.stringify({
            type: 'session:end',
            sessionId: 'current',
            reason: 'peer_disconnected',
          }));
        }
        // Clean up room
        for (const [roomId, room] of rooms) {
          if (room.cliDeviceId === deviceId || room.mobileDeviceId === deviceId) {
            rooms.delete(roomId);
          }
        }
      }
      devices.delete(deviceId);
      console.log(`[ws] Device disconnected: ${deviceId}`);
    }
  });
});

// ─── Pairing Logic ────────────────────────────────────────────

function autoPair(mobileDeviceId: string): void {
  // Find an unpaired CLI device
  for (const [id, device] of devices) {
    if (device.role === 'cli' && !device.pairedWith) {
      // Pair them
      device.pairedWith = mobileDeviceId;
      const mobile = devices.get(mobileDeviceId);
      if (mobile) {
        mobile.pairedWith = id;
      }

      const roomId = `room_${Date.now()}`;
      rooms.set(roomId, {
        cliDeviceId: id,
        mobileDeviceId: mobileDeviceId,
        createdAt: Date.now(),
      });

      console.log(`[pair] Auto-paired: ${id} <-> ${mobileDeviceId}`);

      // Notify both devices
      const pairMsg = JSON.stringify({
        type: 'session:start',
        sessionId: roomId,
        cliDevice: { id, role: 'cli' },
      });

      if (device.ws.readyState === WebSocket.OPEN) {
        device.ws.send(pairMsg);
      }
      if (mobile?.ws.readyState === WebSocket.OPEN) {
        mobile.ws.send(pairMsg);
      }
      return;
    }
  }
  console.log(`[pair] No available CLI device for ${mobileDeviceId}`);
}

function handlePairRequest(msg: any): void {
  // Forward to target device
  const target = devices.get(msg.targetDeviceId);
  if (target?.ws.readyState === WebSocket.OPEN) {
    target.ws.send(JSON.stringify(msg));
  }
}

function handlePairResponse(msg: any): void {
  // Forward to requester
  const requester = devices.get(msg.targetDeviceId);
  if (requester?.ws.readyState === WebSocket.OPEN) {
    requester.ws.send(JSON.stringify(msg));
  }
}

// ─── Message Relay ────────────────────────────────────────────

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
