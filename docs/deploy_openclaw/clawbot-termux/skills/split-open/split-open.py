#!/data/data/com.termux/files/usr/bin/python3
"""
Open any app in the bottom half of split screen on TECNO phones.
The current foreground app stays on top.

Usage: split-open.py "App Name"
Example: split-open.py "小红书"
         split-open.py "微信"
"""
import subprocess, time, sys, re

APP_NAME = sys.argv[1] if len(sys.argv) > 1 else None

# Common apps: display name → package name (partial match, case-insensitive)
KNOWN_APPS = {
    '微信': 'com.tencent.mm',
    'wechat': 'com.tencent.mm',
    '小红书': 'com.xingin.xhs',
    '红书': 'com.xingin.xhs',
    'xhs': 'com.xingin.xhs',
    '抖音': 'com.ss.android.ugc.aweme',
    'douyin': 'com.ss.android.ugc.aweme',
    'bilibili': 'tv.danmaku.bili',
    'b站': 'tv.danmaku.bili',
    'bili': 'tv.danmaku.bili',
    '支付宝': 'com.eg.android.AlipayGphone',
    'alipay': 'com.eg.android.AlipayGphone',
    '淘宝': 'com.taobao.taobao',
    '京东': 'com.jingdong.app.mall',
    '网易云': 'com.netease.cloudmusic',
    '高德': 'com.autonavi.minimap',
    '百度地图': 'com.baidu.BaiduMap',
    'chrome': 'com.android.chrome',
    'youtube': 'com.google.android.youtube',
    'telegram': 'org.telegram.messenger',
    'whatsapp': 'com.whatsapp',
    '设置': 'com.android.settings',
    'settings': 'com.android.settings',
    '相机': 'com.mediatek.camera',
    '计算器': 'com.android.calculator2',
    'termux': 'com.termux',
}


def run(cmd, timeout=10):
    return subprocess.run(
        cmd, shell=True, capture_output=True, text=True, timeout=timeout
    ).stdout.strip()


def shell(cmd, timeout=15):
    subprocess.run(cmd, shell=True, capture_output=True, timeout=timeout)


def sleep(s):
    time.sleep(s)


def log(msg):
    print(msg, flush=True)


def find_package(app_name):
    """Find package name by display name."""
    name_lower = app_name.lower()

    # 1. Check known apps dict (partial match both ways)
    for key, pkg in KNOWN_APPS.items():
        if name_lower in key.lower() or key.lower() in name_lower:
            return pkg

    # 2. Search installed packages by keyword in package name
    pkgs = run("pm list packages -3").split('\n')
    for line in pkgs:
        pkg = line.replace('package:', '').strip()
        if pkg and name_lower in pkg.lower():
            return pkg

    return None


def get_main_activity(pkg):
    """Get main launcher activity for package."""
    out = run(
        f"cmd package resolve-activity --brief "
        f"-c android.intent.category.LAUNCHER {pkg}"
    )
    for line in out.split('\n'):
        line = line.strip()
        if '/' in line and not line.startswith(('No activity', 'Warning', 'Unable')):
            return line
    return None


def get_task_id(pkg):
    """Get the task ID of a running app by package name."""
    out = run("dumpsys activity activities")
    # Match: Task{... #<id> type=standard A=<uid>:<pkg> ...}
    pattern = rf'Task\{{[^ ]+ #(\d+) type=standard A=\d+:{re.escape(pkg)}'
    m = re.search(pattern, out)
    if m:
        return int(m.group(1))
    return None


def move_to_side_stage(task_id, position=1):
    """
    Move task to split screen side stage.
    position: 0 = top, 1 = bottom
    """
    result = run(f"wm shell splitscreen moveToSideStage {task_id} {position}")
    return result


if not APP_NAME:
    log('Usage: split-open.py "App Name"')
    log('Example: split-open.py "小红书"')
    sys.exit(1)

# ── 1. Find target app package ────────────────────────────────────────────────
log(f"[1/4] 查找「{APP_NAME}」...")
pkg = find_package(APP_NAME)

if not pkg:
    log(f"ERROR: 未找到应用「{APP_NAME}」，请确认应用名称或包名")
    sys.exit(1)

log(f"  包名: {pkg}")

# ── 2. Get main activity ──────────────────────────────────────────────────────
log("[2/4] 获取启动 Activity...")
activity = get_main_activity(pkg)
if not activity:
    log(f"ERROR: 无法获取「{pkg}」的启动 Activity")
    sys.exit(1)

log(f"  Activity: {activity}")

# ── 3. Launch app (brings to front if already running) ────────────────────────
log(f"[3/4] 启动「{APP_NAME}」...")
shell(f"am start -n {activity}")
sleep(2)

# Get task ID
task_id = get_task_id(pkg)
if task_id is None:
    log(f"ERROR: 启动后未找到「{pkg}」的任务 ID")
    sys.exit(1)

log(f"  Task ID: {task_id}")

# ── 4. Move to bottom split ───────────────────────────────────────────────────
log(f"[4/4] 将「{APP_NAME}」移到底部分屏...")
move_to_side_stage(task_id, position=1)
sleep(1)

log(f"✅ 「{APP_NAME}」已在底部分屏打开，当前应用保留在顶部")
