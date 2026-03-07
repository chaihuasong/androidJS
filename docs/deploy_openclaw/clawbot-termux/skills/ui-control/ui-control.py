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
import subprocess, sys, re, time, os, fcntl
import xml.etree.ElementTree as ET

# 系统命令全路径
UIAUTOMATOR = '/system/bin/uiautomator'
INPUT       = '/system/bin/input'
SCREENCAP   = '/system/bin/screencap'
AM          = '/system/bin/am'
WM          = '/system/bin/wm'
DUMPSYS     = '/system/bin/dumpsys'

# 特权命令执行策略：
#   优先 su（Magisk root，直接在 Termux 进程内执行，无网络开销）
#   失败则回退 adb TCP loopback（前提：adb tcpip 5555 + adb root 已配置）
ADB = '/data/data/com.termux/files/usr/bin/adb'

_su_cmd = None   # None=未检测, ''=不可用, 否则为可用的 su 命令
_SU_CACHE = '/data/data/com.termux/files/usr/tmp/.uictl_su'  # 跨进程缓存，避免每次重新探测

def _check_su():
    """
    探测可用的 su 命令。结果持久化到磁盘缓存，后续调用直接读缓存（< 100ms）。
    首次探测：试 tsu/su/路径，找到即写缓存并返回。
    """
    global _su_cmd
    if _su_cmd is not None:
        return _su_cmd or None

    # ── 读磁盘缓存（跨进程复用，避免每次启动重新探测）──
    try:
        with open(_SU_CACHE) as f:
            cached = f.read().strip()
        if cached == 'none':
            _su_cmd = ''
            return None
        # 缓存命中，快速验证一下仍然有效
        r = subprocess.run([cached, '-c', 'id'], capture_output=True, timeout=3)
        if r.returncode == 0 and b'uid=0' in r.stdout:
            _su_cmd = cached
            return _su_cmd
    except Exception:
        pass  # 缓存不存在或已失效，继续全量探测

    # ── 全量探测（仅首次或缓存失效时执行）──
    for candidate in ['tsu', 'su', '/system/bin/su', '/sbin/su']:
        which = subprocess.run(f'which {candidate} 2>/dev/null || command -v {candidate} 2>/dev/null',
                               shell=True, capture_output=True, timeout=2)
        if not which.stdout.strip() and not candidate.startswith('/'):
            continue
        try:
            r = subprocess.run([candidate, '-c', 'id'], capture_output=True, timeout=3)
            if r.returncode == 0 and b'uid=0' in r.stdout:
                _su_cmd = candidate
                try:
                    with open(_SU_CACHE, 'w') as f:
                        f.write(candidate)
                except Exception:
                    pass
                return _su_cmd
        except (FileNotFoundError, subprocess.TimeoutExpired):
            continue

    _su_cmd = ''
    try:
        with open(_SU_CACHE, 'w') as f:
            f.write('none')
    except Exception:
        pass
    return None

def root_run(cmd, timeout=8):
    """以 root 执行命令，返回 stdout。优先 su，回退 adb loopback。"""
    su = _check_su()
    if su:
        r = subprocess.run([su, '-c', cmd],
                           capture_output=True, text=True, timeout=timeout)
        return r.stdout.strip()
    return subprocess.run(
        f'{ADB} shell {cmd}', shell=True,
        capture_output=True, text=True, timeout=timeout
    ).stdout.strip()

def root_shell(cmd, timeout=8):
    """以 root 执行命令，忽略输出。优先 su，回退 adb loopback。"""
    su = _check_su()
    if su:
        subprocess.run([su, '-c', cmd], capture_output=True, timeout=timeout)
    else:
        subprocess.run(f'{ADB} shell {cmd}', shell=True,
                       capture_output=True, timeout=timeout)

def run(cmd, timeout=15):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()

def shell(cmd, timeout=15):
    subprocess.run(cmd, shell=True, capture_output=True, timeout=timeout)

def log(msg): print(msg, flush=True)

