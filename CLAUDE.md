# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 提供本仓库的工程指引。

## 构建命令

```bash
./gradlew assembleDebug          # 构建 debug APK
./gradlew installDebug           # 构建并安装到已连接设备
./gradlew :core:assembleDebug    # 构建单个模块
./gradlew lint                   # 运行 lint 检查
./gradlew test                   # 运行单元测试（暂无）
./gradlew connectedAndroidTest   # 运行设备端测试
./gradlew clean                  # 清理构建产物
```

## 工程概述

AndroidJS 是一个多模块 Android 工程（包名 `com.example.androidjs`），核心能力是在 Android 端通过 WebView 运行 JavaScript 脚本，并通过 JSON Bridge 实现 JS 与原生模块的双向通信。业务脚本采用**动态下载 + 本地缓存**的方式与宿主程序解耦，宿主只提供引擎和原生能力，业务逻辑由远端 JS 脚本承载。

技术栈：Min SDK 24, Compile SDK 35, Kotlin 2.1, AGP 8.7.3, Java 17。

## 模块结构

```
core/               → 核心 SDK 库（独立，无外部模块依赖）
  ├── engine/       → WebView JS 引擎（JSEngine、AndroidJSEngine、JSContext）
  ├── bridge/       → JS/原生桥接（BridgeDispatcher、NativeModule、BridgeMessage）
  ├── modules/      → 内置原生模块（log、storage、network）
  ├── plugin/       → 插件接口（AndroidJSPlugin）
  └── script/       → 脚本管理（ScriptManager、ScriptInfo）—— 下载/缓存/版本控制

scene-widget/       → 古兰经桌面组件插件
  ├── WidgetPlugin / WidgetNativeModule   → 插件注册 + 原生桥接
  ├── QuranWidgetProvider                 → AppWidget Provider
  ├── WidgetUpdateWorker                  → WorkManager 24 小时定时更新
  └── assets/js/quran_widget.js           → 经文选取逻辑（内置兜底）

scene-accounting/   → 智能记账插件
  ├── AccountingPlugin / VoiceNativeModule / AccountingStorageModule → 插件 + 原生模块
  ├── data/         → Room 数据库（accounting_db v1, TransactionEntity/Dao）
  ├── ui/           → AccountingActivity + TransactionAdapter
  └── assets/js/accounting.js             → 中文自然语言记账解析（内置兜底）

app/                → 演示应用（组装所有插件）
  ├── MainActivity  → 脚本市场：加载清单 → 展示脚本卡片 → 下载/运行
  ├── ScriptCardAdapter → RecyclerView 脚本卡片适配器
  └── assets/mock/manifest.json → 模拟远端脚本清单
```

**依赖关系**：`app` → `core` + `scene-widget` + `scene-accounting`；两个 scene 模块各自依赖 `core`。

## 架构设计

### JS/原生桥接

JavaScript 运行在 WebView 中（非 QuickJS），通过 `kotlinx.serialization` 进行 JSON 序列化通信：

```
JS: __bridge.invoke(moduleName, method, argsJson)
  → WebView @JavascriptInterface ("__bridge_native")
  → BridgeDispatcher.dispatch()
  → NativeModule.invoke()
  → BridgeResponse JSON 返回给 JS
```

核心桥接类位于 `core/src/main/kotlin/.../core/bridge/`：
- `BridgeDispatcher` — 按模块名路由调用到对应 `NativeModule`
- `NativeModule` — 接口：`name`、`invoke(method, args)`、`invokeAsync(method, args, callback)`
- `BridgeMessage.kt` — `BridgeRequest` / `BridgeResponse` 数据类

### 引擎

`AndroidJSEngine`（Builder 模式）是对外主入口，配置插件、内存限制、执行超时后调用 `initialize()` 启动。
`JSEngine` 封装 WebView，运行在专用线程（`JSEngine-Thread`），支持从 assets、文件路径或原始字符串加载执行脚本。console 调用重定向到 `LogModule`。

### 插件系统

`AndroidJSPlugin` 接口：`name`、`getModules()`、`onRegistered()`、`onDestroy()`。每个插件提供若干 `NativeModule` 实例，注册到 `BridgeDispatcher` 中。

**内置模块**（core）：`log`、`storage`（SharedPreferences）、`network`（OkHttp GET/POST）
**Widget 插件模块**：`widget`（触发经文更新）
**记账插件模块**：`voice`（语音识别）、`accountingStorage`（Room CRUD）

### 脚本动态管理

JS 业务脚本与宿主程序解耦，通过 `ScriptManager`（`core/.../script/`）实现：

- **远端清单**：`ScriptManifest` 描述可用脚本列表（id、版本、下载地址、所需模块）
- **下载缓存**：脚本下载到 `filesDir/scripts/{id}_v{version}.js`，元数据存 SharedPreferences
- **版本控制**：对比版本号判断是否需要更新，下载新版后自动清理旧文件
- **mock 协议**：开发阶段使用 `mock://` URL，从 assets 读取脚本；生产环境替换为真实 URL 无需改代码
- **兜底机制**：scene 模块保留 assets 中的 JS 文件作为兜底——已下载用缓存，未下载用内置 asset

数据模型（`ScriptInfo.kt`）：
- `ScriptInfo` — 脚本元信息（id、name、version、url、requiredModules、icon、color）
- `ScriptManifest` — 脚本清单
- `CachedScript` — 缓存元数据（info + localPath + cachedAt）

### 运行流程

```
App 启动
  → 初始化 AndroidJSEngine（注册所有插件）
  → 加载脚本清单（fetchManifest）
  → 展示脚本卡片列表（未下载 / 已缓存 / 有更新）
  → 用户点击"下载" → ScriptManager.downloadScript() → 缓存到本地
  → 用户点击"运行" → engine.executeFileScript(cachedPath)
  → 特殊处理：quran_widget 弹框显示经文，accounting 跳转 AccountingActivity
```

## 关键技术细节

- 所有 JSON 序列化使用 `kotlinx.serialization`（非 Gson/Moshi）
- Room 数据库 `accounting_db` v1，实体 `TransactionEntity`
- WorkManager 定时更新 Widget（24 小时周期，低电量约束），优先使用缓存脚本
- 协程 + 结构化并发处理异步操作
- ProGuard/代码混淆在 release 构建中已禁用
- OkHttp 统一 30 秒超时（NetworkModule 和 ScriptManager）
- SwipeRefreshLayout 下拉刷新脚本清单
