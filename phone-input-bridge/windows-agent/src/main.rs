use rand::Rng;
use serde::Deserialize;
use serde_json::{json, Value};
use std::env;
use std::io::{self, BufRead, BufReader, BufWriter, Write};
use std::mem::size_of;
use std::net::{TcpListener, TcpStream, UdpSocket};
use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
    SendInput, INPUT, INPUT_0, INPUT_KEYBOARD, INPUT_MOUSE, KEYBDINPUT, KEYEVENTF_KEYUP,
    KEYEVENTF_UNICODE, MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, MOUSEEVENTF_MIDDLEDOWN,
    MOUSEEVENTF_MIDDLEUP, MOUSEEVENTF_MOVE, MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP,
    MOUSEEVENTF_WHEEL, MOUSEINPUT,
};

const DEFAULT_PORT: u16 = 9527;
const MAX_MESSAGE_SIZE: usize = 64 * 1024;

#[derive(Debug, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
enum ClientMessage {
    Auth {
        pin: String,
        #[serde(default)]
        client: Option<String>,
    },
    MouseMove {
        dx: i32,
        dy: i32,
    },
    MouseButton {
        button: String,
        action: String,
    },
    Scroll {
        delta: i32,
    },
    Key {
        key: String,
        action: String,
    },
    Shortcut {
        keys: Vec<String>,
    },
    Text {
        text: String,
    },
    Ping,
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    if !cfg!(windows) {
        return Err("This agent can only run on Windows.".into());
    }

    let config = Config::from_env_and_args()?;
    let listener = TcpListener::bind(("0.0.0.0", config.port))?;

    println!("Phone Input Bridge Agent 0.1.0");
    println!("--------------------------------");
    println!(
        "局域网地址: {}:{}",
        local_ip().unwrap_or_else(|| "请运行 ipconfig 查看".into()),
        config.port
    );
    println!("配对 PIN: {}", config.pin);
    println!("请让手机和电脑连接同一个 Wi-Fi。按 Ctrl+C 退出。\n");

    let mut input = InputController::default();

    for incoming in listener.incoming() {
        match incoming {
            Ok(stream) => {
                if let Err(error) = handle_client(stream, &config.pin, &mut input) {
                    eprintln!("客户端连接结束: {error}");
                }
                input.release_all();
                println!("等待下一台手机连接……");
            }
            Err(error) => eprintln!("接受连接失败: {error}"),
        }
    }

    Ok(())
}

struct Config {
    port: u16,
    pin: String,
}

impl Config {
    fn from_env_and_args() -> Result<Self, Box<dyn std::error::Error>> {
        let mut port = env::var("PHONE_INPUT_PORT")
            .ok()
            .and_then(|value| value.parse::<u16>().ok())
            .unwrap_or(DEFAULT_PORT);
        let mut pin = env::var("PHONE_INPUT_PIN").ok();

        let mut args = env::args().skip(1);
        while let Some(arg) = args.next() {
            match arg.as_str() {
                "--port" => {
                    let value = args.next().ok_or("--port requires a value")?;
                    port = value.parse()?;
                }
                "--pin" => {
                    pin = Some(args.next().ok_or("--pin requires a value")?);
                }
                "--help" | "-h" => {
                    println!("Usage: phone-input-bridge-agent.exe [--port 9527] [--pin 123456]");
                    std::process::exit(0);
                }
                other => return Err(format!("Unknown argument: {other}").into()),
            }
        }

        let pin = pin.unwrap_or_else(|| {
            format!(
                "{:06}",
                rand::thread_rng().gen_range(0..=999_999)
            )
        });
        if pin.len() != 6 || !pin.chars().all(|character| character.is_ascii_digit()) {
            return Err("PIN must contain exactly 6 digits".into());
        }

        Ok(Self { port, pin })
    }
}

fn handle_client(
    stream: TcpStream,
    expected_pin: &str,
    input: &mut InputController,
) -> Result<(), Box<dyn std::error::Error>> {
    stream.set_nodelay(true)?;
    let peer = stream.peer_addr()?;
    println!("手机已连接: {peer}");

    let reader_stream = stream.try_clone()?;
    let mut reader = BufReader::new(reader_stream);
    let mut writer = BufWriter::new(stream);
    let mut authenticated = false;
    let mut line = String::new();

    loop {
        line.clear();
        let bytes = reader.read_line(&mut line)?;
        if bytes == 0 {
            break;
        }
        if line.len() > MAX_MESSAGE_SIZE {
            send_json(&mut writer, json!({"type": "error", "message": "消息过大"}))?;
            break;
        }

        let parsed: ClientMessage = match serde_json::from_str(line.trim_end()) {
            Ok(message) => message,
            Err(error) => {
                send_json(
                    &mut writer,
                    json!({"type": "error", "message": format!("JSON 格式错误: {error}")}),
                )?;
                continue;
            }
        };

        if !authenticated {
            match parsed {
                ClientMessage::Auth { pin, client } if pin == expected_pin => {
                    authenticated = true;
                    let client_name = client.unwrap_or_else(|| "unknown".into());
                    println!("配对成功，客户端: {client_name}");
                    send_json(
                        &mut writer,
                        json!({"type": "auth_ok", "message": "已连接到 Windows"}),
                    )?;
                }
                ClientMessage::Auth { .. } => {
                    send_json(&mut writer, json!({"type": "error", "message": "PIN 错误"}))?;
                    break;
                }
                _ => {
                    send_json(&mut writer, json!({"type": "error", "message": "请先认证"}))?;
                    break;
                }
            }
            continue;
        }

        if let Err(error) = execute_message(parsed, input, &mut writer) {
            send_json(
                &mut writer,
                json!({"type": "error", "message": error.to_string()}),
            )?;
        }
    }

    Ok(())
}

