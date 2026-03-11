---
name: ui-control
description: "Tool for app UI automation — clicking, typing, swiping, reading screen content. Use for tasks like sending a WeChat message, posting to social media, navigating settings, filling a form. Standard workflow: dump-clickable → tap-re → has (verify) → repeat. ❌ DO NOT use this skill for internet search tasks — use the web-search skill instead. ❌ DO NOT use this skill to open URLs for reading web page content — use curl in web-search skill instead."
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

## 任务自动化框架（必读）

**对任何涉及 App 操作的任务，必须按以下框架执行：**

### 第 0 步：了解当前状态

```bash
# 先截图，看当前屏幕是什么状态
$PYTHON $SCRIPT screenshot

# 再列出可点击元素，确认在哪个页面
$PYTHON $SCRIPT dump-clickable
```

### 第 1 步：拆解任务，逐步执行

将任务拆成最小 UI 操作单元。每步执行后必须验证。

```
任务示例：「帮我在微信给张三发消息"明天见"」
拆解：
  1. 检查是否在微信 → has "微信|WeChat"
  2. 如不在，打开微信 → 用 am start 命令启动 App
  3. 找到搜索/联系人入口 → dump-clickable，找"搜索"按钮
  4. 点击搜索 → tap-re "搜索"
  5. 输入联系人名字 → type "张三"
  6. 点击联系人 → tap-re "张三"
  7. 找到输入框并聚焦 → tap-re "输入|发送消息"
  8. 输入消息内容 → type "明天见"
  9. 点击发送 → tap-re "发送|Send"
  10. 验证发送成功 → has "明天见"
```

### 第 2 步：每步操作后必须验证

```bash
# 点击后用 has 快速验证是否进入正确状态
$PYTHON $SCRIPT has "目标关键词"
# exit 0 → 继续下一步
# exit 1 → 页面不符预期，立即截图诊断，不能盲目继续
```

### 第 3 步：遇到问题的处理策略

| 问题 | 处理方式 |
|------|---------|
| 找不到元素 | 先 `find <regex>` 搜索，再用 `dump` 查看全部，或 `swipe scroll-down` 后重试 |
| 点击无响应 | 尝试 `tap-id` 按 resource-id 点击，或 `dump` 确认元素是否真的可点击 |
| 页面没变化 | `screenshot` 截图确认当前状态，检查是否有弹窗/权限请求挡住了 |
| App 未启动 | 用 `am start -n <package>/<activity>` 或 `monkey -p <package> 1` 启动 |
| 输入法挡住元素 | `key back` 收起输入法后再操作 |

### 发布文章 / 笔记的填写顺序（防止必填字段为空）

发布内容前，必须按以下顺序检查并填写所有字段，**禁止直接点发布按钮而跳过空字段**：

```
发布流程顺序：
  0. 进入发布页面后，先检查是否有"写长文"、"长文"、"图文"等入口或选项 →
     如果有，优先选择"写长文"模式，再开始填写内容（长文字数上限更高，适合内容丰富的文章）
  1. dump-clickable 查看编辑页面所有输入框
  2. 如果有"标题"输入框 → 先点击聚焦，再 type 输入标题
  3. 然后点击"正文/内容"输入框 → type 输入正文内容
  4. 检查其他必填项（话题、标签、封面等）→ 逐一填写
  5. 所有字段填完后，再点击"发布"按钮
```

**规则：**
- `dump-clickable` 后，先识别页面上所有输入框（标题、正文、话题等）
- **有标题框则必须先填标题**，不能直接跳到正文输入
- **检测到正文/详情/内容输入框时，必须将完整正文全部输入完毕**，不能只输入摘要或部分内容，不能留空
- 正文内容较长时，优先尝试一次性 `type` 全部输入；输入后用 `has` 或 `dump` 确认文字已完整显示在输入框中
- 发布前用 `dump-clickable` 再扫一遍，确认标题和正文均非空且内容完整
- 如果发布失败且提示"请输入标题"/"必填项为空"，立即返回补填，不要重复点击发布

