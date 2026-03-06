#!/data/data/com.termux/files/usr/bin/python3
"""
Vision analysis — DashScope qwen-vl-max (primary), SiliconFlow Qwen2.5-VL-32B (fallback).
Usage: glm-vision.py "question" [camera=0|1]
"""
import subprocess, base64, json, urllib.request, urllib.error, os, sys

QUESTION = sys.argv[1] if len(sys.argv) > 1 else "请详细描述这张图片的内容"
CAMERA   = int(sys.argv[2]) if len(sys.argv) > 2 else 0

TMPDIR   = "/data/data/com.termux/files/usr/tmp"
PHOTO    = f"{TMPDIR}/vision-raw.jpg"
PHOTO_SM = f"{TMPDIR}/vision-small.jpg"

# API priority: DashScope → Gemini → SiliconFlow
BACKENDS = []
if os.environ.get("DASHSCOPE_API_KEY"):
    BACKENDS.append({
        "name": "DashScope qwen-vl-max",
        "url":  "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        "key":  os.environ["DASHSCOPE_API_KEY"],
        "model":"qwen-vl-max",
    })
if os.environ.get("GEMINI_API_KEY"):
    BACKENDS.append({
        "name": "Gemini 3 Pro",
        "url":  "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
        "key":  os.environ["GEMINI_API_KEY"],
        "model":"gemini-3-pro-preview",
    })
if os.environ.get("SILICONFLOW_API_KEY"):
    BACKENDS.append({
        "name": "SiliconFlow Qwen2.5-VL-32B",
        "url":  "https://api.siliconflow.cn/v1/chat/completions",
        "key":  os.environ["SILICONFLOW_API_KEY"],
        "model":"Qwen/Qwen2.5-VL-32B-Instruct",
    })

if not BACKENDS:
    print("ERROR: 请在 ~/.openclaw/.env 中设置 DASHSCOPE_API_KEY / GEMINI_API_KEY / SILICONFLOW_API_KEY")
    sys.exit(1)

# [1/4] Take photo
print(f"[1/4] Taking photo (camera {CAMERA})...")
r = subprocess.run(
    ["/data/data/com.termux/files/usr/bin/termux-camera-photo", "-c", str(CAMERA), PHOTO],
    timeout=15
)
if r.returncode != 0 or not os.path.exists(PHOTO) or os.path.getsize(PHOTO) == 0:
    print("ERROR: photo capture failed")
    sys.exit(1)
print(f"Raw photo: {os.path.getsize(PHOTO)//1024} KB")

# [2/4] Compress
print("[2/4] Compressing image...")
r2 = subprocess.run(
    ["convert", PHOTO, "-resize", "1024x1024>", "-quality", "85", PHOTO_SM],
    capture_output=True
)
if r2.returncode == 0 and os.path.exists(PHOTO_SM) and os.path.getsize(PHOTO_SM) > 0:
    send_path = PHOTO_SM
    print(f"Compressed: {os.path.getsize(PHOTO_SM)//1024} KB")
else:
    send_path = PHOTO
    print("(skipped compression, sending original)")

# [3/4] Encode
print("[3/4] Encoding...")
with open(send_path, "rb") as f:
    b64 = base64.b64encode(f.read()).decode()
for p in [PHOTO, PHOTO_SM]:
    try: os.remove(p)
    except: pass

# [4/4] Call API with fallback
print("[4/4] Calling vision API...")
for backend in BACKENDS:
    print(f"  → {backend['name']}...")
    payload = {
        "model": backend["model"],
        "messages": [{"role": "user", "content": [
            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}},
            {"type": "text", "text": QUESTION}
        ]}],
        "max_tokens": 1024
    }
    req = urllib.request.Request(
        backend["url"],
        data=json.dumps(payload).encode(),
        headers={"Authorization": f"Bearer {backend['key']}", "Content-Type": "application/json"}
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read())
            content = data["choices"][0]["message"]["content"]
            print(f"\n**视觉分析结果（{backend['name']}）：**\n")
            print(content)
            sys.exit(0)
    except urllib.error.HTTPError as e:
        print(f"  ✗ HTTP {e.code}: {e.read().decode()[:200]}")
    except Exception as e:
        print(f"  ✗ {e}")

print("ERROR: 所有视觉 API 均失败")
sys.exit(1)
