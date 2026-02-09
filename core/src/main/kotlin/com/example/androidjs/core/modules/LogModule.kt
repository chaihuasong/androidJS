package com.example.androidjs.core.modules

import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Built-in module for logging from JS to Android Logcat.
 */
class LogModule : NativeModule {

    override val name: String = "log"

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "log" -> {
                val args = json.decodeFromString<LogArgs>(argsJson)
                when (args.level) {
                    "error" -> Log.e(JS_TAG, args.message)
                    "warn" -> Log.w(JS_TAG, args.message)
                    "debug" -> Log.d(JS_TAG, args.message)
                    else -> Log.i(JS_TAG, args.message)
                }
                null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    @Serializable
    private data class LogArgs(
        val level: String = "info",
        val message: String = ""
    )

    companion object {
        private const val TAG = "LogModule"
        private const val JS_TAG = "JS"
    }
}
