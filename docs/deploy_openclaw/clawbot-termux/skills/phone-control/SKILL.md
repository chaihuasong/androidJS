---
name: phone-control
description: "Control Android phone hardware via Termux:API commands. Use when asked to: toggle flashlight, take photos, read sensors, make calls, send SMS, check battery, adjust brightness/volume, speak text aloud (TTS), vibrate phone, get GPS location, read clipboard, send notifications, check Wi-Fi, or read contacts. Requires Termux and Termux:API with granted permissions."
metadata:
  {
    "openclaw":
      {
        "emoji": "📱",
        "requires": { "bins": ["termux-torch", "termux-battery-status"] },
      },
  }
---

# Phone Control — ClawBot

You are running on an Android phone with Termux and Termux:API installed.
You can control phone hardware by executing termux-api commands via the exec/bash tool.

## Available Commands

### 1. Flashlight

| Action | Command |
|--------|---------|
| Turn on | `termux-torch on` |
| Turn off | `termux-torch off` |

### 2. Camera — Take Photos

**Take a photo (rear camera):**
```bash
timeout 10 termux-camera-photo -c 0 /tmp/clawbot-photo.jpg && ls -la /tmp/clawbot-photo.jpg
```

**Take a photo (front camera):**
```bash
timeout 10 termux-camera-photo -c 1 /tmp/clawbot-photo.jpg && ls -la /tmp/clawbot-photo.jpg
```

**List available cameras:**
```bash
termux-camera-info
```

**IMPORTANT:** `termux-camera-photo` may take a few seconds. After taking the photo, verify the file exists and report its size. Do NOT try to base64-encode or describe the image content — the current model does not support vision.

### 3. Sensors

**List all available sensors:**
```bash
termux-sensor -l
```

**Read a specific sensor (single reading):**
```bash
termux-sensor -s "accelerometer" -n 1
```

**Read with custom delay (ms) and count:**
```bash
termux-sensor -s "accelerometer" -d 1000 -n 3
```

**Common sensor names:** accelerometer, gyroscope, light, proximity, pressure, gravity, magnetic_field, rotation_vector

Sensor output is JSON. Parse and present the values clearly to the user.

### 4. Phone Calls

**Dial a number (MUST use background execution to avoid hanging):**
```bash
nohup termux-telephony-call <phone-number> > /dev/null 2>&1 &
echo "Dialling <phone-number>..."
```

**CRITICAL:** Always run `termux-telephony-call` in the background with `nohup ... &`. The command opens the Android dialer activity and will BLOCK FOREVER if run in the foreground. The `nohup ... &` pattern ensures the exec tool returns immediately.

**Important:** This opens the phone dialer and initiates the call. The user must manually hang up. Always confirm the number with the user before dialling.

### 5. SMS

**Send a text message:**
```bash
termux-sms-send -n <phone-number> "message content"
```

**List received SMS:**
```bash
termux-sms-list -l 10
```

### 6. Battery

**Check battery status:**
```bash
termux-battery-status
```

Returns JSON with percentage, status (CHARGING/DISCHARGING), temperature, etc.

### 7. Screen Brightness

**Set brightness (0–255):**
```bash
termux-brightness 255
```

**Set auto brightness:**
```bash
termux-brightness auto
```

### 8. Volume

**Set volume (0–15, varies by stream):**
```bash
termux-volume music 10
termux-volume ring 7
termux-volume alarm 5
```

### 9. Text-to-Speech (TTS)

**Make the phone speak aloud:**
```bash
termux-tts-speak "Hello, I am ClawBot"
```

**With language/rate options:**
```bash
termux-tts-speak -l zh -r 1.2 "你好，我是 ClawBot"
```

### 10. Vibrate

**Vibrate the phone (duration in ms):**
```bash
termux-vibrate -d 500
```

### 11. Notifications

**Send a notification:**
```bash
termux-notification --title "ClawBot" --content "Task completed"
```

**Send notification with action button:**
```bash
termux-notification --title "Alert" --content "Check this" --action "termux-torch on"
```

### 12. GPS Location

**Get current location (ALWAYS use network first, it's much faster):**
```bash
timeout 15 termux-location -p network
```

**GPS location (slower, more accurate — only if network fails):**
```bash
timeout 30 termux-location -p gps
```

**CRITICAL:** ALWAYS wrap `termux-location` with `timeout`. GPS can take 60+ seconds or hang forever indoors. Use `network` provider first (faster). Only try `gps` if the user specifically requests high accuracy.

Returns JSON with latitude, longitude, altitude, accuracy, etc.

### 13. Clipboard

**Read clipboard:**
```bash
termux-clipboard-get
```

**Write to clipboard:**
```bash
termux-clipboard-set "text to copy"
```

### 14. Wi-Fi

**Get current Wi-Fi info:**
```bash
termux-wifi-connectioninfo
```

**Scan nearby networks:**
```bash
termux-wifi-scaninfo
```

### 15. Contacts

**List contacts:**
```bash
termux-contact-list
```

### 16. Phone Info

**Get device and SIM info:**
```bash
termux-telephony-deviceinfo
termux-telephony-cellinfo
```

## Guidelines

- All commands run via the exec or bash tool.
- `termux-camera-photo` may take a few seconds to complete.
- Sensor readings return JSON — format them for readability.
- Phone calls and SMS are sensitive — always confirm the number/content before executing.
- TTS will play audio through the phone speaker.
- Location may take several seconds, especially GPS.
- If a command fails with a permission error, remind the user to grant Termux:API the required Android permission.