### 打开 App 的方式

```bash
# 方法1：通过包名启动（最可靠）
$PYTHON $SCRIPT tap-re "微信"   # 如果在桌面找得到图标

# 方法2：adb 命令启动（App 不在当前屏幕时）
# 微信
/data/data/com.termux/files/usr/bin/adb shell am start -n com.tencent.mm/.ui.LauncherUI
# 支付宝
/data/data/com.termux/files/usr/bin/adb shell am start -n com.eg.android.AlipayGphone/.AlipayLogin
# 设置
/data/data/com.termux/files/usr/bin/adb shell am start -a android.settings.SETTINGS
# 浏览器（通用）
/data/data/com.termux/files/usr/bin/adb shell am start -a android.intent.action.VIEW -d "https://example.com"

# 方法3：回到桌面后找图标
$PYTHON $SCRIPT key home
$PYTHON $SCRIPT tap-re "微信|WeChat"
```

### 全网搜索（必须用 web-search 技能）

⚠️ **执行任何"搜索互联网"任务时，禁止打开浏览器手动搜索。必须使用 `web-search` 技能。**

#### 第一步：用 web-search 技能获取链接列表

```bash
GOOGLE_SEARCH_API_KEY=$(grep GOOGLE_SEARCH_API_KEY ~/.openclaw/.env | cut -d= -f2) \
GOOGLE_SEARCH_CX=$(grep GOOGLE_SEARCH_CX ~/.openclaw/.env | cut -d= -f2) \
bash ~/.openclaw/workspace/skills/web-search/web-search.sh "搜索关键词" 10
```

#### 第二步：用 curl 直接读取链接详情（禁止打开浏览器）

搜索返回链接列表后，用 curl 直接抓取正文，参见 web-search 技能文档中的"读取网页详情"章节。只有需要登录或页面有交互操作时，才用 ui-control 打开浏览器。

---

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
$PYTHON $SCRIPT tap-re "^X$"             # 精确匹配"X"（避免误点 XClub/Xbox）
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

### 浏览列表 / 逐篇查看内容

浏览信息流、搜索结果等列表时，**不能在同一位置反复 tap-re**，必须用"滚动 → 检查 → 点击 → 返回 → 继续滚动"循环：

```bash
# ── 标准列表浏览循环 ──────────────────────────────────────────

# 1. 先截图了解当前屏幕，确认可见哪些文章
$PYTHON $SCRIPT screenshot

# 2. dump 读取当前可见文章的标题/摘要，判断是否是目标内容
$PYTHON $SCRIPT dump

# 3a. 若当前屏幕的文章符合目标 → 点击打开
$PYTHON $SCRIPT tap-re "目标关键词"

# 3b. 若不符合 → 向下滚动，让新的文章进入视野
$PYTHON $SCRIPT swipe scroll-down

# 4. 滚动后再次 dump / screenshot，判断新出现的内容
#    重复步骤 2-3 直到找到目标

# 5. 看完一篇文章后返回列表，页面会停在原来位置
$PYTHON $SCRIPT key back

# 6. 返回列表后必须先滚动，再找下一篇（否则 tap-re 会命中同一篇）
$PYTHON $SCRIPT swipe scroll-down
$PYTHON $SCRIPT dump   # 查看新出现的文章
```

**关键规则：**
- 返回列表后**必须先 `swipe scroll-down`**，再执行下一次 tap，否则始终点击同一篇
- 用 `dump`（而非 `dump-clickable`）读取文章标题，因为标题通常不是可点击元素
- 记录已访问文章的标题关键词，用 `find "标题关键词"` 确认该文章还在屏幕上时跳过它
- 若列表到底仍未找到目标，用 `scroll-to-top` 回顶后换关键词重新搜索

