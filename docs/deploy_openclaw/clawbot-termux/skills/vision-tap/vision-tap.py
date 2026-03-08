#!/data/data/com.termux/files/usr/bin/python3
"""
vision-tap — 视觉AI驱动的界面元素定位与点击工具
当 uiautomator 无法识别界面（如微信主界面）时使用。

Usage:
  vision-tap.py tap       <description>   # 描述元素，AI定位后点击
  vision-tap.py longpress <description>   # 描述元素，AI定位后长按（触发右键菜单/粘贴菜单）
  vision-tap.py find      <description>   # 描述元素，返回坐标（不操作）
"""
import subprocess, sys, os, json, base64, time, re
import urllib.request, urllib.error

ADB      = '/data/data/com.termux/files/usr/bin/adb'
INPUT    = '/system/bin/input'
WM       = '/system/bin/wm'
SCREENCAP= '/system/bin/screencap'
TMPDIR   = os.environ.get('TMPDIR', '/data/data/com.termux/files/usr/tmp')

# 若 API key 不在环境变量里，从 ~/.openclaw/.env 加载
def _load_env():
    env_path = os.path.expanduser('~/.openclaw/.env')
    if not os.path.exists(env_path):
        return
    with open(env_path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#') or '=' not in line:
                continue
            k, v = line.split('=', 1)
            k = k.strip()
            v = v.strip().strip('"\'')
            if k and k not in os.environ:
                os.environ[k] = v

if not any(os.environ.get(k) for k in ['DASHSCOPE_API_KEY', 'GEMINI_API_KEY', 'SILICONFLOW_API_KEY']):
    _load_env()

# 视觉 API 后端（与 vision.py 保持一致）
BACKENDS = []
if os.environ.get('DASHSCOPE_API_KEY'):
    BACKENDS.append({
        'name':  'DashScope qwen-vl-max',
        'url':   'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
        'key':   os.environ['DASHSCOPE_API_KEY'],
        'model': 'qwen-vl-max',
    })
if os.environ.get('GEMINI_API_KEY'):
    BACKENDS.append({
        'name':  'Gemini Flash',
        'url':   'https://generativelanguage.googleapis.com/v1beta/openai/chat/completions',
        'key':   os.environ['GEMINI_API_KEY'],
        'model': 'gemini-2.0-flash',
    })
if os.environ.get('SILICONFLOW_API_KEY'):
    BACKENDS.append({
        'name':  'SiliconFlow Qwen2.5-VL-32B',
        'url':   'https://api.siliconflow.cn/v1/chat/completions',
        'key':   os.environ['SILICONFLOW_API_KEY'],
        'model': 'Qwen/Qwen2.5-VL-32B-Instruct',
    })

def log(msg): print(msg, flush=True)


def get_screen_size():
    out = subprocess.run(f'{ADB} shell wm size', shell=True,
                         capture_output=True, text=True, timeout=5).stdout
    m = re.search(r'(\d+)x(\d+)', out)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)


def take_screenshot():
    """截图并压缩，返回 (jpg路径, 屏幕宽, 屏幕高, 图片宽, 图片高)。"""
    ts   = int(time.time())
    raw  = f'{TMPDIR}/vtap_raw_{ts}.png'
    jpg  = f'{TMPDIR}/vtap_{ts}.jpg'

    # adb exec-out 直接读取截图字节流（最快）
    r = subprocess.run([ADB, 'exec-out', 'screencap', '-p'],
                       capture_output=True, timeout=12)
    if r.returncode == 0 and len(r.stdout) > 1000:
        with open(raw, 'wb') as f:
            f.write(r.stdout)
    else:
        # fallback: screencap → pull
        subprocess.run(f'{ADB} shell screencap -p /sdcard/vtap_tmp.png',
                       shell=True, timeout=10)
        subprocess.run(f'{ADB} pull /sdcard/vtap_tmp.png {raw}',
                       shell=True, timeout=10)
        subprocess.run(f'{ADB} shell rm -f /sdcard/vtap_tmp.png',
                       shell=True, timeout=5)

    # PIL 压缩：缩小 50%，减少 API token 消耗
    try:
        from PIL import Image
        img      = Image.open(raw)
        sw, sh   = img.size          # 实际屏幕分辨率
        iw, ih   = sw // 2, sh // 2  # 压缩后图片尺寸
        img      = img.convert('RGB').resize((iw, ih))
        img.save(jpg, 'JPEG', quality=80)
        try: os.remove(raw)
        except: pass
        log(f"  截图 {sw}x{sh} → 压缩为 {iw}x{ih} JPEG（{os.path.getsize(jpg)//1024} KB）")
        return jpg, sw, sh, iw, ih
    except Exception as e:
        log(f"  PIL 不可用（{e}），使用原始 PNG")
        sw, sh = get_screen_size()
        return raw, sw, sh, sw, sh


