package com.example.androidjs.ai.tools

import com.example.androidjs.core.bridge.NativeModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

/**
 * Adapts a NativeModule method into a ToolExecutor.
 * Each method of a NativeModule becomes a separate tool.
 */
class NativeModuleToolAdapter(
    private val module: NativeModule,
    private val method: String,
    override val definition: ToolDefinition
) : ToolExecutor {

    override suspend fun execute(input: JsonObject): String {
        return withContext(Dispatchers.IO) {
            val result = module.invoke(method, input.toString())
            result ?: """{"success": true}"""
        }
    }

    companion object {
        /**
         * Create a tool name from module name and method.
         */
        fun toolName(moduleName: String, method: String): String {
            return "${moduleName}_$method"
        }
    }
}