fn execute_message(
    message: ClientMessage,
    input: &mut InputController,
    writer: &mut BufWriter<TcpStream>,
) -> io::Result<()> {
    match message {
        ClientMessage::Auth { .. } => {}
        ClientMessage::MouseMove { dx, dy } => {
            input.move_mouse(dx.clamp(-500, 500), dy.clamp(-500, 500))?;
        }
        ClientMessage::MouseButton { button, action } => {
            input.mouse_button(&button, &action)?;
        }
        ClientMessage::Scroll { delta } => {
            input.scroll(delta.clamp(-1200, 1200))?;
        }
        ClientMessage::Key { key, action } => {
            input.key(&key, &action)?;
        }
        ClientMessage::Shortcut { keys } => {
            input.shortcut(&keys)?;
        }
        ClientMessage::Text { text } => {
            input.text(&text)?;
        }
        ClientMessage::Ping => {
            send_json(writer, json!({"type": "pong"}))?;
        }
    }
    Ok(())
}

fn send_json(writer: &mut BufWriter<TcpStream>, value: Value) -> io::Result<()> {
    serde_json::to_writer(&mut *writer, &value)
        .map_err(|error| io::Error::new(io::ErrorKind::InvalidData, error))?;
    writer.write_all(b"\n")?;
    writer.flush()
}

#[derive(Default)]
struct InputController {
    pressed_keys: Vec<u16>,
    left_down: bool,
    right_down: bool,
    middle_down: bool,
}

impl InputController {
    fn move_mouse(&self, dx: i32, dy: i32) -> io::Result<()> {
        send_mouse(dx, dy, 0, MOUSEEVENTF_MOVE)
    }

    fn scroll(&self, delta: i32) -> io::Result<()> {
        send_mouse(0, 0, delta as u32, MOUSEEVENTF_WHEEL)
    }

    fn mouse_button(&mut self, button: &str, action: &str) -> io::Result<()> {
        let button = button.to_ascii_lowercase();
        let action = action.to_ascii_lowercase();

        let (down_flag, up_flag) = match button.as_str() {
            "left" => (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP),
            "right" => (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP),
            "middle" => (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP),
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "不支持的鼠标按键",
                ))
            }
        };

        match action.as_str() {
            "down" => {
                send_mouse(0, 0, 0, down_flag)?;
                self.set_mouse_state(&button, true);
            }
            "up" => {
                send_mouse(0, 0, 0, up_flag)?;
                self.set_mouse_state(&button, false);
            }
            "press" => {
                send_mouse(0, 0, 0, down_flag)?;
                send_mouse(0, 0, 0, up_flag)?;
                self.set_mouse_state(&button, false);
            }
            _ => {
                return Err(io::Error::new(
                    io::ErrorKind::InvalidInput,
                    "不支持的鼠标动作",
                ))
            }
        }
        Ok(())
    }

    fn key(&mut self, key: &str, action: &str) -> io::Result<()> {
        let virtual_key = key_code(key).ok_or_else(|| {
            io::Error::new(io::ErrorKind::InvalidInput, format!("未知按键: {key}"))
        })?;

        match action.to_ascii_lowercase().as_str() {
            "down" => self.key_down(virtual_key),
            "up" => self.key_up(virtual_key),
            "press" => {
                self.key_down(virtual_key)?;
                self.key_up(virtual_key)
            }
            _ => Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "不支持的键盘动作",
            )),
        }
    }

    fn shortcut(&mut self, keys: &[String]) -> io::Result<()> {
        let virtual_keys = keys
            .iter()
            .map(|key| {
                key_code(key).ok_or_else(|| {
                    io::Error::new(io::ErrorKind::InvalidInput, format!("未知按键: {key}"))
                })
            })
            .collect::<io::Result<Vec<_>>>()?;

        for key in &virtual_keys {
            self.key_down(*key)?;
        }
        for key in virtual_keys.iter().rev() {
            self.key_up(*key)?;
        }
        Ok(())
    }

    fn text(&self, text: &str) -> io::Result<()> {
        for code_unit in text.encode_utf16() {
            send_unicode(code_unit, false)?;
            send_unicode(code_unit, true)?;
        }
        Ok(())
    }

    fn key_down(&mut self, virtual_key: u16) -> io::Result<()> {
        send_key(virtual_key, false)?;
        if !self.pressed_keys.contains(&virtual_key) {
            self.pressed_keys.push(virtual_key);
        }
        Ok(())
    }

    fn key_up(&mut self, virtual_key: u16) -> io::Result<()> {
        send_key(virtual_key, true)?;
        self.pressed_keys.retain(|key| *key != virtual_key);
        Ok(())
    }

    fn set_mouse_state(&mut self, button: &str, down: bool) {
        match button {
            "left" => self.left_down = down,
            "right" => self.right_down = down,
            "middle" => self.middle_down = down,
            _ => {}
        }
    }

    fn release_all(&mut self) {
        for key in self.pressed_keys.clone().into_iter().rev() {
            let _ = send_key(key, true);
        }
        self.pressed_keys.clear();

        if self.left_down {
            let _ = send_mouse(0, 0, 0, MOUSEEVENTF_LEFTUP);
        }
        if self.right_down {
            let _ = send_mouse(0, 0, 0, MOUSEEVENTF_RIGHTUP);
        }
        if self.middle_down {
            let _ = send_mouse(0, 0, 0, MOUSEEVENTF_MIDDLEUP);
        }
        self.left_down = false;
        self.right_down = false;
        self.middle_down = false;
    }
}

