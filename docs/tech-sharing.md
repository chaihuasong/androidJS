# AndroidJS: 在 Android 上构建轻量级 JS 动态化引擎

> 技术分享文稿 — 适用于团队内部技术交流

---

## 一、我们要解决什么问题

想象一个场景：你的 App 有十几个功能模块，每次改一行业务逻辑都要走完整的发版流程 — 打包、审核、等待用户升级。这在快速迭代的业务中是不可接受的。

**核心矛盾**：原生代码发布慢，但业务逻辑变化快。

业界常见方案有 React Native、Flutter、WebView H5 等，但如果你的需求不是"替换整个 UI 层"，而只是"让业务逻辑可动态下发"呢？

AndroidJS 尝试回答一个更精确的问题：

> **能不能只用 JS 来写业务逻辑，原生只提供通用能力，两者通过一个极简的桥来通信？**

---

## 二、整体架构：三层分离

```
┌─────────────────────────────────────────────┐
│              JS 脚本层（业务逻辑）              │
│  accounting.js / quran_widget.js / ...       │
│  ✦ 可远端下发  ✦ 本地缓存  ✦ 版本控制          │
├─────────────────────────────────────────────┤
│              Bridge 桥接层（JSON 协议）         │
│  __bridge.invoke(module, method, argsJson)   │
│  → @JavascriptInterface → BridgeDispatcher   │
│  → NativeModule.invoke() → BridgeResponse    │
├─────────────────────────────────────────────┤
│              原生能力层（通用模块）              │
│  database / voice / widget / storage /       │
│  network / log / ui                          │
└─────────────────────────────────────────────┘
```

**设计原则只有一条：原生代码不包含任何业务逻辑。**

原生侧提供的是"数据库读写"、"语音识别"、"Widget 更新"这类通用能力，而"记账时怎么解析'午饭花了30块'"、"古兰经每天展示哪条经文"这些业务规则全部写在 JS 里。

这意味着业务变更 = JS 文件更新，不需要发版。

---

## 三、JS 引擎选型：为什么用 WebView 而不是 QuickJS

这是最常被问到的问题。先看对比：

| 维度 | WebView | QuickJS |
|------|---------|---------|
| 兼容性 | 所有 Android 设备原生支持 | 需要编译 so 库，有 ABI 兼容问题 |
| 包体积 | 0（系统自带） | +1~2 MB |
| 性能 | 较慢（V8 启动开销） | 快（轻量级引擎） |
| 调试 | Chrome DevTools 可直接调试 | 需要自建调试工具 |
| API | 丰富（完整浏览器 API） | 纯 JS，无 DOM/BOM |
| 多实例 | 可以，每个 WebView 独立 | 可以，每个 Runtime 独立 |

**我们选择 WebView 的核心原因**：在项目早期，"能跑起来"比"跑得快"更重要。WebView 零依赖、零配置，而 QuickJS 的 NDK 编译和 ABI 适配会显著拖慢原型验证速度。

**关键设计决策**：将引擎实现藏在 `JSEngine` 接口后面。外部只关心 `evaluateScript(code): String?`，不关心底层是 WebView 还是 QuickJS。日后切换引擎，上层代码零改动。

```kotlin
// 外部使用方完全不知道底层是 WebView
val engine = AndroidJSEngine.Builder(context)
    .addModule(DatabaseModule(context))
    .addModule(VoiceModule(context))
    .setExecutionTimeout(5000)
    .build()

engine.initialize()
engine.executeAssetScript("js/accounting.js")
```

---

## 四、桥接机制：JS 和原生是怎么"说上话"的

这是整个系统最核心的部分。用一张调用链来说明：

```
JS 端                           原生端
──────                         ──────
__bridge.invoke(
  'database',
  'query',
  '{"sql":"SELECT *..."}'
)
  │
  ▼
__bridge_native.invoke()    ← @JavascriptInterface（WebKit 线程）
  │
  ▼
BridgeDispatcher.dispatch()  ← 按 moduleName 路由
  │
  ▼
DatabaseModule.invoke()      ← 具体模块执行逻辑
  │
  ▼
return QueryResult JSON      → 模块返回结果字符串
  │
  ▼
BridgeResponse.success(data) → 包装为统一响应格式
  │
  ▼
'{"success":true,"data":"..."}'
  │
  ▼
JS 拿到 JSON 字符串，JSON.parse 后使用
```

