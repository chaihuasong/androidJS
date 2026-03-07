#!/data/data/com.termux/files/usr/bin/python3
"""
UI Control — uiautomator 驱动的通用界面操作工具
Usage:
  ui-control.py dump                      # 输出所有元素
  ui-control.py dump-clickable            # 只输出可点击元素（更精简）
  ui-control.py find <regex>             # 正则搜索元素（比 dump 快，只看匹配项）
  ui-control.py has <regex>              # 检测页面是否含匹配元素（exit 0=有 / exit 1=无）
  ui-control.py tap <text>               # 按文字点击（子串匹配）
  ui-control.py tap-re <regex>           # 按正则点击（大小写不敏感，优先可点击元素）
  ui-control.py tap-id <resource-id-kw>  # 按 resource-id 关键词点击
  ui-control.py type <text>              # 输入文字（剪贴板粘贴，支持中文）
  ui-control.py swipe <up|down|left|right|scroll-up|scroll-down|scroll-to-top|scroll-to-bottom>
  ui-control.py key <back|home|recent|enter|delete>
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

# Termux 进程无 INJECT_EVENTS / READ_FRAME_BUFFER 权限，通过 adb TCP loopback 执行特权命令
# 前提：PC 执行 adb tcpip 5555，Termux 执行 adb connect 127.0.0.1:5555 && adb root
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

def reconnect_adb():
    """重连 adb TCP loopback，解决 adb 连接超时问题。"""
    log("  重连 adb loopback...")
    subprocess.run(
        f'/data/data/com.termux/files/usr/bin/adb disconnect 127.0.0.1:5555',
        shell=True, capture_output=True, timeout=5
    )
    time.sleep(0.5)
    subprocess.run(
        f'/data/data/com.termux/files/usr/bin/adb connect 127.0.0.1:5555',
        shell=True, capture_output=True, timeout=8
    )
    time.sleep(1)

# ── uiautomator dump ──────────────────────────────────────────────────────────

def dump_ui(retries=3):
    """
    执行 uiautomator dump，内置三阶段重试策略：
      第1次：10s 超时 + --compressed（快失败）
      第2次：18s 超时，不加 --compressed（兼容性更好）
      第3次：25s 超时，先唤屏 + 重连 adb，最后手段
    超时后强杀进程（pkill -9）并等待 1s 确保进程死透。
    """
    for attempt in range(1, retries + 1):
        # 强杀残留 uiautomator 进程，等待死透
        root_shell('pkill -9 -f uiautomator 2>/dev/null; true')
        time.sleep(1.0)

        # 渐进超时：10s → 18s → 25s
        timeout = [10, 18, 25][attempt - 1]

        # 第1次用 --compressed（更快），后续去掉（兼容性）
        flag = '--compressed' if attempt == 1 else ''

        log(f"  dump 第{attempt}次（超时{timeout}s {'--compressed' if flag else '无压缩'}）...")
        try:
            root_shell(f'{UIAUTOMATOR} dump {flag} {UI_XML} 2>/dev/null', timeout=timeout)
        except subprocess.TimeoutExpired:
            log(f"  第{attempt}次超时，强杀进程...")
            root_shell('pkill -9 -f uiautomator 2>/dev/null; true')
            time.sleep(1.0)
            if attempt == retries - 1:
                # 倒数第二次失败：重连 adb，给最后一次机会
                reconnect_adb()
            elif attempt == retries:
                log("ERROR: uiautomator dump 全部超时")
            continue

        time.sleep(0.3)
        try:
            xml_str = root_run(f'cat {UI_XML}', timeout=5)
        except subprocess.TimeoutExpired:
            log(f"  第{attempt}次读取 XML 超时，重连 adb...")
            reconnect_adb()
            continue

        if xml_str and '<hierarchy' in xml_str:
            try:
                return ET.fromstring(xml_str)
            except ET.ParseError as e:
                log(f"  XML 解析失败: {e}")

        if attempt < retries:
            log(f"  第{attempt}次 dump 内容无效，重试...")
            time.sleep(1.0)

    return None

def all_nodes(root):
    return list(root.iter('node')) if root else []

def node_center(node):
    nums = re.findall(r'\d+', node.get('bounds', ''))
    if len(nums) >= 4:
        return (int(nums[0]) + int(nums[2])) // 2, (int(nums[1]) + int(nums[3])) // 2
    return None

def node_label(node):
    return (node.get('text', '') or node.get('content-desc', '')).strip()

def compile_re(pattern):
    """编译正则，失败则打印错误并退出。"""
    try:
        return re.compile(pattern, re.IGNORECASE)
    except re.error as e:
        log(f"ERROR: 无效正则表达式 \"{pattern}\": {e}")
        sys.exit(1)

# ── 元素查找 ──────────────────────────────────────────────────────────────────

def find_node(root, text=None, res_id=None):
    """子串匹配（原始 tap / tap-id 使用）。"""
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

def find_nodes_re(root, rx):
    """正则匹配 text / content-desc / hint / resource-id，返回所有匹配节点列表。"""
    results = []
    for node in all_nodes(root):
        t   = node.get('text', '')
        d   = node.get('content-desc', '')
        h   = node.get('hint', '')
        rid = node.get('resource-id', '')
        if rx.search(t) or rx.search(d) or rx.search(h) or rx.search(rid):
            results.append(node)
    return results

def best_node(matches):
    """从匹配列表中优先选可点击元素，再选第一个。"""
    clickable = [n for n in matches if n.get('clickable') == 'true']
    return clickable[0] if clickable else (matches[0] if matches else None)

def tap_xy(x, y):
    root_shell(f'{INPUT} tap {x} {y}')
    time.sleep(0.8)

# ── dump_summary（tap 后自动调用）─────────────────────────────────────────────

def dump_summary(root):
    """
    tap 后输出屏幕摘要，区分可点击元素与普通文字，供 Agent 快速判断页面。
    格式：[tap后] 按钮: [文字A] [文字B] ... | 文字: "abc" "def" ...
    """
    nodes = all_nodes(root)
    buttons = []
    texts   = []
    for n in nodes:
        t = node_label(n)
        if not t:
            continue
        if n.get('clickable') == 'true':
            buttons.append(f'[{t}]')
        else:
            texts.append(f'"{t}"')

    parts = []
    if buttons: parts.append("按钮: " + " ".join(buttons[:10]))
    if texts:   parts.append("文字: " + " ".join(texts[:8]))
    log("  [tap后屏幕] " + (" | ".join(parts) if parts else "（无元素）"))

# ── 命令实现 ──────────────────────────────────────────────────────────────────

def _format_node(node):
    t   = node.get('text', '').strip()
    d   = node.get('content-desc', '').strip()
    rid = node.get('resource-id', '').strip()
    clk = node.get('clickable', 'false')
    c   = node_center(node)
    label = t or d
    parts = []
    if label: parts.append(f'text="{label}"')
    if rid:   parts.append(f'id="{rid}"')
    if c:     parts.append(f'pos=({c[0]},{c[1]})')
    if clk == 'true': parts.append('[可点击]')
    return '  ' + '  '.join(parts)


def cmd_dump():
    log("分析当前屏幕（所有元素）...")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    results = [_format_node(n) for n in all_nodes(root)
               if node_label(n) or n.get('resource-id', '')]
    if results:
        log(f"找到 {len(results)} 个元素:")
        for r in results: log(r)
    else:
        log("屏幕无可识别元素")


def cmd_dump_clickable():
    """只输出可点击元素，减少 ~70% 输出量，Agent 决策更快。"""
    log("分析当前屏幕（仅可点击元素）...")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    results = [_format_node(n) for n in all_nodes(root)
               if n.get('clickable') == 'true' or n.get('long-clickable') == 'true']
    if results:
        log(f"可点击元素 {len(results)} 个:")
        for r in results: log(r)
    else:
        log("屏幕无可点击元素")


def cmd_find(pattern):
    """
    正则搜索当前屏幕元素（text / content-desc / hint / resource-id），
    只输出匹配项。比 dump 快得多，适合定向查找。
    """
    rx = compile_re(pattern)
    log(f"搜索: \"{pattern}\"")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    matches = find_nodes_re(root, rx)
    if not matches:
        log(f"无匹配: \"{pattern}\"")
        return
    log(f"找到 {len(matches)} 个匹配:")
    for n in matches: log(_format_node(n))


def cmd_has(pattern):
    """
    快速检测页面是否含匹配 pattern 的元素。
    exit 0 = 存在（FOUND），exit 1 = 不存在（NOT FOUND）。
    适合 tap 后的页面验证，比再次 dump 更快。
    """
    rx = compile_re(pattern)
    log(f"检测: \"{pattern}\"")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    matches = find_nodes_re(root, rx)
    if matches:
        labels = [node_label(n) for n in matches[:3] if node_label(n)]
        log(f"FOUND ({len(matches)} 个): {', '.join(repr(l) for l in labels)}")
        sys.exit(0)
    else:
        log(f"NOT FOUND: \"{pattern}\"")
        sys.exit(1)


def cmd_tap(text):
    log(f"查找并点击: \"{text}\"")
    root = dump_ui()
    node = find_node(root, text=text)
    if node is None:
        log(f"ERROR: 未找到包含文字 \"{text}\" 的元素")
        log("提示: 先执行 dump-clickable 查看可点击元素，或用 find <regex> 搜索")
        sys.exit(1)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    log(f"  找到: text=\"{node.get('text','')}\" bounds={node.get('bounds','')}")
    tap_xy(*c)
    after = dump_ui()
    if after: dump_summary(after)
    log("OK")


def cmd_tap_re(pattern):
    """
    正则点击：在 text / content-desc / hint / resource-id 中正则搜索，
    优先选可点击元素，再按出现顺序选第一个。
    """
    rx = compile_re(pattern)
    log(f"正则点击: \"{pattern}\"")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    matches = find_nodes_re(root, rx)
    node = best_node(matches)
    if node is None:
        log(f"ERROR: 未找到匹配 \"{pattern}\" 的元素")
        log("提示: 先执行 find <pattern> 查看匹配情况")
        sys.exit(1)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    label = node_label(node) or node.get('resource-id', '')
    log(f"  点击: \"{label}\" bounds={node.get('bounds','')}"
        + (" [可点击]" if node.get('clickable') == 'true' else " [非clickable,坐标点击]"))
    tap_xy(*c)
    after = dump_ui()
    if after: dump_summary(after)
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
    after = dump_ui()
    if after: dump_summary(after)
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
    """
    方向语义（以手指移动方向为准）：
      up    = 手指向上，内容上移，显示下方内容（向下翻页）
      down  = 手指向下，内容下移，显示上方内容（向上翻页 / 回顶部）
      left  = 手指向左，切换到右侧内容
      right = 手指向右，切换到左侧内容

    语义别名（更直观）：
      scroll-down     = up   （看更多内容，内容往上走）
      scroll-up       = down （看上方内容，内容往下走）
      scroll-to-top   = 连续 down × 5
      scroll-to-bottom= 连续 up × 5
    """
    size_out = root_run(f"{WM} size")
    m = re.search(r'(\d+)x(\d+)', size_out)
    if m:
        w, h = int(m.group(1)), int(m.group(2))
    else:
        w, h = 1080, 2400
    cx = w // 2

    # 语义别名展开
    aliases = {
        'scroll-down':      ['up'],
        'scroll-up':        ['down'],
        'scroll-to-top':    ['down'] * 5,
        'scroll-to-bottom': ['up'] * 5,
    }
    if direction in aliases:
        steps = aliases[direction]
        log(f"滑动: {direction} → {steps}")
        for step in steps:
            _do_swipe(step, cx, w, h)
            time.sleep(0.5)
        log("OK")
        return

    valid = ['up', 'down', 'left', 'right']
    if direction not in valid:
        log(f"ERROR: 方向必须是 {'/'.join(valid)} 或别名 {'/'.join(aliases.keys())}，got \"{direction}\"")
        sys.exit(1)
    log(f"滑动: {direction}")
    _do_swipe(direction, cx, w, h)
    time.sleep(0.6)
    log("OK")


def _do_swipe(direction, cx, w, h):
    swipes = {
        'up':    (cx, int(h*0.7), cx, int(h*0.3), 400),
        'down':  (cx, int(h*0.3), cx, int(h*0.7), 400),
        'left':  (int(w*0.8), h//2, int(w*0.2), h//2, 300),
        'right': (int(w*0.2), h//2, int(w*0.8), h//2, 300),
    }
    x1, y1, x2, y2, dur = swipes[direction]
    root_shell(f'{INPUT} swipe {x1} {y1} {x2} {y2} {dur}')


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
    CLOUD     = "root@114.55.130.197"
    PHOTO_DIR = "/var/www/clawbot-photos"
    BASE_URL  = "http://114.55.130.197:28790/photos"
    tmpdir    = os.environ.get('TMPDIR', '/data/data/com.termux/files/usr/tmp')
    name      = f"ui-{datetime.datetime.now().strftime('%Y%m%d-%H%M%S')}.png"
    local     = f"{tmpdir}/{name}"

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
elif cmd == 'dump-clickable':
    cmd_dump_clickable()
elif cmd == 'find' and len(sys.argv) >= 3:
    cmd_find(sys.argv[2])
elif cmd == 'has' and len(sys.argv) >= 3:
    cmd_has(sys.argv[2])
elif cmd == 'tap' and len(sys.argv) >= 3:
    cmd_tap(sys.argv[2])
elif cmd == 'tap-re' and len(sys.argv) >= 3:
    cmd_tap_re(sys.argv[2])
elif cmd == 'tap-id' and len(sys.argv) >= 3:
    cmd_tap_id(sys.argv[2])
elif cmd == 'type' and len(sys.argv) >= 3:
    cmd_type(sys.argv[2])
elif cmd == 'swipe' and len(sys.argv) >= 3:
    cmd_swipe(sys.argv[2])
elif cmd in ('scroll-up', 'scroll-down', 'scroll-to-top', 'scroll-to-bottom'):
    cmd_swipe(cmd)
elif cmd == 'key' and len(sys.argv) >= 3:
    cmd_key(sys.argv[2])
elif cmd == 'screenshot':
    cmd_screenshot()
else:
    print(__doc__)
    sys.exit(1)
