package com.example.androidjs.core.bridge

/**
 * Interface for native modules that can be invoked from JavaScript.
 * Each module exposes a set of methods callable via the bridge.
 */
interface NativeModule {
    /** Unique name used to identify this module from JS side */
    val name: String

    /** Synchronous invocation of a module method */
    fun invoke(method: String, argsJson: String): String?

    /** Asynchronous invocation with callback for result */
    fun invokeAsync(method: String, argsJson: String, callback: (String) -> Unit) {
        // Default implementation delegates to synchronous invoke
        val result = invoke(method, argsJson)
        callback(result ?: "{}")
    }

    /** Called when the engine is being destroyed. Override to release resources. */
    fun destroy() {}
}
