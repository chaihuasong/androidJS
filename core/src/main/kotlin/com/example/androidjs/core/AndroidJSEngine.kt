package com.example.androidjs.core

import android.content.Context
import android.util.Log
import com.example.androidjs.core.bridge.BridgeDispatcher
import com.example.androidjs.core.engine.JSContext
import com.example.androidjs.core.engine.JSEngine
import com.example.androidjs.core.modules.LogModule
import com.example.androidjs.core.modules.NetworkModule
import com.example.androidjs.core.modules.StorageModule
import com.example.androidjs.core.plugin.AndroidJSPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Public API entry point for the AndroidJS SDK.
 * Use [Builder] to configure and create an instance.
 *
 * Example:
 * ```kotlin
 * val engine = AndroidJSEngine.Builder(context)
 *     .addPlugin(widgetPlugin)
 *     .setMemoryLimit(16 * 1024 * 1024)
 *     .setExecutionTimeout(5000)
 *     .build()
 *
 * engine.initialize()
 * val result = engine.executeScript("1 + 1")
 * ```
 */
class AndroidJSEngine private constructor(
    private val appContext: Context,
    private val plugins: List<AndroidJSPlugin>,
    private val memoryLimit: Long,
    private val executionTimeout: Long,
    private val enableBuiltinModules: Boolean
) {
    private val dispatcher = BridgeDispatcher()
    private val jsContext = JSContext(memoryLimit, executionTimeout)
    private val jsEngine = JSEngine(appContext, dispatcher, jsContext)

    /**
     * Initialize the engine and register all modules.
     * Must be called before executing any scripts.
     */
    suspend fun initialize() {
        // Register built-in modules
        if (enableBuiltinModules) {
            dispatcher.registerModule(LogModule())
            dispatcher.registerModule(StorageModule(appContext))
            dispatcher.registerModule(NetworkModule())
        }

        // Register plugin modules
        for (plugin in plugins) {
            for (module in plugin.getModules()) {
                dispatcher.registerModule(module)
            }
            plugin.onRegistered()
            Log.d(TAG, "Plugin registered: ${plugin.name}")
        }

        // Initialize the JS engine
        jsEngine.initialize()
        Log.d(TAG, "AndroidJSEngine initialized with ${dispatcher.getModuleNames().size} modules")
    }

    /**
     * Execute a JavaScript code string.
     * @return The evaluation result as a string, or null.
     */
    suspend fun executeScript(script: String): String? {
        return jsEngine.evaluateScript(script)
    }

    /**
     * Execute a JavaScript file from the assets folder.
     * @param assetPath Path relative to assets/ (e.g., "js/quran_widget.js")
     */
    suspend fun executeAssetScript(assetPath: String): String? {
        return jsEngine.executeAssetScript(assetPath)
    }

    /**
     * Execute a JavaScript file from internal storage.
     */
    suspend fun executeFileScript(filePath: String): String? {
        return jsEngine.executeFileScript(filePath)
    }

    /**
     * Get the bridge dispatcher for direct module access.
     */
    fun getBridgeDispatcher(): BridgeDispatcher = dispatcher

    /**
     * Check if the engine is ready to execute scripts.
     */
    fun isReady(): Boolean = jsEngine.isReady()

    /**
     * Destroy the engine and release all resources.
     */
    fun destroy() {
        for (plugin in plugins) {
            plugin.onDestroy()
        }
        jsEngine.destroy()
        Log.d(TAG, "AndroidJSEngine destroyed")
    }

    class Builder(private val context: Context) {
        private val plugins = mutableListOf<AndroidJSPlugin>()
        private var memoryLimit = JSContext.DEFAULT_MEMORY_LIMIT
        private var executionTimeout = JSContext.DEFAULT_EXECUTION_TIMEOUT
        private var enableBuiltinModules = true

        fun addPlugin(plugin: AndroidJSPlugin) = apply {
            plugins.add(plugin)
        }

        fun setMemoryLimit(bytes: Long) = apply {
            memoryLimit = bytes
        }

        fun setExecutionTimeout(millis: Long) = apply {
            executionTimeout = millis
        }

        fun setEnableBuiltinModules(enable: Boolean) = apply {
            enableBuiltinModules = enable
        }

        fun build(): AndroidJSEngine {
            return AndroidJSEngine(
                appContext = context.applicationContext,
                plugins = plugins.toList(),
                memoryLimit = memoryLimit,
                executionTimeout = executionTimeout,
                enableBuiltinModules = enableBuiltinModules
            )
        }
    }

    companion object {
        private const val TAG = "AndroidJSEngine"
    }
}
