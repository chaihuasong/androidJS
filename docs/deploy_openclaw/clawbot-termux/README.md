# ClawBot — Termux Local Mode + Cloud Remote Access

Control your Android phone via AI through OpenClaw running in Termux.
Supports: flashlight, camera + image description, sensors, phone calls.

## Architecture

```
114.55.130.197 (Cloud)                  Android Phone (Termux)
┌────────────────────┐                 ┌─────────────────────────┐
│  Nginx :18790      │◄─ SSH tunnel ──►│  OpenClaw Gateway :18789│
│  (reverse proxy)   │                 │         │                │
│                    │                 │    AI Agent (Claude)     │
│  Browser access    │                 │         │                │
└────────────────────┘                 │    termux-api commands   │
                                       │    (torch/camera/sensor) │
                                       └─────────────────────────┘
```

## Prerequisites

1. **Android phone** with:
   - [Termux](https://f-droid.org/en/packages/com.termux/) (from F-Droid)
   - [Termux:API](https://f-droid.org/en/packages/com.termux.api/) (from F-Droid, same source)
2. Grant Termux:API all permissions: Camera, Phone, Location, Storage, Sensors
3. Cloud server 114.55.130.197 already has Nginx configured

## Quick Start

### 1. Copy files to phone

Transfer this entire `clawbot-termux/` directory to the phone (e.g., via `scp`, USB, or `adb push`):

```bash
# From your computer
adb push deploy/clawbot-termux/ /data/data/com.termux/files/home/clawbot-termux/
```

### 2. Run setup in Termux

```bash
cd ~/clawbot-termux
chmod +x scripts/*.sh
bash scripts/termux-setup.sh
```

### 3. Onboard OpenClaw

```bash
openclaw onboard
```

Follow the prompts to bind your AI provider API key.

### 4. Start ClawBot

```bash
bash scripts/clawbot-start.sh
```

This starts the Gateway and SSH tunnel in a tmux session.

### 5. Access from browser

- **Local (same Wi-Fi):** `http://<phone-ip>:18789`
- **Remote (anywhere):** `http://114.55.130.197:18790`

## Demo Commands

| Say this | Agent does | Result |
|----------|-----------|--------|
| "Turn on the flashlight" | `termux-torch on` | Phone LED on |
| "Turn off the flashlight" | `termux-torch off` | Phone LED off |
| "Take a photo" | `termux-camera-photo` + `base64` | AI describes scene |
| "Read the accelerometer" | `termux-sensor -s accelerometer -n 1` | Shows XYZ values |
| "Call 10086" | `termux-telephony-call 10086` | Phone dials number |

## File Layout

```
clawbot-termux/
├── README.md                          # This file
├── openclaw.json                      # Gateway config → ~/.openclaw/
├── skills/
│   └── phone-control/
│       └── SKILL.md                   # Phone control skill → ~/.openclaw/workspace/skills/
├── scripts/
│   ├── termux-setup.sh                # One-time setup script
│   └── clawbot-start.sh              # Start gateway + tunnel
└── nginx/
    └── clawbot.conf                   # Cloud server Nginx config
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `termux-torch: not found` | Install Termux:API: `pkg install termux-api` |
| Permission denied | Grant permissions in Android Settings → Apps → Termux:API |
| SSH tunnel drops | The start script uses `ServerAliveInterval=60` to keep alive |
| Gateway won't start | Check `~/.openclaw/openclaw.log` and ensure port 18789 is free |
| Can't reach from cloud | Verify tunnel: `ssh root@114.55.130.197 'curl -s localhost:18789/health'` |
