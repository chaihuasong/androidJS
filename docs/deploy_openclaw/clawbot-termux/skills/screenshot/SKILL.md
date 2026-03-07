---
name: screenshot
description: "Capture the phone SCREEN or record screen video and show it in chat. Use when asked to: take a screenshot, capture the screen, show what's on screen, screenshot the current app, record screen, screen recording, record a video of the screen. DO NOT use this for taking photos with the camera — use termux-camera-photo for that instead."
metadata:
  {
    "openclaw":
      {
        "emoji": "📸",
        "requires": { "bins": ["adb", "scp"] },
      },
  }
---

# Screenshot — ClawBot

截取手机**屏幕画面**（不是相机拍照）并上传到云端在聊天中显示。

**重要：这是屏幕截图，不是相机拍照。拍照请用 `termux-camera-photo`。**

## How It Works

```
adb exec-out screencap -p → $TMPDIR/screenshot.png → SCP → Cloud /var/www/clawbot-photos/ → Browser
```

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

## Screen Recording（录屏）

录制屏幕 N 秒后上传到云端：

```bash
CLOUD="root@114.55.130.197"
PHOTO_DIR="/var/www/clawbot-photos"
BASE_URL="http://114.55.130.197:28790/photos"
SECONDS=10   # 录制时长（秒），最长180
TS=$(date +%Y%m%d-%H%M%S)
VID_NAME="screenrecord-$TS.mp4"
VID_DEVICE="/sdcard/$VID_NAME"
VID_LOCAL="$TMPDIR/$VID_NAME"

# 录制（adb shell 后台跑，sleep 等待完成）
adb shell "screenrecord --time-limit $SECONDS $VID_DEVICE" \
  && adb pull "$VID_DEVICE" "$VID_LOCAL" \
  && adb shell "rm -f $VID_DEVICE" \
  && echo "Recorded: $(du -sh $VID_LOCAL | cut -f1)" \
  && scp -o StrictHostKeyChecking=no "$VID_LOCAL" "$CLOUD:$PHOTO_DIR/$VID_NAME" \
  && ssh -o StrictHostKeyChecking=no "$CLOUD" "chmod 644 $PHOTO_DIR/$VID_NAME" \
  && echo "[$VID_NAME]($BASE_URL/$VID_NAME)" \
  && rm -f "$VID_LOCAL"
```

> `--time-limit` 最大 180 秒。录制期间手机屏幕需亮屏。

## Guidelines

- `screencap` captures whatever is currently on screen — remind user to navigate to the desired app/screen first.
- The file is a PNG (full resolution). Expect 1–5MB file size.
- After successful upload, output the Markdown image `![name](url)` — chat UI renders it inline.
- Always clean up temp files after upload.
- If `scp` fails, check SSH connectivity: `ssh -o StrictHostKeyChecking=no root@114.55.130.197 echo ok`
