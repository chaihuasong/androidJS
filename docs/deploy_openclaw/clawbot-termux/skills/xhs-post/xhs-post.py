#!/data/data/com.termux/files/usr/bin/python3
"""
Xiaohongshu (小红书) auto-post — 全程 uiautomator 驱动
Usage: xhs-post.py "title" "content"
"""
import subprocess, time, sys, re, os
import xml.etree.ElementTree as ET

# Android 系统命令在 /system/bin，Termux 的 PATH 默认不含此路径
os.environ['PATH'] = '/system/bin:/system/xbin:' + os.environ.get('PATH', '')

TITLE   = sys.argv[1] if len(sys.argv) > 1 else "测试标题"
CONTENT = sys.argv[2] if len(sys.argv) > 2 else "测试内容"
XHS_PKG = "com.xingin.xhs"

# ── 基础工具 ──────────────────────────────────────────────────────────────────

def shell(cmd, timeout=15):
    subprocess.run(cmd, shell=True, capture_output=True, timeout=timeout)

def run(cmd, timeout=15):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()

def sleep(s): time.sleep(s)
def log(msg): print(msg, flush=True)

def screenshot(label="preview"):
    path = f"/sdcard/xhs-{label}.png"
    shell(f"screencap -p {path}")
    return path

# ── uiautomator UI 分析 ───────────────────────────────────────────────────────

UI_DUMP_PATH = "/sdcard/ui_xhs.xml"

def dump_ui(retries=3):
    """dump 当前屏幕 UI，返回解析后的根节点。--compressed 更快，超时 30s，失败自动重试。"""
    for attempt in range(1, retries + 1):
        subprocess.run(
            f"uiautomator dump --compressed {UI_DUMP_PATH} 2>/dev/null",
            shell=True, capture_output=True, timeout=30
        )
        sleep(0.3)
        xml_str = run(f"cat {UI_DUMP_PATH}", timeout=5)
        if xml_str and '<hierarchy' in xml_str:
            try:
                return ET.fromstring(xml_str)
            except ET.ParseError:
                pass
        if attempt < retries:
            log(f"  dump 第{attempt}次失败，重试...")
            sleep(1)
    return None

def all_nodes(root):
    if root is None: return []
    return list(root.iter('node'))

def node_center(node):
    nums = re.findall(r'\d+', node.get('bounds', ''))
    if len(nums) >= 4:
        return (int(nums[0]) + int(nums[2])) // 2, (int(nums[1]) + int(nums[3])) // 2
    return None

def find_node(root, texts=(), res_id_contains=(), clickable_only=False):
    """
    在 UI 树中查找节点。
    - texts: 匹配 text 或 content-desc（精确或包含）
    - res_id_contains: resource-id 包含指定字符串
    - clickable_only: 只返回 clickable=true 的节点
    """
    for node in all_nodes(root):
        if clickable_only and node.get('clickable') != 'true':
            continue
        t = node.get('text', '')
        d = node.get('content-desc', '')
        h = node.get('hint', '')
        rid = node.get('resource-id', '')
        for kw in texts:
            if kw in t or kw in d or kw in h:
                return node
        for kw in res_id_contains:
            if kw in rid:
                return node
    return None

def tap_node(node):
    c = node_center(node)
    if c:
        shell(f"input tap {c[0]} {c[1]}")
        sleep(1.0)
        return True
    return False

def require_node(root, step, texts=(), res_id_contains=(), clickable_only=False):
    """找到节点并点击，找不到则打印当前屏幕所有文本供调试，然后报错退出。"""
    node = find_node(root, texts=texts, res_id_contains=res_id_contains, clickable_only=clickable_only)
    if node is not None:
        tap_node(node)
        return node
    # 打印当前屏幕所有可见文本
    visible = []
    for n in all_nodes(root):
        t = n.get('text', '').strip()
        d = n.get('content-desc', '').strip()
        if t: visible.append(f'text="{t}"')
        elif d: visible.append(f'desc="{d}"')
    log(f"  [调试] 当前屏幕元素: {', '.join(visible[:30]) if visible else '(空)'}")
    log(f"ERROR: [{step}] 未找到目标元素 {list(texts) + list(res_id_contains)}")
    screenshot(f"err-{step.replace('/', '-')}")
    sys.exit(1)

