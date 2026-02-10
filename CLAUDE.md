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

**设计原则**：原生代码只提供通用能力（数据库、语音、Widget、UI），业务逻辑全部由 JS 脚本承载。JS 通过 UIModule 驱动原生 UI。

技术栈：Min SDK 24, Compile SDK 35, Kotlin 2.1, AGP 8.7.3, Java 17。

## 模块结构

```
core/               → 核心 SDK 库（独立，无外部模块依赖）
  ├── engine/       → WebView JS 引擎（JSEngine、AndroidJSEngine、JSContext）
  ├── bridge/       → JS/原生桥接（BridgeDispatcher、NativeModule、BridgeMessage）
  ├── modules/      → 通用原生模块（log、storage、network、database、voice、widget）
  └── script/       → 脚本管理（ScriptManager、ScriptInfo）—— 下载/缓存/版本控制

scene-widget/       → 纯 JS 资源容器（仅含 assets/js/quran_widget.js）
scene-accounting/   → 纯 JS 资源容器（仅含 assets/js/accounting.js）

app/                → 演示应用（组装所有模块）
  ├── demo/         → MainActivity、ScriptCardAdapter、DemoApplication
  │                   ScriptActivity（通用 JS 驱动 Activity）
  │                   UIModule（JS→原生 UI 桥接）
  │                   ScriptListAdapter（通用列表）
  ├── widget/       → ScriptWidgetProvider、WidgetUpdateWorker
  └── assets/mock/manifest.json → 模拟远端脚本清单
```

**依赖关系**：`app` → `core` + `scene-widget` + `scene-accounting`（scene 模块仅提供 JS assets 合并）。

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

`AndroidJSEngine`（Builder 模式）是对外主入口，通过 `addModule()` 注册 `NativeModule`，配置内存限制、执行超时后调用 `initialize()` 启动。引擎销毁时自动调用所有已注册模块的 `destroy()` 方法释放资源。
`JSEngine` 封装 WebView，运行在专用线程（`JSEngine-Thread`），支持从 assets、文件路径或原始字符串加载执行脚本。console 调用重定向到 `LogModule`。

### 模块注册

`NativeModule` 实例通过 `AndroidJSEngine.Builder.addModule()` 直接注册到 `BridgeDispatcher` 中，无需插件包装层。

**内置模块**（core/modules/）：
- `log` — Android Logcat 日志
- `storage` — SharedPreferences 键值存储
- `network` — OkHttp GET/POST
- `database` — 通用 SQLite 数据库（JS 自定义表结构和查询）
- `voice` — SpeechRecognizer 语音识别（locale 由 JS 传参）
- `widget` — AppWidget 文本更新（通过 getIdentifier 按名称查找 view ID）

**app 模块**：
- `ui`（UIModule）— JS 驱动 ScriptActivity UI（configure/showResult/updateList/updateSummary/clearInput/showToast）

### JS 驱动 UI

ScriptActivity 是通用的脚本 Activity，JS 通过 UIModule 控制所有 UI 元素：

```
JS 脚本加载 → onActivityCreated() → ui.configure({title, inputHint, ...})
用户输入    → onInput(text)       → 业务处理 → ui.showResult / ui.updateList
语音结果    → onVoiceResult(text)  → 同 onInput
```

ScriptInfo.display 字段决定脚本展示方式：
- `"dialog"` — 执行脚本，解析返回的 `{title, message}` 展示 AlertDialog
- `"activity"` — 启动 ScriptActivity，JS 脚本控制完整 UI

### 脚本动态管理

JS 业务脚本与宿主程序解耦，通过 `ScriptManager`（`core/.../script/`）实现：

- **远端清单**：`ScriptManifest` 描述可用脚本列表（id、版本、下载地址、所需模块、display）
- **下载缓存**：脚本下载到 `filesDir/scripts/{id}_v{version}.js`，元数据存 SharedPreferences
- **版本控制**：对比版本号判断是否需要更新，下载新版后自动清理旧文件
- **mock 协议**：开发阶段使用 `mock://` URL，从 assets 读取脚本；生产环境替换为真实 URL 无需改代码
- **兜底机制**：scene 模块保留 assets 中的 JS 文件作为兜底——已下载用缓存，未下载用内置 asset

数据模型（`ScriptInfo.kt`）：
- `ScriptInfo` — 脚本元信息（id、name、version、url、requiredModules、icon、color、display）
- `ScriptManifest` — 脚本清单
- `CachedScript` — 缓存元数据（info + localPath + cachedAt）

### 运行流程

```
App 启动
  → 初始化 AndroidJSEngine（注册 WidgetModule）
  → 加载脚本清单（fetchManifest）
  → 展示脚本卡片列表（未下载 / 已缓存 / 有更新）
  → 用户点击"下载" → ScriptManager.downloadScript() → 缓存到本地
  → 用户点击"运行" → 根据 display 路由：
    → "dialog" → 执行脚本 → 解析 {title, message} → AlertDialog
    → "activity" → 启动 ScriptActivity → JS 通过 UIModule 驱动 UI
```

## 关键技术细节

- 所有 JSON 序列化使用 `kotlinx.serialization`（非 Gson/Moshi）
- DatabaseModule 使用原始 SQLiteDatabase（非 Room），JS 自行 CREATE TABLE 和查询
- WorkManager 定时更新 Widget（24 小时周期，低电量约束），优先使用缓存脚本
- 协程 + 结构化并发处理异步操作
- ProGuard/代码混淆在 release 构建中已禁用
- OkHttp 统一 30 秒超时（NetworkModule 和 ScriptManager）
- SwipeRefreshLayout 下拉刷新脚本清单
