# ClawBot 部署总结 — 架构、原理与部署步骤

## 架构原理

```
用户浏览器                    云服务器 114.55.130.197              Android 手机 (Termux)
┌──────────┐                ┌─────────────────────┐            ┌───────────────────────────┐
│ Web UI   │── HTTP/WS ──→  │  Nginx (:28790)     │            │  OpenClaw Gateway (:28789)│
│ 聊天界面  │                │  反向代理             │◄─SSH隧道── │  (Node.js)                │
└──────────┘                │  proxy_pass :28789  │  R 28789   │       ↓                   │
                            └─────────────────────┘            │  DeepSeek Chat (AI 模型)   │
                                                               │       ↓                   │
                                                               │  exec 工具 → bash          │
                                                               │       ↓                   │
                                                               │  Termux:API 命令           │
                                                               │  ┌──────────────────────┐ │
                                                               │  │ termux-torch (手电筒) │ │
                                                               │  │ termux-vibrate (振动) │ │
                                                               │  │ termux-tts-speak(语音)│ │
                                                               │  │ termux-location(定位) │ │
                                                               │  │ termux-telephony(电话)│ │
                                                               │  │ termux-sensor (传感器)│ │
                                                               │  │ ...共 16 项功能       │ │
                                                               │  └──────────────────────┘ │
                                                               └───────────────────────────┘
```

**核心原理：零代码，纯配置 + Skill 提示词**

1. **OpenClaw Gateway** 跑在手机 Termux 里，内置 AI Agent（调用 DeepSeek Chat API）
2. 用户通过 Web UI 发消息 → Agent 理解意图 → 调用 `exec` 工具执行 shell 命令
3. shell 命令就是 `termux-api` 的命令行工具（如 `termux-torch on`）
4. **SKILL.md** 教 Agent 知道有哪些命令可用、怎么用 — 本质是 prompt engineering
5. SSH 反向隧道让云服务器能访问手机上的 Gateway

---

## 一、云服务器部署

**文件：** `/etc/nginx/conf.d/clawbot.conf`

```nginx
server {
    listen 28790;
    server_name _;
    location / {
        proxy_pass http://127.0.0.1:28789;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }
}
```

部署命令：
```bash
# 上传配置 → 重载 Nginx
nginx -t && nginx -s reload
```

服务器只做一件事：**Nginx 反向代理**，把外网 `:28790` 的请求转发到 `localhost:28789`（SSH 隧道映射过来的手机 Gateway）。

---

## 二、手机端部署

### 2.1 前置安装（一次性）

```bash
# Termux 内执行
pkg update && pkg upgrade -y
pkg install nodejs-lts termux-api openssh tmux -y

# 安装 OpenClaw
npm install -g openclaw@latest
termux-fix-shebang $PREFIX/lib/node_modules/openclaw/openclaw.mjs

# 生成 SSH 密钥并添加到云服务器
ssh-keygen -t ed25519 -f ~/.ssh/id_ed25519 -N ""
# 将公钥添加到 root@114.55.130.197 的 authorized_keys

# 授予 Termux:API 权限（通过 adb）
adb shell pm grant com.termux.api android.permission.CAMERA
adb shell pm grant com.termux.api android.permission.CALL_PHONE
adb shell pm grant com.termux.api android.permission.ACCESS_FINE_LOCATION
# ...等权限

# 禁用电池优化
adb shell dumpsys deviceidle whitelist +com.termux
adb shell dumpsys deviceidle whitelist +com.termux.api
```

### 2.2 配置文件

**`~/.openclaw/.env`**
```
OPENCLAW_GATEWAY_TOKEN=<随机生成的token>
DEEPSEEK_API_KEY=sk-xxx
SILICONFLOW_API_KEY=sk-xxx
```

**`~/.openclaw/openclaw.json`**（Gateway 配置）
```json5
{
  agents: {
    defaults: {
      workspace: "~/.openclaw/workspace",
      model: { primary: "deepseek/deepseek-chat" },
    },
    list: [{
      id: "default", default: true,
      identity: {
        name: "ClawBot",
        theme: "Android phone assistant that controls hardware via Termux:API",
        emoji: "🤖",
      },
    }],
  },
  models: {
    providers: {
      deepseek: {
        baseUrl: "https://api.deepseek.com",
        apiKey: "${DEEPSEEK_API_KEY}",
        api: "openai-completions",
        models: [{ id: "deepseek-chat", name: "DeepSeek Chat" }],
      },
    },
  },
  gateway: {
    mode: "local", port: 28789, bind: "lan",
    controlUi: {
      enabled: true,
      allowInsecureAuth: true,
      dangerouslyDisableDeviceAuth: true,
      allowedOrigins: ["http://114.55.130.197:28790"],
    },
    trustedProxies: ["127.0.0.1"],
    auth: { mode: "token", token: "${OPENCLAW_GATEWAY_TOKEN}" },
  },
  tools: { allow: ["exec", "bash"] },
  skills: { load: { extraDirs: ["~/.openclaw/workspace/skills"], watch: true } },
}
```

