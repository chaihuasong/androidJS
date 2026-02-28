---
name: photo-share
description: "Transfer photos from phone to cloud server and display in chat. Use when asked to: take a photo and show it, show phone photos/screenshots, share images from the phone, or display any local image file in the chat."
metadata:
  {
    "openclaw":
      {
        "emoji": "🖼️",
        "requires": { "bins": ["scp", "termux-camera-photo"] },
      },
  }
---

# Photo Share — ClawBot

You can take photos on the phone or find existing images, upload them to the cloud server, and display them in the chat as inline images.

## Architecture

```
Phone (photo) → SCP → Cloud Server /var/www/clawbot-photos/ → Nginx /photos/ → Browser
```

## Step-by-step Workflow

### 1. Get the image

**Take a new photo (rear camera):**
```bash
mkdir -p /tmp/clawbot-photos
PHOTO_NAME="photo-$(date +%Y%m%d-%H%M%S).jpg"
timeout 10 termux-camera-photo -c 0 /tmp/clawbot-photos/$PHOTO_NAME
ls -la /tmp/clawbot-photos/$PHOTO_NAME
```

**Take a new photo (front camera):**
```bash
mkdir -p /tmp/clawbot-photos
PHOTO_NAME="photo-$(date +%Y%m%d-%H%M%S).jpg"
timeout 10 termux-camera-photo -c 1 /tmp/clawbot-photos/$PHOTO_NAME
ls -la /tmp/clawbot-photos/$PHOTO_NAME
```

**Use an existing image file:**
```bash
PHOTO_NAME="photo-$(date +%Y%m%d-%H%M%S).jpg"
cp /path/to/existing/image.jpg /tmp/clawbot-photos/$PHOTO_NAME
```

### 2. Upload to cloud server

```bash
scp -o StrictHostKeyChecking=no /tmp/clawbot-photos/$PHOTO_NAME root@114.55.130.197:/var/www/clawbot-photos/$PHOTO_NAME \
  && ssh -o StrictHostKeyChecking=no root@114.55.130.197 "chmod 644 /var/www/clawbot-photos/$PHOTO_NAME"
```

### 3. Display in chat

After successful upload, output a Markdown image to display it inline:

```
![photo](http://114.55.130.197:28790/photos/PHOTO_NAME)
```

Replace `PHOTO_NAME` with the actual filename (e.g., `photo-20260228-153000.jpg`).

## Complete Example (one-shot)

```bash
mkdir -p /tmp/clawbot-photos
PHOTO_NAME="photo-$(date +%Y%m%d-%H%M%S).jpg"
timeout 10 termux-camera-photo -c 0 /tmp/clawbot-photos/$PHOTO_NAME \
  && scp -o StrictHostKeyChecking=no /tmp/clawbot-photos/$PHOTO_NAME root@114.55.130.197:/var/www/clawbot-photos/$PHOTO_NAME \
  && ssh -o StrictHostKeyChecking=no root@114.55.130.197 "chmod 644 /var/www/clawbot-photos/$PHOTO_NAME" \
  && echo "![photo](http://114.55.130.197:28790/photos/$PHOTO_NAME)"
```

## Guidelines

- Always use a unique filename with timestamp to avoid overwriting.
- Always verify the photo file exists and has non-zero size before uploading.
- If `termux-camera-photo` fails, suggest the user check camera permissions.
- If `scp` fails, check SSH connectivity to the cloud server.
- Clean up old photos periodically: `rm /tmp/clawbot-photos/photo-*.jpg`
- The cloud server directory is `/var/www/clawbot-photos/`, served at `http://114.55.130.197:28790/photos/`.
- After outputting the Markdown image link, the chat UI will render it as an inline image.