def reconnect_adb():
    """adb 超时时重置：杀残留进程，emulator-5554 本地 transport 自动恢复。"""
    log("  重置 adb...")
    subprocess.run('pkill -9 -f "adb.*uiautomator" 2>/dev/null; true',
                   shell=True, timeout=3)
    time.sleep(1.0)

# ── uiautomator dump ──────────────────────────────────────────────────────────

_DUMP_LOCK = '/data/data/com.termux/files/usr/tmp/.uictl_dump.lock'

def dump_ui(retries=3):
    """
    执行 uiautomator dump。
    用 flock 保证同一时刻只有一个进程在 dump，避免并发调用时互相 pkill 导致卡死。
    """
    with open(_DUMP_LOCK, 'w') as _lf:
        fcntl.flock(_lf, fcntl.LOCK_EX)   # 阻塞直到拿到独占锁
        try:
            return _dump_ui_inner(retries)
        finally:
            fcntl.flock(_lf, fcntl.LOCK_UN)

def _dump_ui_inner(retries=3):
    for attempt in range(1, retries + 1):
        time.sleep(0.2)

        timeout = [10, 18, 25][attempt - 1]
        flag = '--compressed' if attempt == 1 else ''

        log(f"  dump 第{attempt}次（超时{timeout}s）...")
        try:
            # 写到 /data/local/tmp（tmpfs，比 /sdcard 快），然后 cat+sed 一次读回
            # sed 剥离无用属性（减少 ~50% 体积），保留: text content-desc hint
            #   resource-id clickable long-clickable bounds
            _UI_TMP = '/data/data/com.termux/files/usr/tmp/ui_dump.xml'
            _strip = (
                "sed -e 's/ index=\"[^\"]*\"//g'"
                " -e 's/ package=\"[^\"]*\"//g'"
                " -e 's/ class=\"[^\"]*\"//g'"
                " -e 's/ checkable=\"[^\"]*\"//g'"
                " -e 's/ checked=\"[^\"]*\"//g'"
                " -e 's/ enabled=\"[^\"]*\"//g'"
                " -e 's/ focusable=\"[^\"]*\"//g'"
                " -e 's/ focused=\"[^\"]*\"//g'"
                " -e 's/ scrollable=\"[^\"]*\"//g'"
                " -e 's/ password=\"[^\"]*\"//g'"
                " -e 's/ selected=\"[^\"]*\"//g'"
                " -e 's/ NAF=\"[^\"]*\"//g'"
            )
            xml_str = root_run(
                f'{UIAUTOMATOR} dump {flag} {_UI_TMP} 2>/dev/null && cat {_UI_TMP} | {_strip}',
                timeout=timeout
            )
        except subprocess.TimeoutExpired:
            log(f"  第{attempt}次超时...")
            time.sleep(1.0)
            if attempt == retries - 1:
                reconnect_adb()
            elif attempt == retries:
                log("ERROR: uiautomator dump 全部超时")
            continue

        # uiautomator 有时会在 XML 前输出一行 "UI hierchary dumped to: ..."，去掉
        if xml_str:
            for marker in ('<?xml', '<hierarchy'):
                idx = xml_str.find(marker)
                if idx > 0:
                    xml_str = xml_str[idx:]
                    break

        if xml_str and '<hierarchy' in xml_str:
            try:
                return ET.fromstring(xml_str)
            except ET.ParseError as e:
                log(f"  XML 解析失败: {e}")

        if attempt < retries:
            log(f"  第{attempt}次 dump 内容无效，重试...")
            time.sleep(0.8)

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

def build_parent_map(root):
    """构建 子节点→父节点 映射，用于向上查找可点击祖先。"""
    parent_map = {}
    for parent in root.iter('node'):
        for child in parent:
            parent_map[child] = parent
    return parent_map

