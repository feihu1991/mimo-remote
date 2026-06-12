// cli/src/mimo.ts — MiMo Code process wrapper with PTY

import { EventEmitter } from 'events';
import * as pty from 'node-pty';
import type { MimoStatus } from '../shared/src/protocol.js';

export type MimoStatusValue = MimoStatus['status'];

export class MimoProcess extends EventEmitter {
  private proc: pty.IPty | null = null;
  private mimoPath: string;
  private currentStatus: MimoStatusValue = 'idle';
  private outputBuffer: string = '';
  private flushTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(mimoPath: string = 'mimo') {
    super();
    this.mimoPath = mimoPath;
  }

  async start(): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        this.proc = pty.spawn(this.mimoPath, [], {
          name: 'xterm-256color',
          cols: 120,
          rows: 40,
          cwd: process.cwd(),
          env: {
            ...process.env,
            TERM: 'xterm-256color',
            FORCE_COLOR: '1',
            // Tell MiMo it's running under remote control
            MIMO_REMOTE: '1',
          },
        });

        let resolved = false;

        this.proc.onData((data: string) => {
          this.outputBuffer += data;

          // Detect MiMo status from output patterns
          this.detectStatus(data);

          // Debounce output flush (batch rapid updates)
          if (this.flushTimer) clearTimeout(this.flushTimer);
          this.flushTimer = setTimeout(() => {
            this.flushOutput();
          }, 16); // ~60fps

          if (!resolved) {
            resolved = true;
            resolve();
          }
        });

        this.proc.onExit(({ exitCode }) => {
          this.emit('exit', exitCode);
          this.setStatus('idle', `Process exited with code ${exitCode}`);
        });

        // Timeout if process doesn't start
        setTimeout(() => {
          if (!resolved) {
            resolved = true;
            resolve(); // Still resolve; process might output later
          }
        }, 3000);
      } catch (err) {
        reject(err);
      }
    });
  }

  write(data: string): void {
    if (!this.proc) {
      throw new Error('MiMo process not running');
    }
    this.proc.write(data);
  }

  executeCommand(command: string, args?: string): void {
    // Send slash command to MiMo Code
    const cmd = `/${command}${args ? ' ' + args : ''}\r`;
    this.write(cmd);
  }

  switchAgent(agent: string): void {
    // Tab key switches agents in MiMo Code
    this.write('\t');
  }

  resize(cols: number, rows: number): void {
    if (this.proc) {
      this.proc.resize(cols, rows);
    }
  }

  kill(): void {
    if (this.proc) {
      this.proc.kill();
      this.proc = null;
    }
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

  private detectStatus(data: string): void {
    // Pattern matching for MiMo Code status indicators
    // These patterns match common terminal AI assistant output
    const patterns: [RegExp, MimoStatusValue][] = [
      [/[Tt]hinking\.?\.{0,3}/, 'thinking'],
      [/[Ee]xecuting\.?\.{0,3}/, 'executing'],
      [/[Rr]unning\.?\.{0,3}/, 'executing'],
      [/[Dd]o you want to (allow|proceed|approve)/, 'waiting_approval'],
      [/[Ee]rror[: ]/, 'error'],
      [/[Ff]ailed[: ]/, 'error'],
    ];

    for (const [pattern, status] of patterns) {
      if (pattern.test(data)) {
        this.setStatus(status);
        return;
      }
    }

    // If we see a prompt character, we're likely idle
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
}
