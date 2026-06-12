// cli/src/mimo.ts — MiMo Code process wrapper with PTY

import { EventEmitter } from 'events';
import { spawn as spawnChild } from 'child_process';
import type { MimoStatus } from './protocol.js';

export type MimoStatusValue = MimoStatus['status'];

export class MimoProcess extends EventEmitter {
  private proc: any = null; // node-pty IPty
  private mimoPath: string;
  private currentStatus: MimoStatusValue = 'idle';
  private outputBuffer: string = '';
  private flushTimer: ReturnType<typeof setTimeout> | null = null;
  private demoMode: boolean = false;
  private ptyModule: any = null;

  constructor(mimoPath: string = 'mimo') {
    super();
    this.mimoPath = mimoPath;
  }

  async start(): Promise<void> {
    // Check if mimo binary exists
    const mimoExists = await this.checkBinaryExists(this.mimoPath);

    if (!mimoExists) {
      console.log(`[mimo] Binary '${this.mimoPath}' not found, entering demo mode`);
      this.enterDemoMode();
      return;
    }

    // Try to load node-pty
    try {
      this.ptyModule = await import('node-pty');
    } catch {
      console.log('[mimo] node-pty not available, entering demo mode');
      this.enterDemoMode();
      return;
    }

    return new Promise((resolve) => {
      try {
        this.proc = this.ptyModule.spawn(this.mimoPath, [], {
          name: 'xterm-256color',
          cols: 120,
          rows: 40,
          cwd: process.cwd(),
          env: {
            ...process.env,
            TERM: 'xterm-256color',
            FORCE_COLOR: '1',
            MIMO_REMOTE: '1',
          },
        });

        let hasOutput = false;
        let resolved = false;

        this.proc.onData((data: string) => {
          if (!hasOutput) {
            hasOutput = true;
          }
          this.outputBuffer += data;
          this.detectStatus(data);

          if (this.flushTimer) clearTimeout(this.flushTimer);
          this.flushTimer = setTimeout(() => this.flushOutput(), 16);

          if (!resolved) {
            resolved = true;
            resolve();
          }
        });

        this.proc.onExit(({ exitCode }: { exitCode: number }) => {
          if (!hasOutput) {
            // Process exited without output — probably not a real mimo session
            console.log(`[mimo] Process exited immediately (code ${exitCode}), entering demo mode`);
            this.proc = null;
            this.enterDemoMode();
          }
          if (!resolved) {
            resolved = true;
            resolve();
          }
          if (hasOutput) {
            this.emit('exit', exitCode);
            this.setStatus('idle', `Process exited with code ${exitCode}`);
          }
        });

        // Timeout: if no output in 5s, assume demo mode
        setTimeout(() => {
          if (!resolved) {
            resolved = true;
            if (!hasOutput) {
              console.log('[mimo] No output received, entering demo mode');
              this.proc?.kill();
              this.proc = null;
              this.enterDemoMode();
            }
            resolve();
          }
        }, 5000);
      } catch (err) {
        console.log(`[mimo] Failed to spawn: ${err}, entering demo mode`);
        this.enterDemoMode();
        resolve();
      }
    });
  }

  write(data: string): void {
    if (this.demoMode) {
      this.simulateResponse(data);
      return;
    }
    if (!this.proc) {
      throw new Error('MiMo process not running');
    }
    this.proc.write(data);
  }

  executeCommand(command: string, args?: string): void {
    const cmd = `/${command}${args ? ' ' + args : ''}\r`;
    this.write(cmd);
  }

  switchAgent(_agent: string): void {
    this.write('\t');
  }

  resize(cols: number, rows: number): void {
    if (this.proc) this.proc.resize(cols, rows);
  }

  kill(): void {
    if (this.proc) {
      this.proc.kill();
      this.proc = null;
    }
  }

  isDemoMode(): boolean {
    return this.demoMode;
  }

  onOutput(callback: (data: string) => void): void {
    this.on('output', callback);
  }

  onStatusChange(callback: (status: MimoStatusValue, detail?: string) => void): void {
    this.on('status', callback);
  }

  private flushOutput(): void {
    if (this.outputBuffer.length > 0) {
      this.emit('output', this.outputBuffer);
      this.outputBuffer = '';
    }
  }

  private enterDemoMode(): void {
    this.demoMode = true;
    const welcome = [
      '\r\n\x1b[36m╔══════════════════════════════════════╗\x1b[0m',
      '\x1b[36m║  MiMo Remote — Demo Mode             ║\x1b[0m',
      '\x1b[36m║  (mimo binary not found)              ║\x1b[0m',
      '\x1b[36m╚══════════════════════════════════════╝\x1b[0m',
      '',
      '\x1b[33m  MiMo Code is not installed on this machine.\x1b[0m',
      '\x1b[33m  Install: npm install -g @mimo-ai/cli\x1b[0m',
      '',
      '\x1b[90m  CLI running in demo mode. Connect from your phone.\x1b[0m',
      '\x1b[90m  Type anything to see simulated output.\x1b[0m',
      '',
      '\x1b[32m❯ \x1b[0m',
    ].join('\r\n');

    // Defer emit so listeners can be registered first
    setTimeout(() => {
      this.emit('output', welcome);
      this.setStatus('idle', 'Demo mode');
    }, 100);
  }

  private simulateResponse(input: string): void {
    const trimmed = input.trim();

    // Echo the input
    const echo = `\r\n\x1b[32m❯ \x1b[0m${trimmed}\r\n`;
    this.emit('output', echo);

    if (!trimmed) return;

    // Simulate thinking
    this.setStatus('thinking');

    setTimeout(() => {
      this.setStatus('executing');

      let response = '';
      if (trimmed.startsWith('/')) {
        const cmd = trimmed.split(' ')[0].slice(1);
        response = `\x1b[36m[Command] /${cmd}\x1b[0m\r\n` +
          `\x1b[90m  Command sent to MiMo Code (demo mode)\x1b[0m\r\n`;
      } else {
        response = `\x1b[36m[MiMo Code]\x1b[0m Received: "${trimmed}"\r\n` +
          `\x1b[90m  Demo response. Install MiMo Code for real output.\x1b[0m\r\n`;
      }

      this.emit('output', response);
      this.setStatus('idle');
    }, 500 + Math.random() * 1000);
  }

  private detectStatus(data: string): void {
    const patterns: [RegExp, MimoStatusValue][] = [
      [/[Tt]hinking\.?\.{0,3}/, 'thinking'],
      [/[Ee]xecuting\.?\.{0,3}/, 'executing'],
      [/[Dd]o you want to (allow|proceed|approve)/, 'waiting_approval'],
      [/[Ee]rror[: ]/, 'error'],
    ];

    for (const [pattern, status] of patterns) {
      if (pattern.test(data)) {
        this.setStatus(status);
        return;
      }
    }

    if (/[>$#]\s*$/.test(data.trimEnd())) {
      this.setStatus('idle');
    }
  }

  private setStatus(status: MimoStatusValue, detail?: string): void {
    if (status !== this.currentStatus) {
      this.currentStatus = status;
      this.emit('status', status, detail);
    }
  }

  private checkBinaryExists(name: string): Promise<boolean> {
    return new Promise((resolve) => {
      const check = spawnChild('which', [name], { stdio: 'ignore' });
      check.on('close', (code) => resolve(code === 0));
      check.on('error', () => resolve(false));
    });
  }
}