def resolve_clickable(node, parent_map):
    """
    如果 node 本身不可点击，向上找最近的可点击祖先。
    解决"文字在 TextView，真正可点击的是父 View"的问题（搜索按钮、列表项等）。
    """
    if node is None:
        return None
    if node.get('clickable') == 'true':
        return node
    cur = parent_map.get(node)
    while cur is not None:
        if cur.get('clickable') == 'true':
            return cur
        cur = parent_map.get(cur)
    return node  # 没找到可点击祖先，退回原节点

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
    root_shell(f'{INPUT} tap {x} {y}', timeout=5)
    time.sleep(0.8)

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
    """
    子串点击：收集所有含该文字的节点，优先选可点击节点（同 tap-re 策略），
    再用 resolve_clickable 向上找可点击祖先。
    避免命中 hint 文字或不可点击的 TextView 而漏掉真正的按钮。
    """
    log(f"查找并点击: \"{text}\"")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    parent_map = build_parent_map(root)
    matches = []
    for node in all_nodes(root):
        t = node.get('text', '')
        d = node.get('content-desc', '')
        h = node.get('hint', '')
        if text in t or text in d or text in h:
            matches.append(node)
    if not matches:
        log(f"ERROR: 未找到包含文字 \"{text}\" 的元素")
        log("提示: 先执行 dump-clickable 查看可点击元素，或用 find <regex> 搜索")
        sys.exit(1)
    node = best_node(matches)
    node = resolve_clickable(node, parent_map)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    label = node_label(node) or node.get('resource-id', '')
    log(f"  点击: \"{label}\" bounds={node.get('bounds','')}"
        + (" [可点击]" if node.get('clickable') == 'true' else " [坐标点击]"))
    tap_xy(*c)
    log("OK")


def cmd_tap_re(pattern):
    """
    正则点击：在 text / content-desc / hint / resource-id 中正则搜索，
    优先选可点击元素；若匹配节点不可点击，向上查找可点击祖先。
    """
    rx = compile_re(pattern)
    log(f"正则点击: \"{pattern}\"")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    parent_map = build_parent_map(root)
    matches = find_nodes_re(root, rx)
    node = best_node(matches)
    if node is None:
        log(f"ERROR: 未找到匹配 \"{pattern}\" 的元素")
        log("提示: 先执行 find <pattern> 查看匹配情况")
        sys.exit(1)
    node = resolve_clickable(node, parent_map)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    label = node_label(node) or node.get('resource-id', '')
    log(f"  点击: \"{label}\" bounds={node.get('bounds','')}"
        + (" [可点击]" if node.get('clickable') == 'true' else " [坐标点击]"))
    tap_xy(*c)
    log("OK")


def cmd_tap_id(res_id):
    log(f"查找并点击 resource-id 含: \"{res_id}\"")
    root = dump_ui()
    if root is None:
        log("ERROR: uiautomator dump 失败")
        sys.exit(1)
    parent_map = build_parent_map(root)
    node = find_node(root, res_id=res_id)
    if node is None:
        log(f"ERROR: 未找到 resource-id 包含 \"{res_id}\" 的元素")
        log("提示: 先执行 dump 查看当前屏幕所有元素")
        sys.exit(1)
    node = resolve_clickable(node, parent_map)
    c = node_center(node)
    if not c:
        log("ERROR: 无法获取元素坐标")
        sys.exit(1)
    log(f"  点击: id=\"{node.get('resource-id','')}\" bounds={node.get('bounds','')}"
        + (" [可点击]" if node.get('clickable') == 'true' else " [坐标点击]"))
    tap_xy(*c)
    log("OK")


_ADB_IME = 'com.android.adbkeyboard/.AdbIME'
_ADB_BIN = '/data/data/com.termux/files/usr/bin/adb'

def _adb(cmd, timeout=8):
    """通过 adb shell 执行命令（走 emulator-5554 本地 transport）。"""
    return subprocess.run(f'{_ADB_BIN} shell {cmd}',
                          shell=True, capture_output=True, text=True, timeout=timeout).stdout.strip()

def _get_current_ime():
    """获取当前默认输入法 ID。"""
    return _adb('settings get secure default_input_method', timeout=5).strip()