# ── 剪贴板输入 ────────────────────────────────────────────────────────────────

def paste_text(text):
    subprocess.run(
        ["termux-clipboard-set"],
        input=text.encode('utf-8'),
        capture_output=True, timeout=5
    )
    sleep(0.6)
    shell("input keyevent 279")   # KEYCODE_PASTE
    sleep(0.6)

# ── 启动小红书 ────────────────────────────────────────────────────────────────

def launch_xhs():
    """通过 dumpsys 找到真实 Launcher Activity 并启动。"""
    out = run(f"dumpsys package {XHS_PKG}", timeout=20)
    lines = out.split('\n')
    activity = None
    for i, line in enumerate(lines):
        if 'android.intent.action.MAIN' in line:
            for j in list(range(i-1, max(i-6,0), -1)) + list(range(i+1, min(i+6, len(lines)))):
                m = re.search(rf'{re.escape(XHS_PKG)}/(\S+Activity\S*)', lines[j])
                if m:
                    activity = f"{XHS_PKG}/{m.group(1).rstrip(':')}"
                    break
        if activity:
            break

    if activity:
        log(f"  Activity: {activity}")
        shell(f"am start -n {activity}")
    else:
        log("  未找到 Activity，使用 LAUNCHER Intent 启动...")
        result = run(f"am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER {XHS_PKG}")
        if 'Error' in result:
            log(f"ERROR: 无法启动小红书 — {result}")
            sys.exit(1)

# ═════════════════════════════════════════════════════════════════════════════
# 主流程
# ═════════════════════════════════════════════════════════════════════════════

# 检查安装
if XHS_PKG not in run("pm list packages"):
    log("ERROR: 小红书未安装 (com.xingin.xhs)")
    sys.exit(1)

# ── 1. 启动 ──────────────────────────────────────────────────────────────────
log("[1/7] 启动小红书...")
launch_xhs()
sleep(4)

# ── 2. 点击「发布」 ───────────────────────────────────────────────────────────
log("[2/7] 点击「发布」...")
ui = dump_ui()
require_node(ui, "发布", texts=("发布",), clickable_only=True)
sleep(2)

# ── 3. 选择「写文字」 ──────────────────────────────────────────────────────────
log("[3/7] 选择「写文字」...")
ui = dump_ui()
require_node(ui, "写文字", texts=("写文字",))
sleep(2)

# ── 4. 切换到「写长文」 ────────────────────────────────────────────────────────
log("[4/7] 切换到「写长文」...")
ui = dump_ui()
node = find_node(ui, texts=("写长文",))
if node is not None:
    tap_node(node)
    sleep(2)
    # 关闭可能出现的引导弹窗
    ui = dump_ui()
    popup = find_node(ui, texts=("去试试", "知道了", "我知道了", "立即体验"))
    if popup is not None:
        log("  关闭引导弹窗...")
        tap_node(popup)
        sleep(2)
else:
    log("  未找到「写长文」，继续在当前编辑模式")

# ── 5. 输入标题 ───────────────────────────────────────────────────────────────
log("[5/7] 输入标题...")
ui = dump_ui()
require_node(ui, "标题框", texts=("输入标题", "填写标题", "添加标题", "标题"))
sleep(0.4)
paste_text(TITLE)

# ── 6. 输入正文 ───────────────────────────────────────────────────────────────
log("[6/7] 输入正文...")
ui = dump_ui()
# 尝试常见占位文字 / resource-id 关键词
node = find_node(ui,
    texts=("粘贴到这里或输入文字，内容将自动保存",
           "说点什么或提个问题...",
           "记录真实的生活...",
           "开始创作..."),
    res_id_contains=("content_edit", "editor", "body"))
if node is None:
    log("  未找到正文框，截图后退出供排查")
    screenshot("err-content")
    log(f"  截图: /sdcard/xhs-err-content.png")
    sys.exit(1)
tap_node(node)
sleep(0.4)
paste_text(CONTENT)

# ── 7. 截图 ───────────────────────────────────────────────────────────────────
log("[7/7] 截图保存...")
path = screenshot("preview")
log(f"截图: {path}")
log("")
log("✅ 标题和正文已填写完成，请在手机上确认内容后点击「下一步」→「发布」。")
