---
name: ui-control
description: "Control the phone's UI by analyzing the screen and clicking/typing/scrolling. Use when asked to: click a button, tap an element, type text into an app, scroll the screen, find and interact with any UI element, analyze what's on screen, or perform any touch interaction on the phone. This skill uses uiautomator to inspect the current screen layout and execute precise interactions."
metadata:
  {
    "openclaw":
      {
        "emoji": "👆",
        "requires": { "bins": ["uiautomator", "input", "screencap"] },
      },
  }
---

# UI 控制 — ClawBot

通过 uiautomator 分析手机当前屏幕，自动找到目标元素并执行点击、输入、滑动等操作。

```
PYTHON=/data/data/com.termux/files/usr/bin/python3
SCRIPT=/data/data/com.termux/files/home/ui-control.py
```

以下示例均使用 `$PYTHON $SCRIPT <cmd>` 形式，简写为 `ui <cmd>`。

---

## 命令速查

| 命令 | 用途 | 速度 |
|------|------|------|
| `dump-clickable` | 只列可点击元素（比 dump 少 ~70%）| 慢（dump） |
| `dump` | 列出所有元素 | 慢（dump） |
| `find <regex>` | 正则搜索元素，只显示匹配项 | 慢（dump） |
| `has <regex>` | 检测页面是否含匹配元素（exit 0/1）| 慢（dump） |
| `tap-re <regex>` | 正则点击（大小写不敏感，自动优先可点击） | 慢（dump+tap）|
| `tap <text>` | 子串精确点击 | 慢（dump+tap）|
| `tap-id <id-kw>` | 按 resource-id 关键词点击 | 慢（dump+tap）|
| `type <text>` | 输入文字（剪贴板，支持中文）| 快 |
| `swipe <方向>` | 滑动（up/down/left/right）| 快 |
| `key <按键>` | 系统键（back/home/recent/enter/delete）| 快 |
| `screenshot` | 截屏上传云端 | 中 |

---

## 推荐工作流（速度优先）

### 步骤 1：了解当前页面 → 用 `dump-clickable`（不用 `dump`）

```bash
$PYTHON $SCRIPT dump-clickable
```

输出只含可点击元素，token 消耗约为 `dump` 的 1/3，适合大多数场景。
只有需要读取非可点击文字（如标题、提示语）时才用 `dump`。

### 步骤 2：点击 → 优先用 `tap-re`（比 `tap` 更容错）

```bash
# 正则点击，大小写不敏感，支持部分匹配
$PYTHON $SCRIPT tap-re "发布|发送|提交"

# 匹配包含"设置"的任意元素
$PYTHON $SCRIPT tap-re "设置"

# 匹配数字结尾的按钮（如"确认123"）
$PYTHON $SCRIPT tap-re "确认\d*"
```

`tap-re` 会自动从匹配项中**优先选可点击元素**，再按顺序取第一个。

### 步骤 3：验证页面 → 用 `has`（最快，不需要读全量输出）

```bash
# 进入发布页后，检查是否有"发布"按钮
$PYTHON $SCRIPT has "发布"   # exit 0 = 在正确页面，exit 1 = 页面有误

# 检查是否出现错误提示
$PYTHON $SCRIPT has "失败|错误|重试"
```

**`has` 的退出码即结论**，不需要解析输出文字，适合链式脚本和条件判断。

### 步骤 4：定向搜索 → 用 `find`

```bash
# 列出所有含"评论"的元素（看看有哪些入口）
$PYTHON $SCRIPT find "评论"

# 搜索输入框（常见 resource-id 规律）
$PYTHON $SCRIPT find "edit|input|search"
```

---

## 各命令详细用法

### 查看屏幕内容

```bash
# 推荐：只看可点击元素（更精简）
$PYTHON $SCRIPT dump-clickable

# 完整 dump（含所有文字、标题等）
$PYTHON $SCRIPT dump
```

### 点击元素

