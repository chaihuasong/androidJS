package com.example.androidjs.core.bridge

import android.util.Log
import com.example.androidjs.core.bridge.BridgeResponse.Companion.toJson

/**
 * Routes JS bridge calls to the appropriate NativeModule.
 */
class BridgeDispatcher {

    private val modules = mutableMapOf<String, NativeModule>()

    fun registerModule(module: NativeModule) {
        modules[module.name] = module
        Log.d(TAG, "Registered module: ${module.name}")
    }

    fun unregisterModule(name: String) {
        modules.remove(name)
    }

    fun hasModule(name: String): Boolean = modules.containsKey(name)

    fun getModuleNames(): Set<String> = modules.keys.toSet()

    /**
     * Dispatch a synchronous call from JS to the appropriate native module.
     * Returns JSON response string.
     */
    fun dispatch(moduleName: String, method: String, argsJson: String): String {
        val module = modules[moduleName]
            ?: return BridgeResponse.error("Module not found: $moduleName").toJson()

        return try {
            val result = module.invoke(method, argsJson)
            BridgeResponse.success(result).toJson()
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching $moduleName.$method", e)
            BridgeResponse.error("${e.javaClass.simpleName}: ${e.message}").toJson()
        }
    }

    /**
     * Dispatch an asynchronous call from JS to the appropriate native module.
     */
    fun dispatchAsync(
        moduleName: String,
        method: String,
        argsJson: String,
        callback: (String) -> Unit
    ) {
        val module = modules[moduleName]
        if (module == null) {
            callback(BridgeResponse.error("Module not found: $moduleName").toJson())
            return
        }

        try {
            module.invokeAsync(method, argsJson) { result ->
                callback(BridgeResponse.success(result).toJson())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dispatching async $moduleName.$method", e)
            callback(BridgeResponse.error("${e.javaClass.simpleName}: ${e.message}").toJson())
        }
    }

    companion object {
        private const val TAG = "BridgeDispatcher"
    }
}
