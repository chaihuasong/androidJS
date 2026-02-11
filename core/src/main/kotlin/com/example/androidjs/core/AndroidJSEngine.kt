package com.example.androidjs.core

import android.content.Context
import android.util.Log
import com.example.androidjs.core.bridge.BridgeDispatcher
import com.example.androidjs.core.engine.JSContext
import com.example.androidjs.core.engine.JSEngine
import com.example.androidjs.core.bridge.NativeModule

/**
 * Public API entry point for the AndroidJS SDK.
 * Use [Builder] to configure and create an instance.
 *
 * Example:
 * ```kotlin
 * val engine = AndroidJSEngine.Builder(context)
 *     .addModule(MyNativeModule())
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
    private val modules: List<NativeModule>,
    private val memoryLimit: Long,
    private val executionTimeout: Long
) {
    private val dispatcher = BridgeDispatcher()
    private val jsContext = JSContext(memoryLimit, executionTimeout)
    private val jsEngine = JSEngine(appContext, dispatcher, jsContext)

    /**
     * Initialize the engine and register all modules.
     * Must be called before executing any scripts.
     */
    suspend fun initialize() {
        // Register modules
        for (module in modules) {
            dispatcher.registerModule(module)
            Log.d(TAG, "Module registered: ${module.name}")
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
        for (module in modules) {
            module.destroy()
        }
        jsEngine.destroy()
        Log.d(TAG, "AndroidJSEngine destroyed")
    }

    class Builder(private val context: Context) {
        private val modules = mutableListOf<NativeModule>()
        private var memoryLimit = JSContext.DEFAULT_MEMORY_LIMIT
        private var executionTimeout = JSContext.DEFAULT_EXECUTION_TIMEOUT
        fun addModule(module: NativeModule) = apply {
            modules.add(module)
        }

        fun setMemoryLimit(bytes: Long) = apply {
            memoryLimit = bytes
        }

        fun setExecutionTimeout(millis: Long) = apply {
            executionTimeout = millis
        }

        fun build(): AndroidJSEngine {
            return AndroidJSEngine(
                appContext = context.applicationContext,
                modules = modules.toList(),
                memoryLimit = memoryLimit,
                executionTimeout = executionTimeout
            )
        }
    }

    companion object {
        private const val TAG = "AndroidJSEngine"
    }
}
