# Phone Input Bridge MVP

这个子项目把 Android 手机变成 Windows 的局域网触控板和输入键盘。

## 已实现

- Android 手机输入电脑 IP、端口和六位 PIN 后连接
- 手机触控区域控制 Windows 鼠标相对移动
- 左键、右键、中键和滚轮
- 手机系统输入法输入中文、英文和 Emoji
- 回车、退格、Tab、Esc、方向键
- 复制、粘贴、Alt+Tab 快捷键
- 连接断开时释放仍处于按下状态的按键和鼠标按钮
- GitHub Actions 自动生成 Android APK 和 Windows EXE

## 使用方式

1. 在 GitHub Actions 中运行 `Phone Input Bridge Build`，或推送相关代码触发构建。
2. 下载 `phone-input-bridge-windows`，在 Windows 上运行 `phone-input-bridge-agent.exe`。
3. Windows 控制台会显示局域网 IP、端口和随机六位 PIN。
4. 下载并安装 `phone-input-bridge-android-debug` 中的 APK。
5. 手机和电脑连接同一个 Wi-Fi，在 App 中填入 IP、端口和 PIN。
6. 先在电脑上点中需要输入的文本框，再使用手机输入法。

## Windows 启动参数

```powershell
phone-input-bridge-agent.exe --port 9527 --pin 123456
```

也可以使用环境变量：

```powershell
$env:PHONE_INPUT_PORT = "9527"
$env:PHONE_INPUT_PIN = "123456"
.\phone-input-bridge-agent.exe
```

## 协议

第一版使用局域网 TCP + UTF-8 JSON Lines。每条 JSON 占一行。

```json
{"type":"auth","pin":"123456","client":"android"}
{"type":"mouse_move","dx":12,"dy":-4}
{"type":"mouse_button","button":"left","action":"press"}
{"type":"scroll","delta":-120}
{"type":"key","key":"ENTER","action":"press"}
{"type":"shortcut","keys":["CTRL","V"]}
{"type":"text","text":"你好，Windows"}
```

## 当前限制

- 只支持 Windows 10/11 电脑端和 Android 手机端。
- 仅建议在可信局域网使用；TCP 内容尚未加密。
- Windows 普通权限进程不能向管理员权限窗口注入输入。
- UAC 安全桌面、登录界面、Ctrl+Alt+Delete 和部分反作弊游戏不可控制。
- 当前是控制台程序，尚未实现托盘图标、二维码发现和安装包签名。
