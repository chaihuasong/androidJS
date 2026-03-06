---
name: ella-vision
description: "Use Ella AI assistant (com.transsion.aivoiceassistant) to analyze and describe images. Use when asked to: recognize what's in a photo, describe an image, analyze a picture, identify objects/text/scenes in photos, or understand image content. Requires Ella app installed on the phone."
metadata:
  {
    "openclaw":
      {
        "emoji": "👁️",
        "requires": { "bins": ["am", "termux-camera-photo"] },
      },
  }
---

# Ella Vision — ClawBot

You can use Ella (com.transsion.aivoiceassistant), the phone's built-in AI assistant, to analyze and describe images. Ella supports image understanding via its NormalShareActivity.

## Architecture

```
Take photo / Use existing image
  → am start ACTION_SEND image/jpeg → Ella NormalShareActivity
  → Ella analyzes the image with its vision model
  → Result displayed on phone screen
```

## Commands

### Share an image to Ella for analysis

```bash
am start -a android.intent.action.SEND \
  -t image/jpeg \
  -n com.transsion.aivoiceassistant/com.transsion.ella.pages.share.NormalShareActivity \
  --eu android.intent.extra.STREAM "file:///path/to/image.jpg" \
  -f 0x00000001
```

- `-f 0x00000001` is FLAG_GRANT_READ_URI_PERMISSION, required for file access.
- Supported types: `image/jpeg`, `image/png`

### Take a new photo and send to Ella

**Rear camera:**
```bash
mkdir -p /tmp/clawbot-photos
PHOTO_NAME="ella-$(date +%Y%m%d-%H%M%S).jpg"
timeout 10 termux-camera-photo -c 0 /tmp/clawbot-photos/$PHOTO_NAME \
  && am start -a android.intent.action.SEND \
       -t image/jpeg \
       -n com.transsion.aivoiceassistant/com.transsion.ella.pages.share.NormalShareActivity \
       --eu android.intent.extra.STREAM "file:///tmp/clawbot-photos/$PHOTO_NAME" \
       -f 0x00000001
```

**Front camera:** change `-c 0` to `-c 1`.

### Analyze an existing photo on the phone

Photos are typically stored in `/sdcard/Pictures/` or `/sdcard/DCIM/Camera/`.

```bash
# List available photos
ls /sdcard/Pictures/
ls /sdcard/DCIM/Camera/

# Send a specific photo to Ella
am start -a android.intent.action.SEND \
  -t image/jpeg \
  -n com.transsion.aivoiceassistant/com.transsion.ella.pages.share.NormalShareActivity \
  --eu android.intent.extra.STREAM "file:///sdcard/Pictures/example.jpg" \
  -f 0x00000001
```

### Share a PNG image

```bash
am start -a android.intent.action.SEND \
  -t image/png \
  -n com.transsion.aivoiceassistant/com.transsion.ella.pages.share.NormalShareActivity \
  --eu android.intent.extra.STREAM "file:///path/to/image.png" \
  -f 0x00000001
```

## Ella also supports documents

NormalShareActivity accepts `text/plain`, `application/pdf`, and Word documents (`.docx`, `.doc`):

```bash
# Share a PDF
am start -a android.intent.action.SEND \
  -t application/pdf \
  -n com.transsion.aivoiceassistant/com.transsion.ella.pages.share.NormalShareActivity \
  --eu android.intent.extra.STREAM "file:///sdcard/Documents/example.pdf" \
  -f 0x00000001
```

## Guidelines

- Always verify the image file exists before sending: `ls -la /path/to/image`
- Ella processes images on-device — the analysis result appears on the phone screen.
- The user will read Ella's response from the phone screen and relay it back.
- If `termux-camera-photo` fails, suggest checking camera permissions.
- Always use `-f 0x00000001` (FLAG_GRANT_READ_URI_PERMISSION) for file URI access.
- Clean up temp photos: `rm /tmp/clawbot-photos/ella-*.jpg`