### 4.1 桥接协议设计

统一的请求-响应模型：

```kotlin
// 请求（JS → 原生）
// 参数直接通过 @JavascriptInterface 方法参数传递
fun invoke(moduleName: String, method: String, argsJson: String): String

// 响应（原生 → JS）
data class BridgeResponse(
    val success: Boolean,
    val data: String? = null,   // 模块返回的 JSON 字符串
    val error: String? = null
)
```

`data` 是 `String` 而不是 `JsonObject`，这是刻意的设计 — **双层序列化**：模块先序列化自己的结果为 JSON 字符串，然后 `BridgeResponse` 再序列化一次。JS 端需要两次 `JSON.parse`：

```javascript
var response = JSON.parse(__bridge.invoke('database', 'query', argsJson));
// response.data 是字符串，需要再 parse 一次
var data = JSON.parse(response.data);
var rows = data.rows;
```

为什么不直接用嵌套对象？因为这样 `BridgeResponse` 的结构完全固定，不需要知道每个模块返回什么。序列化/反序列化逻辑更简单，出错也更容易排查。

### 4.2 同步 vs 异步

大多数桥接调用是**同步**的 — JS 调 `__bridge.invoke()`，阻塞等待原生返回。这在数据库查询、存储读写等快速操作中完全没问题。

但语音识别这类耗时操作不能阻塞 JS 线程，所以有异步路径：

```kotlin
interface NativeModule {
    // 同步调用
    fun invoke(method: String, argsJson: String): String?

    // 异步调用（默认实现委托给同步方法）
    fun invokeAsync(method: String, argsJson: String, callback: (String) -> Unit) {
        val result = invoke(method, argsJson)
        callback(result ?: "{}")
    }
}
```

默认的 `invokeAsync` 直接调同步方法，只有真正需要异步的模块（如 `VoiceModule`）才覆写。这样大多数模块只需实现一个方法。

### 4.3 console 重定向

一个小但关键的细节：JS 的 `console.log` 被重定向到了原生 `LogModule`：

```javascript
console = {
    log: function() {
        var msg = Array.prototype.slice.call(arguments).join(' ');
        __bridge.invoke('log', 'log', JSON.stringify({level: 'info', message: msg}));
        __originalConsole.log.apply(__originalConsole, arguments);
    }
};
```

这意味着 JS 里的 `console.log('hello')` 会出现在 Android Logcat 中（tag 为 `JS`），极大方便了调试。

---

## 五、NativeModule 设计：如何做到"通用"

以 `DatabaseModule` 为例，看看"通用"到底意味着什么。

**反面教材**（业务耦合）：

```kotlin
// 糟糕：模块里写死了业务表结构
class AccountingStorageModule : NativeModule {
    fun addTransaction(amount: Double, category: String) { ... }
    fun getTransactions(): List<TransactionEntity> { ... }
}
```

**正确做法**（通用能力）：

```kotlin
// DatabaseModule 只提供 SQL 执行能力，表结构由 JS 定义
class DatabaseModule(context: Context) : NativeModule {
    override val name = "database"

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "exec"  -> exec(args)    // DDL/DML: CREATE TABLE, INSERT, UPDATE, DELETE
            "query" -> query(args)   // SELECT → 返回 {columns, rows}
            else    -> null
        }
    }
}
```

JS 侧完全控制数据结构：

```javascript
// JS 自己建表
__bridge.invoke('database', 'exec', JSON.stringify({
    sql: 'CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, amount REAL, category TEXT, type TEXT, timestamp INTEGER)'
}));

// JS 自己查询
var result = JSON.parse(__bridge.invoke('database', 'query', JSON.stringify({
    sql: 'SELECT * FROM transactions ORDER BY timestamp DESC'
})));
```

同样的模式应用到所有模块：

