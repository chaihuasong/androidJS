package com.example.androidjs.core.modules

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class ClipboardModule(context: Context) : NativeModule {

    override val name: String = "clipboard"

    private val clipboardManager =
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "getText" -> {
                val clip = clipboardManager.primaryClip
                val text = clip?.getItemAt(0)?.text?.toString()
                json.encodeToString(ClipTextResult.serializer(), ClipTextResult(text))
            }
            "setText" -> {
                val args = json.decodeFromString<SetTextArgs>(argsJson)
                val clip = ClipData.newPlainText("Clawdbot", args.text)
                clipboardManager.setPrimaryClip(clip)
                """{"success": true}"""
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    @Serializable
    private data class SetTextArgs(val text: String)

    @Serializable
    private data class ClipTextResult(val text: String?)

    companion object {
        private const val TAG = "ClipboardModule"
    }
}