```bash
# 正则点击（推荐）：大小写不敏感，支持 | 多选
$PYTHON $SCRIPT tap-re "发布|Post"
$PYTHON $SCRIPT tap-re "^下一步$"         # 精确匹配"下一步"
$PYTHON $SCRIPT tap-re "关闭|×|✕"

# 子串点击（精确文字）
$PYTHON $SCRIPT tap "发布"

# resource-id 关键词点击
$PYTHON $SCRIPT tap-id "btn_publish"
```

### 搜索 + 验证

```bash
# 搜索：列出匹配元素详情
$PYTHON $SCRIPT find "搜索|search"

# 验证：判断是否在正确页面
$PYTHON $SCRIPT has "首页"      # 确认在首页
$PYTHON $SCRIPT has "登录"      # 检测是否需要登录
```

### 输入文字

```bash
# 先 tap-re 聚焦输入框，再 type 输入（中文必须用 type）
$PYTHON $SCRIPT tap-re "搜索|请输入"
$PYTHON $SCRIPT type "要输入的内容"
```

### 滑动

**注意方向语义（以手指移动方向为准，与内容滚动方向相反）：**

| 命令 | 手指方向 | 内容移动 | 实际效果 |
|------|----------|----------|----------|
| `swipe up` / `scroll-down` | 向上 | 内容上移 | 看更多下方内容（向下翻页）|
| `swipe down` / `scroll-up` | 向下 | 内容下移 | 看上方内容（向上翻页）|
| `scroll-to-top` | 向下 ×5 | 内容下移 | **回到页面顶部** |
| `scroll-to-bottom` | 向上 ×5 | 内容上移 | **滚到页面底部** |

```bash
# 推荐用语义别名，避免方向混淆
$PYTHON $SCRIPT swipe scroll-to-top      # 回到顶部
$PYTHON $SCRIPT swipe scroll-to-bottom   # 滚到底部
$PYTHON $SCRIPT swipe scroll-down        # 向下翻页（看更多）
$PYTHON $SCRIPT swipe scroll-up          # 向上翻页（回看）

# 或直接用原始方向（以手指移动方向为准）
$PYTHON $SCRIPT swipe up      # 手指向上 = 内容上移 = 看下方内容
$PYTHON $SCRIPT swipe down    # 手指向下 = 内容下移 = 看上方内容
$PYTHON $SCRIPT swipe left
$PYTHON $SCRIPT swipe right
```

### 系统键

```bash
$PYTHON $SCRIPT key back      # 返回
$PYTHON $SCRIPT key home      # Home
$PYTHON $SCRIPT key recent    # 最近任务
$PYTHON $SCRIPT key enter     # 回车
$PYTHON $SCRIPT key delete    # 删除
```

### 截屏

```bash
$PYTHON $SCRIPT screenshot
```

---

## Guidelines

### 操作前
- 优先用 **`dump-clickable`** 了解页面，避免 `dump` 的大量噪声
- 不确定文字是否完整时用 **`find <regex>`** 搜索，再决定用什么文字点击

### 点击
- 优先用 **`tap-re`**，它支持 `|` 多选、大小写不敏感、自动选可点击元素
- 文字完全确定时用 `tap`，按 resource-id 时用 `tap-id`
- `tap-re` 输出会标注 `[可点击]` 或 `[非clickable,坐标点击]`，后者成功率稍低

### 点击后验证（必须执行）
- 用 **`has <关键词>`** 快速验证是否进入正确页面
  - exit 0 → 页面正确，继续
  - exit 1 → 页面有误，**立即停止任务并报告**，不能继续执行后续步骤
- 例：点击"发布"后，`has "发布成功|已发布"` 验证是否发布完成
- 只有需要看具体元素细节时才用 `dump-clickable`

### 输入
- 输入中文必须用 **`type`**（剪贴板粘贴，无乱码）
- 先 `tap-re` 聚焦输入框，再 `type` 输入

### 输出规范
- **必须**将脚本完整输出原样展示给用户
