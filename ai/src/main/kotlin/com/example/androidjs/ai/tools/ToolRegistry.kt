package com.example.androidjs.ai.tools

import android.util.Log
import com.example.androidjs.ai.model.ToolSpec
import kotlinx.serialization.json.JsonObject

/**
 * Central registry for all tools available to the AI.
 */
class ToolRegistry {

    private val executors = mutableMapOf<String, ToolExecutor>()

    fun register(executor: ToolExecutor) {
        executors[executor.definition.name] = executor
        Log.d(TAG, "Registered tool: ${executor.definition.name}")
    }

    fun unregister(name: String) {
        executors.remove(name)
    }

    fun getToolSpecs(): List<ToolSpec> {
        return executors.values.map { it.definition.toToolSpec() }
    }

    fun getToolNames(): Set<String> = executors.keys.toSet()

    fun getDefinitions(): List<ToolDefinition> {
        return executors.values.map { it.definition }
    }

    suspend fun execute(name: String, input: JsonObject): String {
        val executor = executors[name]
            ?: return """{"error": "Tool not found: $name"}"""
        return try {
            executor.execute(input)
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: $name", e)
            """{"error": "${e.message?.replace("\"", "\\\"")}"}"""
        }
    }

    companion object {
        private const val TAG = "ToolRegistry"
    }
}
