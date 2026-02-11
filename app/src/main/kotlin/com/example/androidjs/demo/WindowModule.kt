package com.example.androidjs.demo

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.example.androidjs.core.bridge.NativeModule
import org.json.JSONObject

class WindowModule(private val activity: Activity) : NativeModule {

    override val name = "window"
    private var currentDialog: Dialog? = null

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "show" -> show(argsJson)
            "dismiss" -> dismiss()
            else -> null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun show(argsJson: String): String {
        val html = JSONObject(argsJson).getString("html")
        activity.runOnUiThread {
            if (activity.isFinishing) return@runOnUiThread
            currentDialog?.dismiss()

            val webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                        val level = when (msg.messageLevel()) {
                            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
                            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                            else -> Log.DEBUG
                        }
                        Log.println(level, "WindowModule", "${msg.message()} [${msg.sourceId()}:${msg.lineNumber()}]")
                        return true
                    }
                }
            }

            val dialog = Dialog(activity, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            dialog.setContentView(webView)
            dialog.setOnDismissListener { webView.destroy() }
            webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
            dialog.show()
            currentDialog = dialog
        }
        return """{"success":true}"""
    }

    private fun dismiss(): String {
        activity.runOnUiThread { currentDialog?.dismiss() }
        return """{"success":true}"""
    }

    override fun destroy() {
        activity.runOnUiThread {
            currentDialog?.dismiss()
            currentDialog = null
        }
    }
}
