# ClawBot — Termux Local Mode + Cloud Remote Access

Control your Android phone via AI through OpenClaw running in Termux.
Supports: flashlight, camera, screenshots, weather, sensors, phone calls, file management and more.

## Architecture

```
114.55.130.197 (Cloud)                  Android Phone (Termux)
┌────────────────────┐                 ┌─────────────────────────┐
│  Nginx :28790      │◄─ SSH tunnel ──►│  OpenClaw Gateway :28789│
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

- **Local (same Wi-Fi):** `http://<phone-ip>:28789`
- **Remote (anywhere):** `http://114.55.130.197:28790`

## Skills

| Skill | Description |
|-------|-------------|
| `phone-control` | Flashlight, camera, sensors, calls, SMS, TTS, GPS, clipboard, Wi-Fi |
| `photo-share` | Take a new photo and display it inline in chat |
| `photo-find` | Browse camera roll, filter by date, upload existing photos to chat |
| `screenshot` | Capture the phone screen and display it inline in chat |
| `weather` | Current weather and 3-day forecast via wttr.in (no API key needed) |
| `system-status` | Battery, storage, memory, CPU, and network overview |
| `file-manager` | Browse, search, read, copy, move and delete files on the phone |
| `ella-vision` | Send a photo to Ella AI for on-device image analysis |

## Demo Commands

| Say this | Skill | Result |
|----------|-------|--------|
| "Turn on the flashlight" | phone-control | Phone LED on |
| "Take a photo and show me" | photo-share | Inline photo in chat |
| "Show me the latest photo from my camera roll" | photo-find | Uploads and displays existing photo |
| "Take a screenshot" | screenshot | Inline screenshot in chat |
| "What's the weather today?" | weather | Current weather + forecast |
| "How much storage is left?" | system-status | Disk/battery/memory report |
| "List my recent downloads" | file-manager | Files in /sdcard/Download/ |
| "Read the accelerometer" | phone-control | Shows XYZ sensor values |
| "Call 10086" | phone-control | Phone dials number |

## File Layout

```
clawbot-termux/
├── README.md                          # This file
├── openclaw.json                      # Gateway config → ~/.openclaw/
├── skills/
│   ├── phone-control/SKILL.md         # Hardware control (torch/camera/sensor/call/SMS)
│   ├── photo-share/SKILL.md           # Take photo and display in chat
│   ├── photo-find/SKILL.md            # Browse and upload existing photos
│   ├── screenshot/SKILL.md            # Capture screen and display in chat
│   ├── weather/SKILL.md               # Weather forecast via wttr.in
│   ├── system-status/SKILL.md         # Battery/storage/memory/CPU overview
│   ├── file-manager/SKILL.md          # Browse/search/manage phone files
│   └── ella-vision/SKILL.md           # On-device image analysis via Ella AI
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
| SSH tunnel drops | The start script uses `ServerAliveInterval=5` to keep alive |
| Gateway won't start | Check `~/.openclaw/openclaw.log` and ensure port 28789 is free |
| Can't reach from cloud | Verify tunnel: `ssh root@114.55.130.197 'curl -s localhost:28789/health'` |
