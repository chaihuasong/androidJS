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

Clawdbot 是一个多模块 Android 工程（包名 `com.example.androidjs`），核心定位是**端侧 AI Agent**。在保留原有 WebView JS 执行引擎 + 原生模块桥接框架的基础上，新增了 AI 对话能力，让 AI 能调用手机原生能力作为工具，并将 JS 脚本作为 AI 可调用的"技能"。

**设计原则**：原生代码只提供通用能力（数据库、语音、Widget、UI、剪贴板、设备信息、通知、文件、闹钟），业务逻辑由 JS 脚本或 AI 对话承载。

技术栈：Min SDK 24, Compile SDK 35, Kotlin 2.1, AGP 8.7.3, Java 17。

## 模块结构

```
core/               → 核心 SDK 库（独立，无外部模块依赖）
  ├── engine/       → WebView JS 引擎（JSEngine、AndroidJSEngine、JSContext）
  ├── bridge/       → JS/原生桥接（BridgeDispatcher、NativeModule、BridgeMessage）
  ├── modules/      → 通用原生模块（log、storage、network、database、clipboard、device、notification、file、alarm）
  └── plugin/       → 插件接口（AndroidJSPlugin）

ai/                 → AI 服务抽象层（依赖 core）
  ├── service/      → AIService 接口 + ClaudeAIService 实现 + ApiKeyManager
  ├── model/        → 对话数据模型（Message、StreamEvent、ChatResponse 等）
  ├── tools/        → 工具系统（ToolRegistry、NativeModuleToolAdapter、ScriptToolAdapter、ToolOrchestrator）
  ├── conversation/ → 对话管理（ConversationManager、SystemPromptBuilder）
  └── persistence/  → Room 数据库（对话持久化）

scene-widget/       → Quran Widget 插件（JS 资源 + 原生桥接）
scene-accounting/   → 智能记账插件（JS 资源 + Room DB + 语音识别）

app/                → 主应用（组装所有模块）
  ├── demo/         → ClawdbotApplication、MainActivity
  ├── chat/         → ChatActivity、ChatViewModel、ChatMessageAdapter、ApiKeySetupDialog
  └── widget/       → ScriptWidgetProvider、WidgetUpdateWorker
```

**依赖关系**：`app` → `core` + `ai` + `scene-widget` + `scene-accounting`。`ai` → `core`。

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

### AI Agent 架构

```
用户 → ChatActivity → ChatViewModel → ToolOrchestrator
  → ClaudeAIService (SSE streaming) → Claude API
  → 如果 stop_reason="tool_use"
    → ToolRegistry.execute() → NativeModuleToolAdapter → NativeModule.invoke()
    → 组装 tool_result → 再次调用 AI
    → 循环直到 stop_reason="end_turn"
  → 流式文本输出到 UI
```

### 工具系统

每个 NativeModule 的每个 method 映射为一个独立的 Tool：
- 命名规则：`{moduleName}_{method}`（如 `storage_get`、`device_getTime`）
- `NativeModuleToolAdapter` 将 `NativeModule.invoke()` 包装为 `ToolExecutor.execute()`
- `ScriptToolAdapter` 将 JS 脚本包装为高级 Tool
- `ToolOrchestrator` 处理 AI ↔ Tool 执行循环（最多 10 轮）

### 内置模块

| 模块 | name | 方法 |
|------|------|------|
| LogModule | `log` | log |
| StorageModule | `storage` | get, set, remove, clear, getAll, has, keys |
| NetworkModule | `network` | get, post, put, delete |
| DatabaseModule | `database` | exec, query, listTables, describeTable |
| ClipboardModule | `clipboard` | getText, setText |
| DeviceInfoModule | `device` | getInfo, getTime, getConnectivity |
| NotificationModule | `notification` | show |
| FileModule | `file` | readText, writeText, listFiles, delete |
| AlarmModule | `alarm` | setAlarm, setReminder |

### 对话持久化

独立的 Room 数据库 `clawdbot_chat_db`（与 core 的 DatabaseModule 完全独立）：
- `ConversationEntity`：id, title, createdAt, updatedAt
- `MessageEntity`：id, conversationId, role, content, toolUseJson, timestamp

### API Key 管理

使用 `EncryptedSharedPreferences` 加密存储 Claude API Key。首次进入对话时弹出设置对话框。

## 关键技术细节

- 所有 JSON 序列化使用 `kotlinx.serialization`（非 Gson/Moshi）
- Claude API 通过 OkHttp SSE 实现流式输出
- Markwon 渲染 AI 回复中的 Markdown
- 工具定义使用 JSON Schema 格式（与 Claude tool_use API 兼容）
- 上下文窗口管理：估算 Token 数，保留最近消息不超过 100K tokens
- System Prompt 根据已注册工具动态生成
- FileModule 限制在 App 内部存储，防止路径遍历攻击
- DatabaseModule 表名参数经过正则清洗防止 SQL 注入