def cmd_type(text):
    """
    输入文字。
    - 纯 ASCII：adb shell input text 直接发，无需切换 IME。
    - 含中文/特殊字符：记录当前 IME → 切 ADBKeyBoard → broadcast 发文字 → 切回原 IME。
      ADBKeyBoard broadcast 必须走 adb shell（不能用 su），否则无法触发输入法接收。
    """
    log(f"输入文字: \"{text}\"")
    is_ascii = all(ord(c) < 128 for c in text)

    if is_ascii:
        safe = text.replace('\\', '\\\\').replace("'", "\\'")
        _adb(f"input text '{safe}'", timeout=8)
        log("OK")
        return

    # 含中文：切 ADBKeyBoard，完成后切回
    prev_ime = _get_current_ime()
    log(f"  切换输入法: {prev_ime or '未知'} → ADBKeyBoard")
    _adb(f'ime set {_ADB_IME}', timeout=5)
    time.sleep(0.3)

    escaped = text.replace('\\', '\\\\').replace('"', '\\"')
    _adb(f'am broadcast -a ADB_INPUT_TEXT --es msg "{escaped}"', timeout=5)
    time.sleep(0.3)

    if prev_ime and prev_ime != _ADB_IME:
        _adb(f'ime set {prev_ime}', timeout=5)
        log(f"  恢复输入法: {prev_ime}")

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
    size_out = root_run(f"{WM} size", timeout=5)
    m = re.search(r'(\d+)x(\d+)', size_out)
    w, h = (int(m.group(1)), int(m.group(2))) if m else (1080, 2400)
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
    root_shell(f'{INPUT} swipe {x1} {y1} {x2} {y2} {dur}', timeout=5)


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
    root_shell(f'{INPUT} keyevent {keycodes[key]}', timeout=5)
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

    remote_png = '/sdcard/ui_screenshot_tmp.png'
    name       = name.replace('.png', '.jpg')
    local      = local.replace('.png', '.jpg')

    # 1. screencap — 诊断日志 + su/adb 明确分路
    su_avail = _check_su()
    log(f"  截图（su可用={su_avail}）...")
    taken = False

    # 路径A：su 直接执行（无网络，< 2s）
    if su_avail:
        try:
            r = subprocess.run(['su', '-c', f'{SCREENCAP} -p {remote_png}'],
                               capture_output=True, timeout=8)
            if r.returncode == 0:
                log("  su 截图成功")
                taken = True
            else:
                log(f"  su 截图失败(rc={r.returncode} err={r.stderr.decode().strip()[:80]})，回退 adb...")
        except subprocess.TimeoutExpired:
            log("  su 截图超时，回退 adb...")
            subprocess.run('pkill -9 -f "su.*screencap" 2>/dev/null; true', shell=True, timeout=3)

    # 路径B：adb loopback（fallback）
    # 每次重试前都做完整 reconnect（杀残留进程 + disconnect + connect）
    if not taken:
        for attempt in range(1, 4):
            reconnect_adb()
            log(f"  adb 截图第{attempt}次...")
            try:
                subprocess.run(f'{ADB} shell {SCREENCAP} -p {remote_png}',
                               shell=True, capture_output=True, timeout=12)
                taken = True
                break
            except subprocess.TimeoutExpired:
                # shell 可能未退出但文件已写完 — 先检查
                if os.path.exists(remote_png) and os.path.getsize(remote_png) > 1000:
                    log("  shell 未退出但文件已写完，继续")
                    taken = True
                    break
                log(f"  第{attempt}次无响应")
        if not taken:
            log("ERROR: screencap 全部超时")
            sys.exit(1)

    # 2. 直接读 /sdcard 文件（Termux 有 external storage 权限），压缩为 JPEG，无需 adb pull
    log("  压缩截图...")
    try:
        from PIL import Image
        img = Image.open(remote_png).convert('RGB')
        img.save(local, 'JPEG', quality=70)
        log("  压缩成功（JPEG quality=70）")
    except Exception as e:
        log(f"ERROR: 截图读取/压缩失败: {e}")
        sys.exit(1)
    finally:
        shell(f'rm -f {remote_png}')

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
