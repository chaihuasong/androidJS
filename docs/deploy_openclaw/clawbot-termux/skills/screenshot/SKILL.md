---
name: screenshot
description: "Take a screenshot of the phone screen and display it in chat. Use when asked to: take a screenshot, capture the screen, show what's on screen, screenshot the current app, or share screen content."
metadata:
  {
    "openclaw":
      {
        "emoji": "📸",
        "requires": { "bins": ["screencap", "scp"] },
      },
  }
---

# Screenshot — ClawBot

Capture the phone screen via `screencap` and upload to the cloud server for display in chat.

## How It Works

```
screencap → $TMPDIR/screenshot.png → SCP → Cloud /var/www/clawbot-photos/ → Browser
```

`screencap` is a built-in Android tool — no Termux:API or extra permissions needed.

## Take and Display a Screenshot

```bash
CLOUD="root@114.55.130.197"
PHOTO_DIR="/var/www/clawbot-photos"
BASE_URL="http://114.55.130.197:28790/photos"
TS=$(date +%Y%m%d-%H%M%S)
RAW="$TMPDIR/ss-$TS.png"
SS_NAME="screenshot-$TS.jpg"
SS_PATH="$TMPDIR/$SS_NAME"

# 截图
adb exec-out screencap -p > "$RAW" \
  && echo "Captured: $(du -sh $RAW | cut -f1)" \
  && convert "$RAW" -resize 50% -quality 70 "$SS_PATH" \
  && rm -f "$RAW" \
  && echo "Compressed: $(du -sh $SS_PATH | cut -f1)" \
  && scp -o StrictHostKeyChecking=no "$SS_PATH" "$CLOUD:$PHOTO_DIR/$SS_NAME" \
  && ssh -o StrictHostKeyChecking=no "$CLOUD" "chmod 644 $PHOTO_DIR/$SS_NAME" \
  && echo "![$SS_NAME]($BASE_URL/$SS_NAME)" \
  && rm -f "$SS_PATH"
```

## Take Multiple Screenshots with Delay

```bash
CLOUD="root@114.55.130.197"
PHOTO_DIR="/var/www/clawbot-photos"
BASE_URL="http://114.55.130.197:28790/photos"

for i in 1 2 3; do
  sleep 2
  SS_NAME="screenshot-$(date +%Y%m%d-%H%M%S)-${i}.png"
  SS_PATH="$TMPDIR/$SS_NAME"
  adb exec-out screencap -p > "$SS_PATH" \
    && scp -o StrictHostKeyChecking=no "$SS_PATH" "$CLOUD:$PHOTO_DIR/$SS_NAME" \
    && ssh -o StrictHostKeyChecking=no "$CLOUD" "chmod 644 $PHOTO_DIR/$SS_NAME" \
    && echo "![$SS_NAME]($BASE_URL/$SS_NAME)" \
    && rm -f "$SS_PATH"
done
```

## Guidelines

- `screencap` captures whatever is currently on screen — remind user to navigate to the desired app/screen first.
- The file is a PNG (full resolution). Expect 1–5MB file size.
- After successful upload, output the Markdown image `![name](url)` — chat UI renders it inline.
- Always clean up temp files after upload.
- If `scp` fails, check SSH connectivity: `ssh -o StrictHostKeyChecking=no root@114.55.130.197 echo ok`