| 模块 | 通用能力 | JS 控制的部分 |
|------|---------|-------------|
| `database` | SQL 执行 | 表结构、查询逻辑 |
| `voice` | 语音识别 | 识别语言（locale） |
| `widget` | Widget 文本更新 | 更新哪些字段、什么内容 |
| `storage` | KV 读写 | key 和 value 的含义 |
| `network` | HTTP 请求 | URL、请求体、响应处理 |
| `ui` | Activity UI 控制 | 标题、按钮文字、列表数据 |

---

## 六、脚本动态管理：从下载到运行

脚本的生命周期分为四个阶段：

```
发现 → 下载 → 缓存 → 执行
```

### 6.1 发现：脚本清单（Manifest）

服务端维护一个 JSON 清单，描述所有可用脚本：

```json
{
  "manifestVersion": 1,
  "scripts": [
    {
      "id": "accounting",
      "name": "智能记账",
      "version": 1,
      "url": "https://cdn.example.com/scripts/accounting_v1.js",
      "requiredModules": ["voice", "database"],
      "display": "activity"
    }
  ]
}
```

`requiredModules` 声明了脚本依赖的原生模块。宿主可以据此做**能力检查** — 如果设备不支持某个模块，UI 上可以置灰或隐藏。

`display` 决定展示方式：`"dialog"` 弹窗展示结果，`"activity"` 启动完整页面。

### 6.2 下载与缓存

```
filesDir/scripts/
  ├── accounting_v1.js     ← 缓存的脚本文件
  └── quran_widget_v1.js

SharedPreferences (androidjs_scripts)
  ├── script_accounting → { info, localPath, cachedAt }
  └── script_quran_widget → { info, localPath, cachedAt }
```

版本控制策略很简单：新版本下载后，自动删除旧版本文件。对比 `info.version` 和 `cached.info.version` 判断是否需要更新。

### 6.3 Mock 协议

开发阶段使用 `mock://` 协议，从 assets 读取脚本，无需真实服务器：

```kotlin
val content = if (info.url.startsWith("mock://")) {
    context.assets.open("js/$assetName.js").bufferedReader().use { it.readText() }
} else {
    // 真实网络下载...
}
```

上线时只需把 manifest 中的 URL 从 `mock://accounting` 改为 `https://cdn.example.com/scripts/accounting_v1.js`，代码无需任何改动。

### 6.4 兜底机制

脚本来源有三个优先级：

```
1. 本地缓存文件（已下载的最新版本）
2. APK 内置 assets（打包时内嵌的版本）
3. 无法执行（提示用户下载）
```

ScriptActivity 实现了自动降级：

```kotlin
if (scriptPath != null) {
    try {
        engine.executeFileScript(scriptPath)  // 优先用缓存
    } catch (e: Exception) {
        // 缓存文件损坏？降级到 assets
    }
}
if (!loaded && assetPath != null) {
    engine.executeAssetScript(assetPath)      // assets 兜底
}
```

---

## 七、JS 驱动原生 UI：ScriptActivity 模式

这是架构中最有意思的部分。传统做法是每个业务写一个 Activity，而我们只有**一个** ScriptActivity，所有业务 UI 都由 JS 驱动。

### 7.1 生命周期协议

```
ScriptActivity.onCreate()
    │
    ▼
engine.initialize()
    │
    ▼
engine.executeScript("accounting.js")   ← 加载业务脚本
    │
    ▼
JS: onActivityCreated()                  ← JS 配置 UI
    │  ├─ ui.configure({title, hint, ...})
    │  ├─ database.exec(CREATE TABLE ...)
    │  └─ refreshList()
    │
    ▼
用户操作
    │
    ├─ 输入文字 → JS: onInput(text)
    ├─ 语音输入 → JS: onVoiceResult(text)
    └─ ...
```

JS 通过 `UIModule` 控制原生 UI，而不是自己渲染 UI：