fn send_mouse(dx: i32, dy: i32, mouse_data: u32, flags: u32) -> io::Result<()> {
    let input = INPUT {
        r#type: INPUT_MOUSE,
        Anonymous: INPUT_0 {
            mi: MOUSEINPUT {
                dx,
                dy,
                mouseData: mouse_data,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    send_input(input)
}

fn send_key(virtual_key: u16, key_up: bool) -> io::Result<()> {
    let input = INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: KEYBDINPUT {
                wVk: virtual_key,
                wScan: 0,
                dwFlags: if key_up { KEYEVENTF_KEYUP } else { 0 },
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    send_input(input)
}

fn send_unicode(code_unit: u16, key_up: bool) -> io::Result<()> {
    let input = INPUT {
        r#type: INPUT_KEYBOARD,
        Anonymous: INPUT_0 {
            ki: KEYBDINPUT {
                wVk: 0,
                wScan: code_unit,
                dwFlags: KEYEVENTF_UNICODE | if key_up { KEYEVENTF_KEYUP } else { 0 },
                time: 0,
                dwExtraInfo: 0,
            },
        },
    };
    send_input(input)
}

fn send_input(input: INPUT) -> io::Result<()> {
    let sent = unsafe { SendInput(1, &input, size_of::<INPUT>() as i32) };
    if sent == 0 {
        Err(io::Error::last_os_error())
    } else {
        Ok(())
    }
}

fn key_code(name: &str) -> Option<u16> {
    let normalized = name.trim().to_ascii_uppercase();
    if normalized.len() == 1 {
        let byte = normalized.as_bytes()[0];
        if byte.is_ascii_alphanumeric() {
            return Some(byte as u16);
        }
    }

    match normalized.as_str() {
        "BACKSPACE" => Some(0x08),
        "TAB" => Some(0x09),
        "ENTER" | "RETURN" => Some(0x0D),
        "SHIFT" => Some(0x10),
        "CTRL" | "CONTROL" => Some(0x11),
        "ALT" => Some(0x12),
        "ESC" | "ESCAPE" => Some(0x1B),
        "SPACE" => Some(0x20),
        "PAGE_UP" => Some(0x21),
        "PAGE_DOWN" => Some(0x22),
        "END" => Some(0x23),
        "HOME" => Some(0x24),
        "LEFT" => Some(0x25),
        "UP" => Some(0x26),
        "RIGHT" => Some(0x27),
        "DOWN" => Some(0x28),
        "DELETE" => Some(0x2E),
        "WIN" | "WINDOWS" => Some(0x5B),
        "F1" => Some(0x70),
        "F2" => Some(0x71),
        "F3" => Some(0x72),
        "F4" => Some(0x73),
        "F5" => Some(0x74),
        "F6" => Some(0x75),
        "F7" => Some(0x76),
        "F8" => Some(0x77),
        "F9" => Some(0x78),
        "F10" => Some(0x79),
        "F11" => Some(0x7A),
        "F12" => Some(0x7B),
        _ => None,
    }
}

fn local_ip() -> Option<String> {
    let socket = UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("1.1.1.1:80").ok()?;
    Some(socket.local_addr().ok()?.ip().to_string())
}