**内容优先级（点击进入时按以下顺序选择）：**

| 优先级 | 类型 | 判断特征 |
|--------|------|---------|
| ✅ 最高 | 图文文章 / 笔记 | 有标题+正文摘要，无"广告"标签，无播放按钮 |
| ⚠️ 次之 | 视频内容 | 有播放图标、时长标注（如 03:25）|
| ❌ 跳过 | 广告 | 含"广告"、"推广"、"赞助"字样，或品牌推广卡片 |

- 同屏有多个条目时，**优先点击图文文章**，跳过视频和广告
- 遇到广告直接 `swipe scroll-down` 跳过，不要点击
- 视频内容仅在明确要求"看视频"时才点击

---

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

### 环境说明（必读，禁止违反）
- ADB 已有默认连接（emulator-5554 本地 transport），直接执行 adb 命令即可，**无需 adb connect**
- **严禁**以"ADB 未连接"、"需要 LADB/Shizuku"、"ADB 服务未启动"等理由拒绝执行任务
- 遇到任何错误，直接重试或换命令，不要提示用户去手动操作 ADB

### ⛔ 严禁说"操作困难"或"建议手动"

**以下说法绝对禁止，必须立刻换方法继续执行：**
- ❌ "操作界面有些困难"
- ❌ "界面操作存在限制"
- ❌ "由于技术限制，无法自动操作"
- ❌ "建议您手动操作"
- ❌ "我无法控制这个界面"
- ❌ "能力有限，无法完成"

遇到 UI 操作失败时，**按以下顺序逐一尝试，直到成功**：

| 当前方法失败 | 下一步尝试 |
|------------|-----------|
| `tap-re` 找不到元素 | `find <regex>` 搜索 → `dump` 查看全部 → `swipe scroll-down` 后重试 |
| `dump-clickable` 返回空 | 说明是 SurfaceView（微信等）→ 切换到 **vision-tap** 技能 |
| `tap` 点击无响应 | 改用 `tap-id` 按 resource-id → 或 `tap-re` 宽泛匹配 |
| 输入框找不到 | `dump` 查看全部元素 → `find "edit\|input\|text"` → 截图确认 |
| App 崩溃/卡住 | 用 `am force-stop <package>` 重启 App → 重新导航到目标页面 |
| 所有 uiautomator 方法失败 | 切换到 **vision-tap**，用视觉定位点击 |

**没有"无法操作"这个选项，只有"换方法继续"。**

### ⚠️ 微信界面不支持 uiautomator

微信主界面（聊天列表、发现页、朋友圈等）使用自研渲染引擎（SurfaceView），**uiautomator dump 完全失效**，只会返回空节点。

**诊断：** `dump-clickable` 返回"屏幕无可点击元素"即为失效。

**解决：切换到 vision-tap 技能**，详见 vision-tap SKILL.md。

```bash
PYTHON=/data/data/com.termux/files/usr/bin/python3
VTAP=/data/data/com.termux/files/home/vision-tap.py

$PYTHON $VTAP tap "右上角搜索图标，放大镜形状的Q"   # 搜索联系人
$PYTHON $VTAP tap "搜索结果列表中第一个联系人"       # 点进对话
$PYTHON $VTAP tap "底部消息输入框"                  # 聚焦输入
$PYTHON $VTAP tap "右侧绿色发送按钮"                # 发送
```

#### 发短信：手机号必须验证为 11 位

**⚠️ 强制规则：发送短信前必须校验手机号，不合法则停止。**

```bash
# 校验手机号是否为 11 位纯数字
PHONE="13812345678"
if ! echo "$PHONE" | grep -Eq '^[0-9]{11}$'; then
  echo "❌ 手机号不合法：'$PHONE' 不是 11 位纯数字，已终止，未发送"
  exit 1
fi
echo "✅ 手机号合法：$PHONE，继续发送"

# 打开短信 App 并发送
$ADB shell am start -a android.intent.action.SENDTO -d "smsto:$PHONE" --es sms_body "消息内容"
```