```javascript
function onActivityCreated() {
    // 配置页面标题、输入框提示、按钮文字
    __bridge.invoke('ui', 'configure', JSON.stringify({
        title: '智能记账',
        inputHint: '输入记账内容，如：午饭花了30块',
        submitText: '记账',
        showVoice: true,
        voiceLocale: 'zh-CN',
        summaryLabels: ['总支出', '总收入']
    }));
}

function onInput(text) {
    var result = parseTransaction(text);    // 纯 JS 业务逻辑
    if (result.success) {
        __bridge.invoke('database', 'exec', ...);       // 存储
        __bridge.invoke('ui', 'showResult', ...);       // 显示结果
        __bridge.invoke('ui', 'clearInput', '{}');      // 清空输入
        refreshList();                                   // 刷新列表
    }
}
```

### 7.2 为什么不用 WebView 直接渲染 UI

既然 JS 引擎本身就是 WebView，为什么不直接用 HTML 渲染 UI 呢？

因为我们要的是**原生体验** — Material Design 组件、原生动画、系统一致的交互反馈。WebView 渲染的 H5 页面在细节上永远追不上原生 UI。

JS 只负责"说什么"（数据和指令），原生负责"怎么说"（渲染和交互）。

---

## 八、线程模型

理解线程模型对排查问题至关重要：

```
Main Thread (UI)
  ├─ WebView 创建和初始化
  ├─ evaluateJavascript() 调用
  ├─ evaluateJavascript() 回调
  └─ UIModule.runOnUiThread { UI 更新 }

WebKit Thread
  ├─ JS 代码实际执行
  └─ @JavascriptInterface 方法执行
      ├─ BridgeDispatcher.dispatch()
      ├─ NativeModule.invoke()
      └─ 返回结果给 JS（同步阻塞）

JSEngine-Thread (协程调度)
  └─ 协程 scope 管理

IO Thread
  ├─ ScriptManager 网络请求
  └─ 文件读写
```

**关键约束**：
- `evaluateJavascript` 必须在主线程调用
- `@JavascriptInterface` 方法在 WebKit 线程执行
- UI 操作必须 post 到主线程
- `SpeechRecognizer` 必须在主线程创建和启动

---

## 九、踩过的坑

### 9.1 evaluateJavascript 返回值的坑

`WebView.evaluateJavascript` 的回调结果不是 JS 的原始值，而是 **JSON 编码后的字符串**：

```
JS 返回值              回调拿到的字符串
─────────            ──────────
42                → "42"
"hello"           → "\"hello\""
null/undefined    → "null"
{title:"hi"}      → 不会自动序列化，需要 JS 端先 JSON.stringify
```

对于字符串返回值，回调值外层有引号，内部引号被转义。处理时需要：
1. 剥离外层引号
2. 反转义 `\"` → `"`
3. 反转义 `\\` → `\`
4. **不要**在 JSON.parse 之前转换 `\n` → 换行符（会破坏 JSON 结构）

```kotlin
val cleanResult = result.let {
    if (it.startsWith("\"") && it.endsWith("\"")) {
        it.substring(1, it.length - 1)
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            // 注意：不要在这里 replace("\\n", "\n")
            // 让 JSON 解析器自己处理 \n 转义
    } else it
}
val data = json.decodeFromString<DialogResult>(cleanResult)  // 正确 ✓
```

### 9.2 双层 JSON 序列化

桥接响应中 `data` 是字符串，不是对象。JS 端需要两次 parse：

```javascript
// 错误 ❌ — queryResult.data 是字符串，不是对象
var rows = JSON.parse(bridgeResult).data.rows;

// 正确 ✓ — 两次 parse
var response = JSON.parse(bridgeResult);
var data = JSON.parse(response.data);
var rows = data.rows;
```

### 9.3 this 上下文

在 `evaluateJavascript` 中执行的脚本，顶层 `this` 指向 `window`（全局对象）。要把函数暴露给后续的 `evaluateJavascript` 调用，需要显式赋值到全局：

```javascript
function onInput(text) { ... }

// 必须显式导出到全局
this.onInput = onInput;

