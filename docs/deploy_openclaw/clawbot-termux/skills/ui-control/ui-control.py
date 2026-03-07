#!/data/data/com.termux/files/usr/bin/python3
"""
UI Control — uiautomator 驱动的通用界面操作工具
Usage:
  ui-control.py dump
  ui-control.py tap <text>
  ui-control.py tap-id <resource-id-keyword>
  ui-control.py type <text>
  ui-control.py swipe <up|down|left|right>
  ui-control.py key <back|home|recent|enter>
  ui-control.py screenshot
"""
import subprocess, sys, re, time, os
import xml.etree.ElementTree as ET

# 系统命令全路径
UIAUTOMATOR = '/system/bin/uiautomator'
INPUT       = '/system/bin/input'
SCREENCAP   = '/system/bin/screencap'
AM          = '/system/bin/am'
WM          = '/system/bin/wm'
DUMPSYS     = '/system/bin/dumpsys'

# screencap / uiautomator / input 需要 INJECT_EVENTS / READ_FRAME_BUFFER 权限
# Termux 进程 (uid 10231) 无此权限，也没有 su 二进制
# 方案：通过 adb TCP loopback 连接本机 root adbd 执行特权命令
# 前提：PC 执行 adb tcpip 5555，Termux 执行 adb connect 127.0.0.1:5555 && adb root
# Termux 里 adb 的完整路径（openclaw 执行时 PATH 不含 Termux bin 目录）
ADB = '/data/data/com.termux/files/usr/bin/adb -H 127.0.0.1 -P 5555'

def root_run(cmd, timeout=30):
    """通过 adb loopback 以 root 执行命令，返回 stdout。"""
    return subprocess.run(
        f'{ADB} shell {cmd}', shell=True,
        capture_output=True, text=True, timeout=timeout
    ).stdout.strip()

def root_shell(cmd, timeout=30):
    """通过 adb loopback 以 root 执行命令，忽略输出。"""
    subprocess.run(f'{ADB} shell {cmd}', shell=True,
                   capture_output=True, timeout=timeout)

UI_XML = "/sdcard/ui_dump.xml"

def run(cmd, timeout=15):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()

def shell(cmd, timeout=15):
    subprocess.run(cmd, shell=True, capture_output=True, timeout=timeout)

def log(msg): print(msg, flush=True)

# ── uiautomator dump ──────────────────────────────────────────────────────────

def dump_ui(retries=3):
    # uiautomator 需要写 /data/local/tmp/，Termux 无权限，必须走 adb loopback
    for attempt in range(1, retries + 1):
        root_shell('pkill -f uiautomator 2>/dev/null; true')
        time.sleep(0.5)
        try:
            root_shell(f'{UIAUTOMATOR} dump --compressed {UI_XML} 2>/dev/null', timeout=20)
        except subprocess.TimeoutExpired:
            if attempt < retries:
                log(f"  dump 第{attempt}次超时，重试...")
                time.sleep(1)
            continue
        time.sleep(0.3)
        xml_str = root_run(f'cat {UI_XML}', timeout=5)
        if xml_str and '<hierarchy' in xml_str:
            try:
                return ET.fromstring(xml_str)
            except ET.ParseError:
                pass
        if attempt < retries:
            log(f"  dump 第{attempt}次失败，重试...")
            time.sleep(1)
    return None

def all_nodes(root):
    return list(root.iter('node')) if root else []

def node_center(node):
    nums = re.findall(r'\d+', node.get('bounds', ''))
    if len(nums) >= 4:
        return (int(nums[0]) + int(nums[2])) // 2, (int(nums[1]) + int(nums[3])) // 2
    return None

def find_node(root, text=None, res_id=None):
    for node in all_nodes(root):
        if text:
            t = node.get('text', '')
            d = node.get('content-desc', '')
            h = node.get('hint', '')
            if text in t or text in d or text in h:
                return node
        if res_id:
            if res_id in node.get('resource-id', ''):
                return node
    return None

def tap_xy(x, y):
    root_shell(f'{INPUT} tap {x} {y}')
    time.sleep(0.8)

# ── 命令实现 ──────────────────────────────────────────────────────────────────

def cmd_dump():
    log("分析当前屏幕...")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)

    nodes = all_nodes(root)
    results = []
    for node in nodes:
        t    = node.get('text', '').strip()
        d    = node.get('content-desc', '').strip()
        rid  = node.get('resource-id', '').strip()
        clk  = node.get('clickable', 'false')
        c    = node_center(node)
        label = t or d
        if not label and not rid:
            continue
        parts = []
        if label: parts.append(f'text="{label}"')
        if rid:   parts.append(f'id="{rid}"')
        if c:     parts.append(f'pos=({c[0]},{c[1]})')
        if clk == 'true': parts.append('[可点击]')
        results.append('  ' + '  '.join(parts))

    if results:
        log(f"找到 {len(results)} 个元素:")
        for r in results:
            log(r)
    else:
        log("屏幕无可识别元素")


