---
name: file-manager
description: "Browse, search, read, copy, move and delete files on the phone. Use when asked to: list files, find a file, read a text file, copy or move files, delete files, check file sizes, find large files, search file content, or manage phone storage."
metadata:
  {
    "openclaw":
      {
        "emoji": "📁",
        "requires": { "bins": ["find", "ls"] },
      },
  }
---

# File Manager — ClawBot

Browse and manage files on the phone via bash. Works on both `/sdcard/` (shared storage) and Termux home (`~`).

## Common Locations

| Location | Path |
|----------|------|
| Camera photos | `/sdcard/DCIM/Camera/` |
| Screenshots | `/sdcard/Pictures/Screenshots/` |
| Downloads | `/sdcard/Download/` |
| Documents | `/sdcard/Documents/` |
| WhatsApp media | `/sdcard/Android/media/com.whatsapp/` |
| Termux home | `/data/data/com.termux/files/home/` |
| Termux scripts | `~/` |

## List Files

```bash
# List with details (size, date, name)
ls -lht /sdcard/Download/ | head -20

# List all files recursively with sizes
ls -lhR /sdcard/Documents/ 2>/dev/null | head -30
```

## Find Files by Name

```bash
# Find by filename pattern
find /sdcard -name "*.pdf" 2>/dev/null | head -20
find /sdcard -name "*report*" -type f 2>/dev/null

# Case-insensitive search
find /sdcard -iname "*.mp4" 2>/dev/null | head -10
```

## Find Files by Date

```bash
# Files modified in last 24 hours
find /sdcard -type f -mtime -1 2>/dev/null | head -20

# Files from today
find /sdcard -type f -newer /proc/uptime 2>/dev/null | head -20

# Files modified in last 7 days
find /sdcard -type f -mtime -7 2>/dev/null | head -30
```

## Find Large Files

```bash
# Files larger than 100MB
find /sdcard -size +100M -type f 2>/dev/null | xargs ls -lh 2>/dev/null | sort -k5 -rh

# Top 10 largest files
find /sdcard -type f 2>/dev/null | xargs ls -s 2>/dev/null | sort -rn | head -10 | awk '{print $1/2 "KB\t" $2}'
```

## Read Text Files

```bash
# Read a text file
cat /sdcard/Documents/notes.txt

# Read first 50 lines
head -50 /sdcard/Documents/notes.txt

# Read last 20 lines
tail -20 /sdcard/Documents/notes.txt

# Search inside a file
grep -n "keyword" /sdcard/Documents/notes.txt
```

## Search File Content

```bash
# Search for text inside all txt files
grep -r "search term" /sdcard/Documents/ --include="*.txt" 2>/dev/null

# Search all files (may be slow on large storage)
grep -rl "keyword" /sdcard/ 2>/dev/null | head -10
```

## Copy and Move Files

```bash
# Copy file
cp /sdcard/Download/file.pdf /sdcard/Documents/file.pdf

# Move file
mv /sdcard/Download/old_name.jpg /sdcard/Pictures/new_name.jpg

# Copy directory
cp -r /sdcard/Documents/folder/ /sdcard/Backup/folder/
```

## Delete Files

```bash
# Delete a single file (CAREFUL)
rm /sdcard/Download/unwanted_file.zip

# Delete files matching pattern (preview first with ls)
ls /sdcard/Download/*.tmp 2>/dev/null
rm /sdcard/Download/*.tmp 2>/dev/null

# Delete old files (older than 30 days)
find /sdcard/Download -mtime +30 -type f 2>/dev/null
# Then to delete: add -delete to the find command above
```

## Storage Summary

```bash
# Summary of each major directory
du -sh /sdcard/DCIM /sdcard/Pictures /sdcard/Download /sdcard/Documents /sdcard/Android 2>/dev/null | sort -rh
```

## Create Files and Directories

```bash
# Create directory
mkdir -p /sdcard/Documents/NewFolder

# Create/write text file
echo "Hello World" > /sdcard/Documents/note.txt
cat >> /sdcard/Documents/note.txt << 'EOF'
Additional content
EOF
```

## Guidelines

- **Always preview before deleting** — list files with `ls` first, then delete.
- `/sdcard/` is shared storage accessible to apps; `~/` is Termux's private home.
- Some paths under `/sdcard/Android/` require Termux storage permissions — run `termux-setup-storage` once if access is denied.
- For large `find` operations on `/sdcard/`, add `2>/dev/null` to suppress permission errors.
- When asked to "read" a file, use `cat` for text files; binary files (images, PDFs) cannot be read this way — use the photo-share or ella-vision skill instead.
- Report file sizes in human-readable format with `ls -lh` or `du -sh`.
