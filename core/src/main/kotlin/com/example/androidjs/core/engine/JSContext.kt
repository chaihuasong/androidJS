package com.example.androidjs.core.engine

/**
 * Abstraction over a JavaScript execution context.
 * Provides sandboxed JS evaluation with configurable limits.
 */
class JSContext(
    val memoryLimit: Long = DEFAULT_MEMORY_LIMIT,
    val executionTimeout: Long = DEFAULT_EXECUTION_TIMEOUT
) {
    private var isDestroyed = false
    private val globalProperties = mutableMapOf<String, Any>()

    fun setGlobalProperty(name: String, value: Any) {
        check(!isDestroyed) { "JSContext has been destroyed" }
        globalProperties[name] = value
    }

    fun getGlobalProperties(): Map<String, Any> = globalProperties.toMap()

    fun destroy() {
        isDestroyed = true
        globalProperties.clear()
    }

    fun isActive(): Boolean = !isDestroyed

    companion object {
        const val DEFAULT_MEMORY_LIMIT = 16L * 1024 * 1024  // 16MB
        const val DEFAULT_EXECUTION_TIMEOUT = 5000L           // 5 seconds
    }
}
