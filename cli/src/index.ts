#!/usr/bin/env node
// cli/src/index.ts — Main entry point for mimo-remote CLI

import { Command } from 'commander';
import chalk from 'chalk';
import { MimoProcess } from './mimo.js';
import { SignalingClient } from './signaling.js';
import { MediaBridge } from './webrtc.js';
import { MemoryWatcher } from './memory.js';
import { Crypto } from './crypto.js';
import { generatePairingQR, generateDeviceId } from './utils.js';

const VERSION = '0.1.0';

const program = new Command();

program
  .name('mimo-remote')
  .description('Remote control MiMo Code from your Android phone')
  .version(VERSION);

program
  .command('start', { isDefault: true })
  .description('Start MiMo Code with remote control enabled')
  .option('-s, --server <url>', 'Signaling server URL', 'wss://relay.mimo-remote.com')
  .option('-p, --port <port>', 'Local WebSocket port for direct LAN connection', '9821')
  .option('-m, --mimo-path <path>', 'Path to MiMo Code binary', 'mimo')
  .option('--no-qr', 'Skip QR code display')
  .option('--lan-only', 'LAN-only mode, no cloud relay')
  .action(async (opts) => {
    console.log(chalk.bold.cyan('\n  ╔══════════════════════════════════╗'));
    console.log(chalk.bold.cyan('  ║      MiMo Remote  v' + VERSION + '        ║'));
    console.log(chalk.bold.cyan('  ╚══════════════════════════════════╝\n'));

    const deviceId = generateDeviceId();
    const crypto = new Crypto();
    await crypto.init();

    console.log(chalk.gray(`  Device ID: ${deviceId}`));
    console.log(chalk.gray(`  Mode: ${opts.lanOnly ? 'LAN only' : 'LAN + Cloud relay'}\n`));

    // 1. Start MiMo Code process
    console.log(chalk.yellow('  ▸ Starting MiMo Code...'));
    const mimo = new MimoProcess(opts.mimoPath);
    await mimo.start();
    console.log(chalk.green('  ✓ MiMo Code running\n'));

    // 2. Start memory watcher
    const memory = new MemoryWatcher(process.cwd());
    memory.start();

    // 3. Start signaling
    const signaling = new SignalingClient({
      deviceId,
      serverUrl: opts.server,
      localPort: parseInt(opts.port),
      lanOnly: opts.lanOnly,
      crypto,
    });

    // 4. Wire up message routing
    mimo.onOutput((data) => {
      signaling.broadcast({
        type: 'mimo:output',
        data,
        timestamp: Date.now(),
      });
    });

    mimo.onStatusChange((status, detail) => {
      signaling.broadcast({
        type: 'mimo:status',
        status,
        detail,
      });
    });

    memory.onUpdate((files) => {
      signaling.broadcast({
        type: 'mimo:memory',
        files,
      });
    });

    signaling.onMessage('user:input', (msg) => {
      mimo.write(msg.data);
    });

    signaling.onMessage('user:command', (msg) => {
      mimo.executeCommand(msg.command, msg.args);
    });

    signaling.onMessage('user:agent_switch', (msg) => {
      mimo.switchAgent(msg.agent);
    });

    // 5. Media bridge for voice/video
    const media = new MediaBridge();
    signaling.onMessage('media:offer', async (msg) => {
      const answer = await media.handleOffer(msg.sdp, msg.mediaType);
      signaling.sendToCurrent({
        type: 'media:answer',
        sdp: answer,
      });
    });

    signaling.onMessage('media:candidate', async (msg) => {
      await media.addIceCandidate(msg.candidate, msg.sdpMid, msg.sdpMLineIndex);
    });

    signaling.onMessage('media:control', (msg) => {
      media.handleControl(msg.action);
    });

    // 6. Start signaling server
    await signaling.start();

    // Show pairing QR
    if (opts.qr !== false) {
      const pairInfo = crypto.getPairingInfo();
      console.log(chalk.bold('  Scan to connect:\n'));
      generatePairingQR(pairInfo, deviceId, parseInt(opts.port));
      console.log('');
    }

    console.log(chalk.green('  ✓ Ready! Waiting for phone connection...\n'));

    // Handle graceful shutdown
    const shutdown = async () => {
      console.log(chalk.gray('\n  Shutting down...'));
      mimo.kill();
      media.destroy();
      signaling.stop();
      process.exit(0);
    };

    process.on('SIGINT', shutdown);
    process.on('SIGTERM', shutdown);
  });

program
  .command('pair')
  .description('Show pairing QR code for an already-running instance')
  .option('-p, --port <port>', 'Local port', '9821')
  .action(async (opts) => {
    const crypto = new Crypto();
    await crypto.init();
    const pairInfo = crypto.getPairingInfo();
    console.log(chalk.bold('\n  Scan to connect:\n'));
    generatePairingQR(pairInfo, 'cli', parseInt(opts.port));
  });

program
  .command('status')
  .description('Check connection status')
  .action(() => {
    // TODO: connect to running instance and query status
    console.log(chalk.gray('  Not yet implemented'));
  });

program.parse();
