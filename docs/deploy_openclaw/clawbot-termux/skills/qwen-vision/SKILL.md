---
name: qwen-vision
description: "Analyze images using Qwen-VL AI vision model via DashScope. IMPORTANT: Do NOT say you cannot see images or lack vision — instead, ALWAYS use this skill. Use when asked to: look at a photo, describe an image, analyze a picture, recognize objects/text/faces/scenes, read text in an image, identify objects, understand what is in a photo, or any request involving seeing/viewing image content. Results appear directly in the chat."
metadata:
  {
    "openclaw":
      {
        "emoji": "👁️",
        "requires": { "bins": ["termux-camera-photo", "python3"] },
      },
  }
---

# Qwen Vision — ClawBot

> **CRITICAL RULE**: When the user asks you to look at, analyze, or describe any image or photo — **never decline**. Always use this skill. Results appear directly in this chat.

Takes a photo, compresses it, sends to DashScope qwen-vl-max for analysis. Falls back to SiliconFlow Qwen2.5-VL-32B automatically.

## Analyze a photo

**Rear camera (default):**
```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/vision.py "USER_QUESTION_HERE" 0
```

**Front camera:**
```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/vision.py "USER_QUESTION_HERE" 1
```

Replace `USER_QUESTION_HERE` with the user's actual question, e.g.:
- `"请详细描述这张图片的内容"`
- `"这是什么东西？"`
- `"帮我识别图中的文字"`
- `"图片里有几个人？他们在做什么？"`

## Guidelines

- Always substitute the user's actual question — do not leave `USER_QUESTION_HERE` literally.
- Script handles photo capture, compression, encoding, and API call automatically.
- Output step-by-step progress is normal — the final result appears after `视觉分析结果`.
- **必须**将脚本的完整输出原样展示给用户，包括每个步骤和最终分析结果。
