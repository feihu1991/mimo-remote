// cli/src/utils.ts — Utility functions

import * as os from 'os';
import * as crypto from 'crypto';
import qrcode from 'qrcode-terminal';

/**
 * Generate a unique device ID based on machine info
 */
export function generateDeviceId(): string {
  const networkInterfaces = os.networkInterfaces();
  let mac = '';

  for (const [name, addresses] of Object.entries(networkInterfaces)) {
    if (addresses) {
      for (const addr of addresses) {
        if (addr.mac && addr.mac !== '00:00:00:00:00:00') {
          mac = addr.mac;
          break;
        }
      }
    }
    if (mac) break;
  }

  const hash = crypto
    .createHash('sha256')
    .update(mac + os.hostname() + os.platform())
    .digest('hex')
    .slice(0, 12);

  return `mimo_${hash}`;
}

/**
 * Generate QR code for pairing
 */
export function generatePairingQR(
  pairInfo: { publicKey: string; secretKey: string },
  deviceId: string,
  port: number
): void {
  // Find local IP
  const localIp = getLocalIP();

  const pairingData = JSON.stringify({
    v: 1,
    type: 'mimo_remote_pair',
    device: deviceId,
    host: localIp,
    port,
    key: pairInfo.publicKey,
    ts: Date.now(),
  });

  qrcode.generate(pairingData, { small: true }, (qr: string) => {
    console.log(qr);
  });

  console.log(`  Or connect manually: ws://${localIp}:${port}`);
}

/**
 * Get local IP address
 */
export function getLocalIP(): string {
  const interfaces = os.networkInterfaces();
  for (const [name, addresses] of Object.entries(interfaces)) {
    if (addresses) {
      for (const addr of addresses) {
        if (addr.family === 'IPv4' && !addr.internal) {
          return addr.address;
        }
      }
    }
  }
  return '127.0.0.1';
}

/**
 * Format bytes to human-readable
 */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
}
