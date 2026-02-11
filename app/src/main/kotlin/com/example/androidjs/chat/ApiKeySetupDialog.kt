package com.example.androidjs.chat

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout

object ApiKeySetupDialog {

    fun show(context: Context, onApiKeySet: (String) -> Unit) {
        val editText = EditText(context).apply {
            hint = "sk-..."
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }

        val container = FrameLayout(context).apply {
            val padding = (16 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(editText)
        }

        AlertDialog.Builder(context)
            .setTitle("Configure API Key")
            .setMessage("Enter your Clawbot API key to start chatting.")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val key = editText.text.toString().trim()
                if (key.isNotEmpty()) {
                    onApiKeySet(key)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
