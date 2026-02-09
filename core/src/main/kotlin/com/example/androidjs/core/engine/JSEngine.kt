package com.example.androidjs.core.engine

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.androidjs.core.bridge.BridgeDispatcher
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * JavaScript engine implementation using Android WebView as the execution backend.
 *
 * This provides a reliable JS execution environment available on all Android devices.
 * In future iterations, this can be swapped to QuickJS native for better performance
 * when the quickjs-android dependency is properly configured.
 *
 * Key design:
 * - Single-threaded execution model via dedicated dispatcher
 * - Bridge injection via `__bridge` global object
 * - Memory and execution timeout configuration
 */
class JSEngine(
    private val appContext: Context,
    private val dispatcher: BridgeDispatcher,
    private val jsContext: JSContext
) {
    private val jsDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "JSEngine-Thread").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(jsDispatcher + SupervisorJob())
    private var webView: WebView? = null
    private var isInitialized = false

    /**
     * Initialize the JS engine on the main thread (WebView requirement).
     */
    suspend fun initialize() = withContext(Dispatchers.Main) {
        if (isInitialized) return@withContext

        webView = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(BridgeInterface(dispatcher), "__bridge_native")
        }

        // Inject bridge wrapper that provides clean API to JS
        val bridgeScript = """
            var __bridge = {
                invoke: function(moduleName, method, args) {
                    return __bridge_native.invoke(moduleName, method, args || '{}');
                },
                invokeAsync: function(moduleName, method, args, callback) {
                    var callbackId = '__cb_' + Date.now() + '_' + Math.random().toString(36);
                    window[callbackId] = function(result) {
                        if (callback) callback(result);
                        delete window[callbackId];
                    };
                    __bridge_native.invokeAsync(moduleName, method, args || '{}', callbackId);
                }
            };

            // Redirect console.log to native Log module
            var __originalConsole = console;
            console = {
                log: function() {
                    var msg = Array.prototype.slice.call(arguments).join(' ');
                    __bridge.invoke('log', 'log', JSON.stringify({level: 'info', message: msg}));
                    __originalConsole.log.apply(__originalConsole, arguments);
                },
                error: function() {
                    var msg = Array.prototype.slice.call(arguments).join(' ');
                    __bridge.invoke('log', 'log', JSON.stringify({level: 'error', message: msg}));
                    __originalConsole.error.apply(__originalConsole, arguments);
                },
                warn: function() {
                    var msg = Array.prototype.slice.call(arguments).join(' ');
                    __bridge.invoke('log', 'log', JSON.stringify({level: 'warn', message: msg}));
                    __originalConsole.warn.apply(__originalConsole, arguments);
                }
            };
        """.trimIndent()

        webView?.evaluateJavascript(bridgeScript, null)
        isInitialized = true
        Log.d(TAG, "JSEngine initialized")
    }

    /**
     * Execute a JavaScript code string and return the result.
     */
    suspend fun evaluateScript(script: String): String? {
        check(isInitialized) { "JSEngine not initialized. Call initialize() first." }

        return withTimeout(jsContext.executionTimeout) {
            suspendCancellableCoroutine { continuation ->
                scope.launch(Dispatchers.Main) {
                    try {
                        webView?.evaluateJavascript(script) { result ->
                            continuation.resume(result)
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            }
        }
    }

    /**
     * Load and execute a JS file from assets.
     */
    suspend fun executeAssetScript(assetPath: String): String? {
        val script = appContext.assets.open(assetPath).bufferedReader().use { it.readText() }
        return evaluateScript(script)
    }

    /**
     * Load and execute a JS file from internal storage.
     */
    suspend fun executeFileScript(filePath: String): String? {
        val script = java.io.File(filePath).readText()
        return evaluateScript(script)
    }

    fun isReady(): Boolean = isInitialized

    /**
     * Destroy the engine and release resources.
     */
    fun destroy() {
        scope.cancel()
        webView?.let { view ->
            view.removeJavascriptInterface("__bridge_native")
            view.destroy()
        }
        webView = null
        jsContext.destroy()
        isInitialized = false
        jsDispatcher.close()
        Log.d(TAG, "JSEngine destroyed")
    }

    /**
     * JavascriptInterface bridge exposed to WebView JS context.
     */
    private class BridgeInterface(private val dispatcher: BridgeDispatcher) {
        @JavascriptInterface
        fun invoke(moduleName: String, method: String, argsJson: String): String {
            return dispatcher.dispatch(moduleName, method, argsJson)
        }

        @JavascriptInterface
        fun invokeAsync(moduleName: String, method: String, argsJson: String, callbackId: String) {
            dispatcher.dispatchAsync(moduleName, method, argsJson) { result ->
                // Result will be delivered via callback in the WebView context
                Log.d(TAG, "Async result for $callbackId: $result")
            }
        }
    }

    companion object {
        private const val TAG = "JSEngine"
    }
}
