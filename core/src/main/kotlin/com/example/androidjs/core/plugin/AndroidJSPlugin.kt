package com.example.androidjs.core.plugin

import com.example.androidjs.core.bridge.NativeModule

/**
 * Plugin interface for extending AndroidJS with scene-specific functionality.
 * Each business scene (widget, accounting, etc.) implements this interface.
 */
interface AndroidJSPlugin {
    /** Unique identifier for this plugin */
    val name: String

    /** List of native modules this plugin provides */
    fun getModules(): List<NativeModule>

    /** Called when the plugin is registered with the engine */
    fun onRegistered() {}

    /** Called when the engine is being destroyed */
    fun onDestroy() {}
}
