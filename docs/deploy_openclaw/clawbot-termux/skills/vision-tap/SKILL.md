---
name: vision-tap
description: "Use AI vision to locate and click UI elements by visual description when uiautomator CANNOT recognize the interface. Use for: WeChat main interface (聊天列表/朋友圈/发现页), apps using SurfaceView/TextureView rendering, any screen where dump-clickable returns empty or 0 nodes. Workflow: vision-tap tap <description> → AI takes screenshot → locates element visually → clicks coordinates. WeChat messaging: open WeChat → vision-tap search icon → type contact → vision-tap first result → vision-tap input box → type message → vision-tap send button."
metadata:
  {
    "openclaw":
      {
        "emoji": "🔍",
        "requires": { "bins": ["adb", "python3"] },
      },
  }
---

# Vision Tap — ClawBot

通过截图 + 视觉 AI 定位界面元素并点击，**专门解决 uiautomator 无法识别的界面**（如微信主界面）。

```
PYTHON=/data/data/com.termux/files/usr/bin/python3
SCRIPT=/data/data/com.termux/files/home/vision-tap.py
```

以下示例均使用 `$PYTHON $SCRIPT <cmd>` 形式，简写为 `vtap <cmd>`。

---

## ⚠️ 何时使用 vision-tap vs ui-control

| 场景 | 使用工具 |
|------|---------|
| 普通 App（设置、浏览器、小红书等） | ✅ **ui-control**（更快，无需 API 调用） |
| 微信主界面（聊天列表、发现页等） | ❌ ui-control 无效 → ✅ **vision-tap** |
| dump-clickable 返回"屏幕无可点击元素" | ✅ **vision-tap** |
| 需要精确读取文字内容 | ✅ ui-control dump |

**诊断方法：**
```bash
# 先用 ui-control 试一下
$PYTHON $UICTL dump-clickable
# 若输出"屏幕无可点击元素"或节点数 < 3，切换到 vision-tap
```

---

## 命令速查

| 命令 | 用途 |
|------|------|
| `tap <description>` | 视觉定位元素并点击 |
| `find <description>` | 视觉定位元素，返回坐标（不点击） |

---

## 微信操作指南（必读）

### 背景

微信主界面使用自研渲染引擎（SurfaceView），uiautomator dump **完全无法识别**任何元素（只返回一个空节点）。所有微信界面操作必须使用 vision-tap。

### 发消息完整流程

```bash
PYTHON=/data/data/com.termux/files/usr/bin/python3
VTAP=/data/data/com.termux/files/home/vision-tap.py
UICTL=/data/data/com.termux/files/home/ui-control.py
ADB=/data/data/com.termux/files/usr/bin/adb

# ── 步骤 1：确保微信已打开并在主界面 ──────────────────────────────
$ADB shell am start -n com.tencent.mm/.ui.LauncherUI
sleep 2

# ── 步骤 2：点击右上角搜索图标（Q）────────────────────────────────
$PYTHON $VTAP tap "右上角搜索图标，放大镜形状的Q"
sleep 1

# ── 步骤 3：在搜索框输入联系人名字 ───────────────────────────────
# 搜索框已自动聚焦（进入搜索页面时自动获焦）
$PYTHON $UICTL type "联系人名字"    # 搜索框是标准输入框，ui-control type 可用
sleep 1

# ── 步骤 4：点击搜索结果第一个联系人 ─────────────────────────────
$PYTHON $VTAP tap "搜索结果列表中第一个联系人"
sleep 1

# ── 步骤 5：点击底部消息输入框 ────────────────────────────────────
$PYTHON $VTAP tap "底部消息输入框"
sleep 0.5

# ── 步骤 6：输入消息内容 ─────────────────────────────────────────
$PYTHON $UICTL type "消息内容"
sleep 0.5

# ── 步骤 7：点击发送按钮 ─────────────────────────────────────────
$PYTHON $VTAP tap "右侧绿色发送按钮"
sleep 0.5
```

### 各步骤要点

| 步骤 | vision-tap 描述 | 说明 |
|------|----------------|------|
| 搜索图标 | `"右上角搜索图标，放大镜形状的Q"` | 位于标题栏右侧第一个图标 |
| 第一个联系人 | `"搜索结果列表中第一个联系人"` | 搜索结果列表最上方的一行 |
| 消息输入框 | `"底部消息输入框"` | 屏幕底部横向输入区域 |
| 发送按钮 | `"右侧绿色发送按钮"` | 输入框右侧的绿色方形按钮 |

### 注意事项

- 搜索框（步骤 3）和消息输入框（步骤 6）是**标准 Android EditText**，可用 `$PYTHON $UICTL type` 输入，不需要 vision-tap
- 每次 vision-tap 后等待 0.5～1 秒再操作
- 如果 vision-tap 点击后无反应，用 `$PYTHON $UICTL screenshot` 截图确认当前状态

---

## 通用使用示例

```bash
PYTHON=/data/data/com.termux/files/usr/bin/python3
VTAP=/data/data/com.termux/files/home/vision-tap.py

# 点击某个按钮
$PYTHON $VTAP tap "右下角红色确认按钮"
$PYTHON $VTAP tap "顶部导航栏的返回箭头"
$PYTHON $VTAP tap "屏幕中央的播放按钮"

# 只定位不点击（获取坐标用于调试）
$PYTHON $VTAP find "底部tab栏第二个图标"
```

---

## 工作原理

```
1. adb exec-out screencap  →  PNG 截图
2. PIL 压缩 50%            →  JPEG（减少 API 消耗）
3. DashScope/Gemini/SiliconFlow 视觉 API
   → prompt: 「找到界面中的元素：<description>，返回 x_pct/y_pct」
4. 百分比坐标 × 屏幕分辨率  →  实际像素坐标
5. adb shell input tap X Y  →  点击
```

**API 优先级：** DashScope qwen-vl-max → Gemini Flash → SiliconFlow Qwen2.5-VL-32B

---

## 部署

```bash
# 推送脚本到手机
adb push docs/deploy_openclaw/clawbot-termux/skills/vision-tap/vision-tap.py \
    /data/data/com.termux/files/home/vision-tap.py
adb shell chmod +x /data/data/com.termux/files/home/vision-tap.py
```

## Guidelines

- **优先 ui-control**：vision-tap 每次调用需要截图 + API 请求（约 3~8 秒），比 uiautomator 慢。只在 uiautomator 无效时才用。
- **描述要具体**：描述越精确，AI 定位越准。避免"那个按钮"，改用"右上角搜索图标"。
- **必须**将脚本完整输出原样展示给用户。