**Skills（`~/.openclaw/workspace/skills/`）**

| Skill | 文件 | 功能 |
|-------|------|------|
| `phone-control` | `phone-control/SKILL.md` | 16 项 termux-api 硬件控制 |
| `photo-share` | `photo-share/SKILL.md` | 拍照并上传至云端在聊天中显示 |
| `photo-find` | `photo-find/SKILL.md` | 浏览相册、按日期筛选、上传现有照片 |
| `screenshot` | `screenshot/SKILL.md` | 截屏并在聊天中内联显示 |
| `weather` | `weather/SKILL.md` | 天气预报（wttr.in，无需 API key）|
| `system-status` | `system-status/SKILL.md` | 电量/存储/内存/CPU/网络综合状态 |
| `file-manager` | `file-manager/SKILL.md` | 浏览、搜索、读取、管理手机文件 |
| `ella-vision` | `ella-vision/SKILL.md` | 发图给 Ella AI 进行端侧图像分析 |

关键注意事项：`termux-telephony-call` 必须 `nohup ... &` 后台执行，`termux-location` 必须 `timeout` 包裹。

### 2.3 启动脚本

**`~/clawbot-launch.sh`**
```bash
#!/data/data/com.termux/files/usr/bin/bash
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH=$PREFIX/bin:$PATH
export TMPDIR=$PREFIX/tmp
export TMP=$TMPDIR
export TEMP=$TMPDIR
set -a; . $HOME/.openclaw/.env; set +a

termux-wake-lock           # 防止后台被杀

pkill -f "openclaw" 2>/dev/null
pkill -f "ssh.*28789" 2>/dev/null
tmux kill-session -t clawbot 2>/dev/null
sleep 2

# 窗口1: Gateway
tmux new-session -d -s clawbot -n gw "export HOME=$HOME PREFIX=$PREFIX PATH=$PATH TMPDIR=$TMPDIR TMP=$TMPDIR TEMP=$TMPDIR OPENCLAW_GATEWAY_TOKEN=$OPENCLAW_GATEWAY_TOKEN DEEPSEEK_API_KEY=$DEEPSEEK_API_KEY SILICONFLOW_API_KEY=$SILICONFLOW_API_KEY; openclaw gateway --port 28789 --verbose 2>&1 | tee $PREFIX/tmp/openclaw/gateway-stdout.log"

sleep 12

# 窗口2: 自动重连隧道
tmux new-window -t clawbot -n tun "bash $HOME/tunnel-loop.sh"

sleep 3
echo "ClawBot started. Tunnel will auto-reconnect if dropped."
```

**`~/tunnel-loop.sh`**（断线自动重连）
```bash
#!/data/data/com.termux/files/usr/bin/bash
export HOME=/data/data/com.termux/files/home
export PATH=/data/data/com.termux/files/usr/bin:$PATH
while true; do
  echo "$(date): Starting SSH tunnel..."
  ssh -o ServerAliveInterval=5 -o ServerAliveCountMax=2 \
      -o ExitOnForwardFailure=yes -o StrictHostKeyChecking=no \
      -o ConnectTimeout=5 \
      -R 28789:127.0.0.1:28789 root@114.55.130.197 -N
  echo "$(date): Tunnel died (exit=$?). Restarting in 5s..."
  sleep 5
done
```

### 2.4 启动方式

必须在 **Termux 前台** 执行（`am broadcast` 或直接在 Termux 终端输入）：
```bash
bash ~/clawbot-launch.sh
```

---

## 三、踩过的坑

