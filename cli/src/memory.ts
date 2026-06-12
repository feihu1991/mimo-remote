// cli/src/memory.ts — Watch MiMo Code memory files

import * as fs from 'fs';
import * as path from 'path';
import { EventEmitter } from 'events';
import type { MemoryFile } from './protocol.js';

const WATCHED_FILES = [
  'MEMORY.md',
  'checkpoint.md',
  'notes.md',
  'tasks/progress.md',
];

export class MemoryWatcher extends EventEmitter {
  private rootDir: string;
  private watchers: fs.FSWatcher[] = [];
  private debounceTimer: ReturnType<typeof setTimeout> | null = null;

  constructor(rootDir: string) {
    super();
    this.rootDir = rootDir;
  }

  start(): void {
    // Watch .mimocode directory
    const mimocodeDir = path.join(this.rootDir, '.mimocode');
    if (fs.existsSync(mimocodeDir)) {
      this.watchDir(mimocodeDir);
    }

    // Watch root-level memory files
    for (const file of WATCHED_FILES) {
      const filePath = path.join(this.rootDir, file);
      if (fs.existsSync(filePath)) {
        this.watchFile(filePath);
      }
    }

    // Watch tasks directory
    const tasksDir = path.join(this.rootDir, 'tasks');
    if (fs.existsSync(tasksDir)) {
      this.watchDir(tasksDir);
    }

    console.log('[memory] Watching for memory file changes');
  }

  stop(): void {
    for (const watcher of this.watchers) {
      watcher.close();
    }
    this.watchers = [];
  }

  onUpdate(callback: (files: MemoryFile[]) => void): void {
    this.on('update', callback);
  }

  getAllFiles(): MemoryFile[] {
    const files: MemoryFile[] = [];

    for (const file of WATCHED_FILES) {
      const filePath = path.join(this.rootDir, file);
      if (fs.existsSync(filePath)) {
        try {
          files.push(this.readFile(filePath));
        } catch {}
      }
    }

    const mimocodeDir = path.join(this.rootDir, '.mimocode');
    if (fs.existsSync(mimocodeDir)) {
      this.scanDir(mimocodeDir, files);
    }

    const tasksDir = path.join(this.rootDir, 'tasks');
    if (fs.existsSync(tasksDir)) {
      this.scanDir(tasksDir, files);
    }

    return files;
  }

  readFile(relPath: string): MemoryFile {
    const filePath = path.isAbsolute(relPath)
      ? relPath
      : path.join(this.rootDir, relPath);

    const stat = fs.statSync(filePath);
    const content = fs.readFileSync(filePath, 'utf-8');

    return {
      path: path.relative(this.rootDir, filePath),
      content,
      lastModified: stat.mtimeMs,
    };
  }

  private watchFile(filePath: string): void {
    try {
      const watcher = fs.watch(filePath, () => {
        this.debounceEmit();
      });
      this.watchers.push(watcher);
    } catch {}
  }

  private watchDir(dirPath: string): void {
    try {
      const watcher = fs.watch(dirPath, { recursive: true }, () => {
        this.debounceEmit();
      });
      this.watchers.push(watcher);
    } catch {}
  }

  private debounceEmit(): void {
    if (this.debounceTimer) clearTimeout(this.debounceTimer);
    this.debounceTimer = setTimeout(() => {
      const files = this.getAllFiles();
      this.emit('update', files);
    }, 500);
  }

  private scanDir(dir: string, files: MemoryFile[]): void {
    try {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const entry of entries) {
        const fullPath = path.join(dir, entry.name);
        if (entry.isFile() && (entry.name.endsWith('.md') || entry.name.endsWith('.json'))) {
          try {
            files.push(this.readFile(fullPath));
          } catch {}
        } else if (entry.isDirectory()) {
          this.scanDir(fullPath, files);
        }
      }
    } catch {}
  }
}
