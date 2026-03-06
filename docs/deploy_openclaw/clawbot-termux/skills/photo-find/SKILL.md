---
name: photo-find
description: "Browse, search and display existing photos from phone storage. Use when asked to: show recent photos, find photos from today/this week, browse camera roll, show screenshots, find photos by date, or display existing photos from the phone's gallery."
metadata:
  {
    "openclaw":
      {
        "emoji": "🔍",
        "requires": { "bins": ["scp", "find"] },
      },
  }
---

# Photo Find — ClawBot

Browse photos already on the phone, select and upload them to the cloud server for display in chat.

## Storage Locations

```
/sdcard/DCIM/Camera/       # Camera photos
/sdcard/Pictures/          # Screenshots, downloads, misc
/sdcard/Pictures/Screenshots/
/sdcard/Download/
```

## Workflows

### Show most recent N photos

```bash
# List the 10 most recent photos from camera roll
find /sdcard/DCIM/Camera /sdcard/Pictures -type f \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" \) \
  -newer /sdcard/DCIM/Camera/$(ls -t /sdcard/DCIM/Camera/ | tail -1) -o -type f \
  \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" \) 2>/dev/null \
  | xargs ls -t 2>/dev/null | head -10
```

Simpler version:
```bash
ls -t /sdcard/DCIM/Camera/*.jpg 2>/dev/null | head -10
ls -t /sdcard/Pictures/Screenshots/*.png 2>/dev/null | head -5
```

### Show photos from today

```bash
TODAY=$(date +%Y%m%d)
find /sdcard/DCIM/Camera /sdcard/Pictures -type f \
  \( -iname "*.jpg" -o -iname "*.jpeg" -o -iname "*.png" \) \
  -newer /proc/1/exe 2>/dev/null | head -20

# Or filter by filename date pattern (YYYYMMDD in name)
find /sdcard/DCIM/Camera -name "*${TODAY}*" -o -name "IMG_${TODAY}*" 2>/dev/null
```

### List recent photos with details

```bash
ls -lht /sdcard/DCIM/Camera/*.jpg 2>/dev/null | head -10
```

### Upload a specific photo and display in chat

```bash
CLOUD="root@114.55.130.197"
PHOTO_DIR="/var/www/clawbot-photos"
BASE_URL="http://114.55.130.197:28790/photos"

SOURCE_PATH="/sdcard/DCIM/Camera/IMG_XXXXXX.jpg"   # replace with actual path
PHOTO_NAME="photo-$(date +%Y%m%d-%H%M%S).jpg"

scp -o StrictHostKeyChecking=no "$SOURCE_PATH" "$CLOUD:$PHOTO_DIR/$PHOTO_NAME" \
  && ssh -o StrictHostKeyChecking=no "$CLOUD" "chmod 644 $PHOTO_DIR/$PHOTO_NAME" \
  && echo "![$PHOTO_NAME]($BASE_URL/$PHOTO_NAME)"
```

### Upload multiple recent photos (batch)

```bash
CLOUD="root@114.55.130.197"
PHOTO_DIR="/var/www/clawbot-photos"
BASE_URL="http://114.55.130.197:28790/photos"

# Get 3 most recent photos
PHOTOS=$(ls -t /sdcard/DCIM/Camera/*.jpg 2>/dev/null | head -3)

for SOURCE in $PHOTOS; do
  PHOTO_NAME="photo-$(date +%Y%m%d-%H%M%S)-$(basename $SOURCE)"
  scp -o StrictHostKeyChecking=no "$SOURCE" "$CLOUD:$PHOTO_DIR/$PHOTO_NAME" \
    && ssh -o StrictHostKeyChecking=no "$CLOUD" "chmod 644 $PHOTO_DIR/$PHOTO_NAME" \
    && echo "![$(basename $SOURCE)]($BASE_URL/$PHOTO_NAME)"
  sleep 1
done
```

### Search photos by keyword in filename

```bash
KEYWORD="screenshot"   # replace with search term
find /sdcard -type f \( -iname "*${KEYWORD}*.jpg" -o -iname "*${KEYWORD}*.png" \) 2>/dev/null | head -10
```

## Complete One-Shot: Show latest photo from camera roll

```bash
CLOUD="root@114.55.130.197"
PHOTO_DIR="/var/www/clawbot-photos"
BASE_URL="http://114.55.130.197:28790/photos"
SOURCE=$(ls -t /sdcard/DCIM/Camera/*.jpg 2>/dev/null | head -1)

if [ -z "$SOURCE" ]; then
  echo "No photos found in /sdcard/DCIM/Camera/"
else
  PHOTO_NAME="found-$(date +%Y%m%d-%H%M%S).jpg"
  echo "Found: $SOURCE ($(du -sh "$SOURCE" | cut -f1))"
  scp -o StrictHostKeyChecking=no "$SOURCE" "$CLOUD:$PHOTO_DIR/$PHOTO_NAME" \
    && ssh -o StrictHostKeyChecking=no "$CLOUD" "chmod 644 $PHOTO_DIR/$PHOTO_NAME" \
    && echo "![$PHOTO_NAME]($BASE_URL/$PHOTO_NAME)"
fi
```

## Guidelines

- Always list available photos first before uploading so user can confirm.
- Use `ls -lht` to show file sizes and timestamps for context.
- Prefer recent photos from `/sdcard/DCIM/Camera/` unless the user specifies another location.
- For screenshots, check `/sdcard/Pictures/Screenshots/`.
- Always verify the source file exists before SCP: `ls -la "$SOURCE"`.
- After uploading, output the Markdown image link — the chat UI renders it inline.
- Clean up old uploads periodically: `ssh root@114.55.130.197 "find /var/www/clawbot-photos -mtime +7 -delete"`