| 问题 | 原因 | 解决 |
|------|------|------|
| Gateway 启动报错 | Termux 的 shebang `/usr/bin/env` 不存在 | `termux-fix-shebang` |
| 502 Bad Gateway | SSH 隧道断了 | `tunnel-loop.sh` 自动重连 |
| Termux 后台被杀 | Android 电池优化 | `termux-wake-lock` + 电池白名单 |
| 打电话卡住 | `termux-telephony-call` 阻塞不返回 | `nohup ... &` 后台执行 |
| GPS 定位卡住 | 室内 GPS 信号弱，无限等待 | `timeout 15` 包裹 |
| New session 极慢 | 卡住的命令阻塞队列（串行处理） | 清除 session + 重启 |
| DeepSeek-VL2 无反应 | VL 模型不支持 function calling | 切回 deepseek-chat |
| `run-as` 执行命令失败 | 无法发送 Android Intent | 必须在 Termux app 上下文启动 |

---

## 四、关键文件清单

| 位置 | 文件 | 作用 |
|------|------|------|
| 云服务器 | `/etc/nginx/conf.d/clawbot.conf` | Nginx 反向代理 |
| 手机 | `~/.openclaw/openclaw.json` | Gateway + 模型 + 认证配置 |
| 手机 | `~/.openclaw/.env` | API Keys |
| 手机 | `~/.openclaw/workspace/skills/phone-control/SKILL.md` | 16 项硬件控制 |
| 手机 | `~/.openclaw/workspace/skills/photo-share/SKILL.md` | 拍照显示 |
| 手机 | `~/.openclaw/workspace/skills/photo-find/SKILL.md` | 浏览相册 |
| 手机 | `~/.openclaw/workspace/skills/screenshot/SKILL.md` | 截图显示 |
| 手机 | `~/.openclaw/workspace/skills/weather/SKILL.md` | 天气查询 |
| 手机 | `~/.openclaw/workspace/skills/system-status/SKILL.md` | 系统状态 |
| 手机 | `~/.openclaw/workspace/skills/file-manager/SKILL.md` | 文件管理 |
| 手机 | `~/.openclaw/workspace/skills/ella-vision/SKILL.md` | 图像分析 |
| 手机 | `~/clawbot-launch.sh` | 一键启动脚本 |
| 手机 | `~/tunnel-loop.sh` | SSH 隧道自动重连 |
| 源码仓库 | `deploy/clawbot-termux/` | 所有部署文件的备份 |

---

## 五、可演示功能

### phone-control（16 项硬件控制）

| # | 功能 | 指令示例 | 效果 |
|---|------|---------|------|
| 1 | 手电筒 | "打开手电筒" | 闪光灯亮 |
| 2 | 拍照 | "拍张照" | 拍照保存文件 |
| 3 | 传感器 | "读取加速度传感器" | 返回 xyz 数值 |
| 4 | 打电话 | "拨打 10086" | 手机拨出电话 |
| 5 | 发短信 | "给 xxx 发短信" | 发送 SMS |
| 6 | 电池状态 | "查看电量" | 返回电量百分比 |
| 7 | 屏幕亮度 | "亮度调到最大" | 屏幕变亮 |
| 8 | 音量控制 | "媒体音量调到 10" | 调节音量 |
| 9 | 语音播报 | "说一句你好" | 手机扬声器朗读 |
| 10 | 振动 | "振动手机" | 手机振动 |
| 11 | 发通知 | "发条通知提醒开会" | 通知栏推送 |
| 12 | GPS 定位 | "获取当前位置" | 返回经纬度 |
| 13 | 剪贴板 | "读取剪贴板" | 读写剪贴板 |
| 14 | Wi-Fi | "查看当前 Wi-Fi" | 返回网络信息 |
| 15 | 通讯录 | "列出联系人" | 读取通讯录 |
| 16 | 设备信息 | "查看手机信息" | SIM 卡、运营商 |

### 图像类 Skills

| 指令示例 | Skill | 效果 |
|---------|-------|------|
| "拍张照发给我看" | photo-share | 拍照上传，聊天中内联显示图片 |
| "把相册最新的照片给我看" | photo-find | 浏览相册，上传现有照片显示 |
| "截个屏" | screenshot | 截图当前屏幕，聊天中显示 |
| "帮我分析一下这张图" | ella-vision | 调用 Ella AI 分析图片内容 |

### 信息查询 Skills

| 指令示例 | Skill | 效果 |
|---------|-------|------|
| "今天天气怎么样" | weather | 当前天气 + 3 天预报 |
| "手机还剩多少存储" | system-status | 电量/存储/内存/CPU 综合报告 |
| "帮我看看 Download 文件夹里有什么" | file-manager | 列出并管理手机文件 |

**零行自定义代码**，全部通过 OpenClaw 配置 + SKILL.md 提示词实现 AI 控制手机。