def cmd_tap(text):
    log(f"查找并点击: \"{text}\"")
    root = dump_ui()
    node = find_node(root, text=text)
    if node is None:
        log(f"ERROR: 未找到包含文字 \"{text}\" 的元素")
        log("提示: 先执行 dump 查看当前屏幕所有元素")
        sys.exit(1)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    log(f"  找到: text=\"{node.get('text','')}\" bounds={node.get('bounds','')}")
    tap_xy(*c)
    log("OK")


def cmd_tap_id(res_id):
    log(f"查找并点击 resource-id 含: \"{res_id}\"")
    root = dump_ui()
    node = find_node(root, res_id=res_id)
    if node is None:
        log(f"ERROR: 未找到 resource-id 包含 \"{res_id}\" 的元素")
        log("提示: 先执行 dump 查看当前屏幕所有元素")
        sys.exit(1)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    log(f"  找到: id=\"{node.get('resource-id','')}\" bounds={node.get('bounds','')}")
    tap_xy(*c)
    log("OK")


def cmd_type(text):
    log(f"输入文字: \"{text}\"")
    subprocess.run(
        ["termux-clipboard-set"],
        input=text.encode('utf-8'),
        capture_output=True, timeout=5
    )
    time.sleep(0.4)
    shell("input keyevent 279")   # KEYCODE_PASTE
    time.sleep(0.4)
    log("OK")


def cmd_swipe(direction):
    size_out = root_run(f"{WM} size")
    m = re.search(r'(\d+)x(\d+)', size_out)
    if m:
        w, h = int(m.group(1)), int(m.group(2))
    else:
        w, h = 1080, 2400
    cx = w // 2
    swipes = {
        'up':    (cx, int(h*0.7), cx, int(h*0.3), 400),
        'down':  (cx, int(h*0.3), cx, int(h*0.7), 400),
        'left':  (int(w*0.8), h//2, int(w*0.2), h//2, 300),
        'right': (int(w*0.2), h//2, int(w*0.8), h//2, 300),
    }
    if direction not in swipes:
        log(f"ERROR: 方向必须是 up/down/left/right，got \"{direction}\"")
        sys.exit(1)
    x1, y1, x2, y2, dur = swipes[direction]
    log(f"滑动: {direction}")
    root_shell(f'{INPUT} swipe {x1} {y1} {x2} {y2} {dur}')
    time.sleep(0.6)
    log("OK")


def cmd_key(key):
    keycodes = {
        'back':   '4',
        'home':   '3',
        'recent': '187',
        'enter':  '66',
        'delete': '67',
    }
    if key not in keycodes:
        log(f"ERROR: 支持的按键: {', '.join(keycodes.keys())}")
        sys.exit(1)
    log(f"按键: {key}")
    root_shell(f'{INPUT} keyevent {keycodes[key]}')
    time.sleep(0.4)
    log("OK")


def cmd_screenshot():
    import datetime
    CLOUD    = "root@114.55.130.197"
    PHOTO_DIR = "/var/www/clawbot-photos"
    BASE_URL  = "http://114.55.130.197:28790/photos"
    tmpdir   = os.environ.get('TMPDIR', '/data/data/com.termux/files/usr/tmp')
    name     = f"ui-{datetime.datetime.now().strftime('%Y%m%d-%H%M%S')}.png"
    local    = f"{tmpdir}/{name}"

    # adb exec-out 把 screencap 输出直接写到本地文件，无需 Termux 执行 Android 二进制
    with open(local, 'wb') as f:
        r = subprocess.run(f'{ADB} exec-out screencap -p',
                           shell=True, stdout=f, stderr=subprocess.PIPE, timeout=15)
    if r.returncode != 0 or os.path.getsize(local) < 1000:
        log(f"ERROR: 截图失败: {r.stderr.decode()}")
        sys.exit(1)

    r = subprocess.run(
        f"scp -o StrictHostKeyChecking=no {local} {CLOUD}:{PHOTO_DIR}/{name}",
        shell=True, capture_output=True, timeout=20
    )
    if r.returncode != 0:
        # scp 失败时保留本地文件
        log(f"截图已保存本地: {local}（上传失败，可手动查看）")
    else:
        subprocess.run(f"ssh -o StrictHostKeyChecking=no {CLOUD} 'chmod 644 {PHOTO_DIR}/{name}'",
                       shell=True, capture_output=True, timeout=10)
        os.remove(local)
        log(f"![screenshot]({BASE_URL}/{name})")


# ── 入口 ──────────────────────────────────────────────────────────────────────

if len(sys.argv) < 2:
    print(__doc__)
    sys.exit(0)

cmd = sys.argv[1]

if cmd == 'dump':
    cmd_dump()
elif cmd == 'tap' and len(sys.argv) >= 3:
    cmd_tap(sys.argv[2])
elif cmd == 'tap-id' and len(sys.argv) >= 3:
    cmd_tap_id(sys.argv[2])
elif cmd == 'type' and len(sys.argv) >= 3:
    cmd_type(sys.argv[2])
elif cmd == 'swipe' and len(sys.argv) >= 3:
    cmd_swipe(sys.argv[2])
elif cmd == 'key' and len(sys.argv) >= 3:
    cmd_key(sys.argv[2])
elif cmd == 'screenshot':
    cmd_screenshot()
else:
    print(__doc__)
    sys.exit(1)
