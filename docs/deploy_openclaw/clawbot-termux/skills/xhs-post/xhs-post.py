#!/data/data/com.termux/files/usr/bin/python3
"""
Xiaohongshu (小红书) auto-post — 写长文模式
Usage: xhs-post.py "title" "content"
Run from Termux context (via ClawBot exec/bash tool).
"""
import subprocess, time, sys, re, os
import xml.etree.ElementTree as ET

TITLE   = sys.argv[1] if len(sys.argv) > 1 else "测试标题"
CONTENT = sys.argv[2] if len(sys.argv) > 2 else "测试内容"
XHS_PKG = "com.xingin.xhs"
XHS_ACT = "com.xingin.xhs/.index.v2.IndexActivityV2"

def shell(cmd, timeout=15):
    subprocess.run(cmd, shell=True, capture_output=True, timeout=timeout)

def run(cmd, timeout=10):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()

def tap(x, y):
    shell(f"input tap {x} {y}")
    time.sleep(1.0)

def sleep(s): time.sleep(s)
def log(msg): print(msg, flush=True)

def dump_ui(path="/sdcard/ui_xhs.xml"):
    shell(f"uiautomator dump {path} 2>/dev/null")
    sleep(0.5)
    return run(f"cat {path}")

def find_text(xml_str, *texts):
    try:
        root = ET.fromstring(xml_str)
    except ET.ParseError:
        return None
    for node in root.iter('node'):
        t = node.get('text', '')
        d = node.get('content-desc', '')
        if t in texts or d in texts:
            return node
    return None

def tap_node(node):
    if node is None: return False
    nums = re.findall(r'\d+', node.get('bounds', ''))
    if len(nums) >= 4:
        x = (int(nums[0]) + int(nums[2])) // 2
        y = (int(nums[1]) + int(nums[3])) // 2
        tap(x, y)
        return True
    return False

def set_clip_and_paste(text):
    """Write text to temp file, then set clipboard via Termux API."""
    tmp = "/data/data/com.termux/files/usr/tmp/xhs_clip.txt"
    # Write to temp file then use cat to pipe to clipboard
    with open(tmp, 'w') as f:
        f.write(text)
    # termux-clipboard-set reads from stdin
    subprocess.run(
        ["termux-clipboard-set"],
        input=text.encode('utf-8'),
        capture_output=True,
        timeout=5
    )
    sleep(0.8)
    shell("input keyevent 279")  # KEYCODE_PASTE
    sleep(0.8)

def screenshot(label=""):
    path = f"/sdcard/xhs-{label}.png" if label else "/sdcard/xhs-preview.png"
    shell(f"screencap -p {path}")
    return path

# ── Check ─────────────────────────────────────────────────────────────────────
if XHS_PKG not in run("pm list packages"):
    log("ERROR: 小红书未安装 (com.xingin.xhs)")
    sys.exit(1)

# ── 1. Launch ─────────────────────────────────────────────────────────────────
log("[1/7] 启动小红书...")
shell(f"am start -n {XHS_ACT}")
sleep(4)

# ── 2. Tap 发布 ───────────────────────────────────────────────────────────────
log("[2/7] 点击「发布」...")
ui = dump_ui()
node = find_text(ui, "发布")
if node is not None:
    tap_node(node)
else:
    tap(540, 2263)
sleep(2)

# ── 3. Tap 写文字 ─────────────────────────────────────────────────────────────
log("[3/7] 选择「写文字」...")
ui = dump_ui()
node = find_text(ui, "写文字")
if node is not None:
    tap_node(node)
else:
    tap(540, 2083)
sleep(2)

# ── 4. Switch to 写长文 ───────────────────────────────────────────────────────
log("[4/7] 切换到「写长文」...")
ui = dump_ui()
node = find_text(ui, "写长文")
if node is not None:
    tap_node(node)
    sleep(2)
    # Dismiss intro popup if shown
    ui = dump_ui()
    popup = find_text(ui, "去试试", "知道了", "我知道了", "立即体验")
    if popup is not None:
        log("  关闭引导弹窗...")
        tap_node(popup)
        sleep(2)
else:
    log("  未找到「写长文」，继续在当前模式操作")

# ── 5. Input title ────────────────────────────────────────────────────────────
log("[5/7] 输入标题...")
ui = dump_ui()
node = find_text(ui, "输入标题", "填写标题", "添加标题")
if node is not None:
    tap_node(node)
else:
    tap(540, 264)
sleep(0.5)
set_clip_and_paste(TITLE)

# ── 6. Input content ──────────────────────────────────────────────────────────
log("[6/7] 输入正文...")
ui = dump_ui()
node = find_text(ui, "粘贴到这里或输入文字，内容将自动保存",
                     "说点什么或提个问题...", "记录真实的生活...")
if node is not None:
    tap_node(node)
else:
    tap(540, 800)
sleep(0.5)
set_clip_and_paste(CONTENT)

# ── 7. Screenshot ─────────────────────────────────────────────────────────────
log("[7/7] 截图保存...")
path = screenshot("preview")
log(f"截图: {path}")
log("")
log("✅ 标题和正文已填写完成，请在手机上确认内容后点击「下一步」→「发布」。")
