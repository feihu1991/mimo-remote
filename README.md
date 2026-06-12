# MiMo Remote

> 🤖 远程控制 MiMo Code —— 从手机端监控、输入、语音视频通话

[![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/platform-Android-green.svg)](android/)
[![Node.js](https://img.shields.io/badge/CLI-Node.js-yellow.svg)](cli/)

MiMo Remote 让你通过 Android 手机远程控制电脑上运行的 [MiMo Code](https://github.com/XiaomiMiMo/MiMo-Code) AI 编程助手。

## ✨ 特性

- 📱 **实时终端镜像** — 手机上实时查看 MiMo Code 的终端输出
- ⌨️ **远程输入** — 从手机发送文本和命令
- 🎤 **语音通话** — 实时语音交互，语音→ASR→MiMo Code
- 📹 **视频通话** — 屏幕共享 + 摄像头
- 🧠 **记忆浏览器** — 查看 MiMo Code 的项目记忆、检查点、任务进度
- 🌳 **任务树** — 可视化 T1→T1.1→T1.2 任务层级
- ⚡ **快捷操作** — /goal, /dream, /distill, Compose 一键触发
- 🔐 **端到端加密** — libsodium 加密，服务端不可读
- 🏠 **P2P 直连** — 局域网自动发现，无需服务器

## 📦 项目结构

```
mimo-remote/
├── cli/          # 电脑端 CLI (Node.js + TypeScript) ✅
├── server/       # 信令中继服务 (可选) ✅
├── android/      # 原生 Android App (Kotlin + Jetpack Compose) ✅
└── shared/       # 共享协议定义 ✅
```

## 🏗️ 实现状态

| 模块 | 状态 | 说明 |
|------|------|------|
| CLI 主控 | ✅ | PTY 包装、MiMo 进程管理、Demo 模式 |
| 信令服务 | ✅ | WebSocket 中继、自动配对、心跳 |
| 加密层 | ✅ | libsodium Curve25519 密钥交换 |
| QR 码配对 | ✅ | CLI 生成 → Android ML Kit 扫描 |
| 终端镜像 | ✅ | 实时 ANSI 输出同步 |
| 远程输入 | ✅ | 文本 + 命令 (/goal, /dream 等) |
| 记忆浏览器 | ✅ | 文件分类、搜索、内容查看 |
| 任务树 | ✅ | T1→T1.1 层级可视化 |
| 语音通话 | ✅ | WebRTC P2P 音频流 |
| 视频通话 | ✅ | WebRTC P2P 视频流 (框架就绪) |
| 前台服务 | ✅ | Android 后台保活 |

## 🚀 快速开始

### 1. 安装 CLI

```bash
npm install -g mimo-remote-cli
```

### 2. 在电脑端启动

```bash
mimo-remote start
# 显示 QR 码，等待手机连接
```

### 3. 安装 Android App

从 [Releases](../../releases) 下载 APK，或自行编译：

```bash
cd android
./gradlew assembleDebug
# APK 在 app/build/outputs/apk/debug/
```

### 4. 扫码连接

打开 App → Scan QR Code → 扫描电脑端显示的 QR 码

## 🏗️ 开发

### CLI

```bash
cd cli
npm install
npm run dev    # 开发模式
npm run build  # 编译
```

### Server

```bash
cd server
npm install
npm run dev
```

### Android

```bash
cd android
./gradlew installDebug
```

## 🔧 配置

### CLI 选项

```bash
mimo-remote start \
  --server wss://your-relay.com \  # 信令服务器
  --port 9821 \                     # 本地端口
  --mimo-path /usr/local/bin/mimo \ # MiMo 路径
  --lan-only                        # 仅局域网
```

### 信令服务部署

```bash
cd server
PORT=9822 npm start
```

## 📐 架构

```
┌──────────────┐     WebSocket      ┌──────────────┐     PTY      ┌──────────┐
│  Android App │ ◄════════════════► │  CLI Wrapper │ ◄══════════► │ MiMo Code│
│  (Kotlin)    │     WebRTC P2P     │  (Node.js)   │              │          │
│              │ ◄════════════════► │              │              │          │
│  音视频通话   │     音视频流        │  媒体桥接      │              │          │
└──────────────┘                    └──────────────┘              └──────────┘
       │                                  │
       │           ┌──────────────┐       │
       └══════════►│ Signaling    │◄══════┘
                   │ Server       │  (可选中继)
                   └──────────────┘
```

## 📝 协议

所有控制消息通过 WebSocket 传输，使用 JSON 格式：

```json
{"type": "mimo:output", "data": "...", "timestamp": 1234567890}
{"type": "mimo:status", "status": "thinking"}
{"type": "user:input", "data": "help me refactor this"}
{"type": "user:command", "command": "dream"}
```

音视频通过 WebRTC P2P 传输，信令通过 WebSocket 中继。

## 📄 License

MIT License

## 🙏 致谢

- [MiMo Code](https://github.com/XiaomiMiMo/MiMo-Code) — 小米 AI 编程助手
- [OpenCode](https://github.com/anomalyco/opencode) — MiMo Code 的上游项目
- [Happy](https://github.com/slopus/happy) — 架构参考