// 之后的 evaluateJavascript 调用才能找到它
// engine.executeScript("onInput('hello')")
```

---

## 十、项目结构与模块边界

```
androidjs/
├── core/                    ← 核心 SDK，可独立发布为 AAR
│   ├── engine/
│   │   ├── JSEngine.kt      ← WebView JS 引擎
│   │   ├── JSContext.kt      ← 执行上下文（超时、内存限制）
│   │   └── AndroidJSEngine.kt ← 对外主入口（Builder 模式）
│   ├── bridge/
│   │   ├── NativeModule.kt   ← 模块接口（3 个方法：invoke / invokeAsync / destroy）
│   │   ├── BridgeDispatcher.kt ← 模块路由
│   │   └── BridgeMessage.kt  ← 请求/响应数据类
│   ├── modules/
│   │   ├── LogModule.kt      ← console → Logcat
│   │   ├── StorageModule.kt  ← SharedPreferences
│   │   ├── NetworkModule.kt  ← OkHttp GET/POST
│   │   ├── DatabaseModule.kt ← SQLite（JS 控制 schema）
│   │   ├── VoiceModule.kt    ← 语音识别
│   │   └── WidgetModule.kt   ← AppWidget 更新
│   └── script/
│       ├── ScriptManager.kt  ← 下载/缓存/版本控制
│       └── ScriptInfo.kt     ← 数据模型
│
├── scene-widget/            ← 纯资源模块（只有 assets/js/quran_widget.js）
├── scene-accounting/        ← 纯资源模块（只有 assets/js/accounting.js）
│
└── app/                     ← 演示应用
    ├── demo/
    │   ├── MainActivity.kt   ← 脚本市场（清单展示 + 下载 + 运行）
    │   ├── ScriptActivity.kt ← 通用 JS 驱动 Activity
    │   ├── UIModule.kt       ← JS → 原生 UI 桥接
    │   └── ScriptListAdapter.kt
    └── widget/
        ├── ScriptWidgetProvider.kt ← 通用 Widget Provider
        └── WidgetUpdateWorker.kt   ← WorkManager 定时更新
```

**模块边界清晰**：
- `core` 完全不知道"记账"或"古兰经"的存在
- `scene-*` 模块只有 JS 文件，没有 Kotlin 代码
- `app` 只做组装和 UI 壳，不包含业务逻辑

---

## 十一、思考与展望

### 这个方案适合什么场景

- **轻量级动态化**：业务逻辑频繁变化，但 UI 结构相对稳定
- **规则引擎**：分类规则、匹配算法等可用 JS 表达的逻辑
- **内容型功能**：日历、天气、每日推荐等数据驱动的功能
- **快速原型验证**：新功能先用 JS 跑通，再决定是否沉淀为原生

### 不适合什么场景

- 高性能计算（图像处理、加密）
- 复杂 UI 交互（手势、动画密集的页面）
- 需要大量原生 API 的功能（相机、蓝牙）

### 可能的演进方向

1. **引擎升级**：WebView → QuickJS，获得更好的启动性能和更小的内存占用
2. **热更新**：结合推送实现脚本静默更新，用户无感知
3. **沙箱安全**：限制 JS 可调用的模块，防止恶意脚本
4. **TypeScript 支持**：开发时用 TS，构建时编译为 JS 下发
5. **可视化编排**：低代码平台生成 JS 脚本，进一步降低业务开发门槛

---

## 附：5 分钟快速上手

### 1. 创建一个自定义 NativeModule

```kotlin
class MyModule : NativeModule {
    override val name = "myModule"

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "greet" -> {
                val args = json.decodeFromString<GreetArgs>(argsJson)
                json.encodeToString(GreetResult("Hello, ${args.name}!"))
            }
            else -> null
        }
    }
}
```

### 2. 注册到引擎

```kotlin
val engine = AndroidJSEngine.Builder(context)
    .addModule(MyModule())
    .build()
engine.initialize()
```

### 3. 在 JS 中调用

```javascript
var result = JSON.parse(__bridge.invoke('myModule', 'greet',
    JSON.stringify({ name: 'World' })));
// result → { success: true, data: '{"message":"Hello, World!"}' }

var data = JSON.parse(result.data);
console.log(data.message);  // "Hello, World!"
```

从注册到调用，三步完成。这就是 AndroidJS 的全部 API。

---

*技术栈：Kotlin 2.1 / Android SDK 24-35 / WebView / kotlinx.serialization / OkHttp / WorkManager / Coroutines*
