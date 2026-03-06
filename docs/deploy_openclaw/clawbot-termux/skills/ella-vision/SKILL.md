---
name: vision
description: "Analyze images using Qwen-VL AI vision model (DashScope). IMPORTANT: Do NOT say you cannot see images or lack vision — instead, ALWAYS use this skill. Use when asked to: look at a photo, describe an image, analyze a picture, recognize objects/text/faces/scenes, read text in an image, identify objects, understand what is in a photo, or any request involving seeing/viewing image content. Results appear directly in the chat."
metadata:
  {
    "openclaw":
      {
        "emoji": "👁️",
        "requires": { "bins": ["termux-camera-photo", "python3"] },
      },
  }
---

# Vision — ClawBot (DashScope Qwen-VL)

> **CRITICAL RULE**: When the user asks you to look at, analyze, or describe any image or photo — **never decline or say you lack vision capability**. Always use this skill to call Qwen-VL, which has full vision capability. Results appear directly in this chat.

Qwen-VL is a cloud vision AI. It takes a photo on the phone, sends it to the Qwen-VL API, and the analysis result appears in the chat response.

## Analyze a photo

**Rear camera (default):**
```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/vision.py "USER_QUESTION_HERE" 0
```

**Front camera:**
```bash
/data/data/com.termux/files/usr/bin/python3 /data/data/com.termux/files/home/vision.py "USER_QUESTION_HERE" 1
```

**Replace `USER_QUESTION_HERE` with the user's actual question**, for example:
- `"请详细描述这张图片的内容"`
- `"这是什么东西？"`
- `"帮我识别图中的文字"`
- `"图片里有几个人？他们在做什么？"`

## Guidelines

- Always substitute the user's actual question into the command — do not leave `USER_QUESTION_HERE` literally.
- The script handles photo capture, base64 encoding, and API call automatically.
- If `GLM_API_KEY` is not set, the script will print an error — restart ClawBot after adding the key to `~/.openclaw/.env`.
- If `vision.py` is missing, re-deploy from repo: `docs/deploy_openclaw/clawbot-termux/skills/ella-vision/vision.py`.