def vision_locate(image_path, description, screen_w, screen_h, img_w, img_h):
    """
    调用视觉 AI 定位元素，返回实际屏幕坐标 (x, y)，未找到返回 None。
    坐标以百分比形式请求，换算时不依赖图片尺寸，更鲁棒。
    """
    if not BACKENDS:
        log("ERROR: 未配置视觉 API（需要 DASHSCOPE_API_KEY / GEMINI_API_KEY / SILICONFLOW_API_KEY）")
        sys.exit(1)

    with open(image_path, 'rb') as f:
        b64 = base64.b64encode(f.read()).decode()
    mime = 'image/jpeg' if image_path.endswith('.jpg') else 'image/png'

    prompt = f"""这是一张 Android 手机截图（图片尺寸 {img_w}x{img_h}，对应实际屏幕 {screen_w}x{screen_h}）。

请找到界面中的元素：「{description}」

**只返回 JSON，不要任何其他文字：**

找到时：
{{"found": true, "x_pct": <元素中心点X占图片宽度的百分比，0.0~1.0>, "y_pct": <元素中心点Y占图片高度的百分比，0.0~1.0>, "desc": "<你找到的元素简述>"}}

找不到时：
{{"found": false, "reason": "<原因>"}}"""

    payload = {
        'model': '',  # 每个 backend 覆盖
        'messages': [{'role': 'user', 'content': [
            {'type': 'image_url', 'image_url': {'url': f'data:{mime};base64,{b64}'}},
            {'type': 'text', 'text': prompt},
        ]}],
        'max_tokens': 256,
    }

    for backend in BACKENDS:
        log(f"  → {backend['name']}...")
        payload['model'] = backend['model']
        req = urllib.request.Request(
            backend['url'],
            data=json.dumps(payload).encode(),
            headers={'Authorization': f"Bearer {backend['key']}",
                     'Content-Type': 'application/json'},
        )
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                data    = json.loads(resp.read())
                raw_txt = data['choices'][0]['message']['content'].strip()
        except urllib.error.HTTPError as e:
            log(f"    ✗ HTTP {e.code}: {e.read().decode()[:120]}")
            continue
        except Exception as e:
            log(f"    ✗ {e}")
            continue

        # 提取 JSON（去除可能的 markdown 代码块）
        try:
            m = re.search(r'\{.*\}', raw_txt, re.DOTALL)
            if not m:
                log(f"    ✗ 响应中无 JSON: {raw_txt[:100]}")
                continue
            result = json.loads(m.group())
        except Exception as e:
            log(f"    ✗ JSON 解析失败: {e}  原文: {raw_txt[:100]}")
            continue

        if not result.get('found'):
            log(f"  NOT FOUND: {result.get('reason', '未找到元素')}")
            return None

        x_pct = float(result['x_pct'])
        y_pct = float(result['y_pct'])
        real_x = int(x_pct * screen_w)
        real_y = int(y_pct * screen_h)
        log(f"  找到: {result.get('desc', description)}")
        log(f"  百分比: ({x_pct:.3f}, {y_pct:.3f})  →  屏幕坐标: ({real_x}, {real_y})")
        return real_x, real_y

    log("ERROR: 所有视觉 API 均失败")
    return None


def tap_xy(x, y):
    subprocess.run([ADB, 'shell', f'input tap {x} {y}'],
                   capture_output=True, timeout=5)
    time.sleep(0.8)


def longpress_xy(x, y, duration=1200):
    """长按：用 swipe 起止点相同模拟长按。"""
    subprocess.run([ADB, 'shell', f'input swipe {x} {y} {x} {y} {duration}'],
                   capture_output=True, timeout=duration // 1000 + 5)
    time.sleep(0.6)


def _locate(description):
    """截图 + 视觉定位，返回 (x, y) 或失败时 exit(1)。"""
    log('  截图中...')
    image_path, sw, sh, iw, ih = take_screenshot()
    try:
        coords = vision_locate(image_path, description, sw, sh, iw, ih)
    finally:
        try: os.remove(image_path)
        except: pass
    if coords is None:
        sys.exit(1)
    return coords


def cmd_tap(description):
    log(f"视觉点击: 「{description}」")
    x, y = _locate(description)
    log(f'  点击 ({x}, {y})')
    tap_xy(x, y)
    log('OK')


def cmd_longpress(description):
    log(f"视觉长按: 「{description}」")
    x, y = _locate(description)
    log(f'  长按 ({x}, {y})')
    longpress_xy(x, y)
    log('OK')


def cmd_find(description):
    log(f"视觉定位: 「{description}」")
    x, y = _locate(description)
    log(f'坐标: ({x}, {y})')
    # 机器可读格式，供调用方 parse
    print(f'COORD:{x},{y}', flush=True)


# ── 入口 ──────────────────────────────────────────────────────────────────────

if len(sys.argv) < 3:
    print(__doc__)
    sys.exit(0 if len(sys.argv) == 1 else 1)

cmd         = sys.argv[1]
description = ' '.join(sys.argv[2:])

if cmd == 'tap':
    cmd_tap(description)
elif cmd == 'longpress':
    cmd_longpress(description)
elif cmd == 'find':
    cmd_find(description)
else:
    print(__doc__)
    sys.exit(1)
