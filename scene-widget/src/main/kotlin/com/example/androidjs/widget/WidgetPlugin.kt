package com.example.androidjs.widget

import android.content.Context
import com.example.androidjs.core.bridge.NativeModule
import com.example.androidjs.core.plugin.AndroidJSPlugin

/**
 * Plugin that registers all Quran Widget related native modules.
 */
class WidgetPlugin(private val context: Context) : AndroidJSPlugin {

    override val name: String = "quran-widget"

    private val widgetModule by lazy { WidgetNativeModule(context) }

    override fun getModules(): List<NativeModule> {
        return listOf(widgetModule)
    }

    override fun onRegistered() {
        // Widget plugin registered
    }

    override fun onDestroy() {
        // Cleanup if needed
    }
}
