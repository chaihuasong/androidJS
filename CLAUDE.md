# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew installDebug           # Build and install on connected device
./gradlew :core:assembleDebug    # Build single module
./gradlew lint                   # Run lint checks
./gradlew test                   # Run unit tests (none exist yet)
./gradlew connectedAndroidTest   # Run instrumented tests
./gradlew clean                  # Clean build artifacts
```

## Project Structure

Multi-module Android project (`com.example.androidjs`). Min SDK 24, Compile SDK 35, Kotlin 2.1, AGP 8.7.3, Java 17.

```
core/           → Standalone SDK library. WebView-based JS engine, bridge system, built-in modules
scene-widget/   → Quran daily verse widget plugin (WorkManager for 24h updates)
scene-accounting/ → Smart accounting plugin (Room DB, voice input, Chinese NLP for transactions)
app/            → Demo application wiring all plugins together
```

Dependencies flow: `app` → `core`, `scene-widget`, `scene-accounting`. Both scene modules depend on `core`.

## Architecture

### JS/Native Bridge

JavaScript runs in a WebView (not QuickJS). Communication flows through a JSON bridge using `kotlinx.serialization`:

```
JS: __bridge.invoke(module, method, argsJson)
  → WebView @JavascriptInterface ("__bridge_native")
  → BridgeDispatcher.dispatch()
  → NativeModule.invoke()
  → BridgeResponse JSON returned to JS
```

Key bridge classes in `core/src/main/kotlin/.../core/bridge/`:
- `BridgeDispatcher` — routes calls to registered `NativeModule` instances by name
- `NativeModule` — interface: `name`, `invoke(method, args)`, `invokeAsync(method, args, callback)`
- `BridgeMessage.kt` — `BridgeRequest`/`BridgeResponse` data classes

### Engine

`AndroidJSEngine` (Builder pattern) is the main entry point. Configured with plugins, memory limits, and execution timeout. Must call `initialize()` before executing scripts.

`JSEngine` wraps a WebView on a dedicated thread (`JSEngine-Thread`). Scripts can be loaded from assets, files, or raw strings. Console calls are redirected to `LogModule`.

### Plugin System

`AndroidJSPlugin` interface: `name`, `getModules()`, `onRegistered()`, `onDestroy()`. Each plugin provides `NativeModule` instances that get registered with the `BridgeDispatcher`.

**Built-in modules** (in core): `log`, `storage` (SharedPreferences), `network` (OkHttp GET/POST)
**Widget plugin modules**: `widget` (triggers verse update)
**Accounting plugin modules**: `voice` (speech recognition), `accountingStorage` (Room CRUD)

### JS Assets

- `scene-widget/src/main/assets/js/quran_widget.js` — Daily verse selection from 30 embedded verses
- `scene-accounting/src/main/assets/js/accounting.js` — Chinese natural language transaction parsing (amount extraction, category detection, expense/income classification)

Assets from library modules merge into the app at build time.

## Key Technical Details

- All JSON serialization uses `kotlinx.serialization` (not Gson/Moshi)
- Room database "accounting_db" v1 with `TransactionEntity`
- WorkManager for widget updates (24h period, battery-not-low constraint)
- Coroutines with structured concurrency for async operations
- ProGuard/minification is disabled in release builds