**规则：**
- 手机号必须恰好 11 位纯数字（`^[0-9]{11}$`），不能多位不能少位
- 号码中不能含空格、横线、括号等任何非数字字符（需预处理清洗后再校验）
- 校验不通过 → **立即停止，不发送，向用户索要完整号码**
- 用户提供的号码含格式字符（如 `138-1234-5678`）→ 先去除非数字字符再校验
- **⛔ 严禁用不完整号码（如只有3位、7位等）直接发送或尝试发送** — 即使用户只说了"发给135"，也必须停下来问："请提供完整的11位手机号"，不得猜测补全
- 用户没有提供手机号 → 必须先问用户要号码，不得自行填写任何号码

---

#### 微信发消息：收件人必须精准匹配 + 进入聊天后二次确认

**⚠️ 严格规则，必须遵守：**

1. **搜索联系人/群聊时必须精准匹配，禁止模糊匹配**
   - 输入完整的联系人名或群名，不能只输入前几个字
   - 搜索结果出现后，**必须找到名称与目标完全一致的条目**才能点击
   - 如果搜索结果只有部分匹配（如目标是"项目A群"，结果只有"项目群"），**不能点击，必须重新搜索或报告无法精确匹配**

2. **进入聊天后必须二次确认聊天标题**
   - 进入对话框后，立即截图或 dump 确认聊天窗口顶部标题栏的名称
   - 只有标题与目标收件人名称**完全一致**，才能开始输入/发送
   - 如果标题不匹配 → **立即退出（key back），不得输入任何内容，不得发送**
   - 报告："进入的聊天标题为 [实际标题]，与目标 [目标名称] 不符，已退出，未发送"

```bash
# 微信发消息完整流程（精准匹配版）
TARGET="张三"  # 目标收件人名称（精确）

# 第1步：启动微信
$ADB shell am start -n com.tencent.mm/.ui.LauncherUI

# 第2步：搜索联系人（输入完整名称）
$PYTHON $VTAP tap "右上角搜索图标"
$PYTHON $SCRIPT type "$TARGET"   # 输入完整名称，不能缩写

# 第3步：在搜索结果中找名称完全一致的条目
$PYTHON $VTAP screenshot   # 截图查看搜索结果
# 必须确认有与 "$TARGET" 完全一致的结果，才执行下一步

# 第4步：点击精确匹配的联系人
$PYTHON $VTAP tap "搜索结果中名称与'$TARGET'完全一致的联系人条目"

# 第5步：进入聊天后立即截图，二次确认标题
$PYTHON $VTAP screenshot
# → 确认聊天标题为 "$TARGET"，确认无误后才继续
# → 如果标题不符：立即 key back，不发送

# 第6步：确认标题一致后，输入并发送消息
$PYTHON $VTAP tap "底部消息输入框"
$PYTHON $SCRIPT type "消息内容"
$PYTHON $VTAP tap "右侧绿色发送按钮"
```

### 操作前
- 优先用 **`dump-clickable`** 了解页面，避免 `dump` 的大量噪声
- 不确定文字是否完整时用 **`find <regex>`** 搜索，再决定用什么文字点击

### 点击
- 优先用 **`tap-re`**，它支持 `|` 多选、大小写不敏感、自动选可点击元素
- 文字完全确定时用 `tap`，按 resource-id 时用 `tap-id`
- **单字或短名称必须加 `^` `$` 精确匹配**，否则会误命中含该字的其他元素
  - ❌ `tap-re "X"` → 会匹配 XClub、Xbox 等
  - ✅ `tap-re "^X$"` → 只匹配文字恰好是 "X" 的元素
- `tap-re` 输出会标注 `[可点击]` 或 `[坐标点击]`，后者成功率稍低

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
