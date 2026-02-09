package com.example.androidjs.accounting

import android.content.Context
import com.example.androidjs.core.bridge.NativeModule
import com.example.androidjs.core.plugin.AndroidJSPlugin

/**
 * Plugin that registers all accounting-related native modules.
 */
class AccountingPlugin(private val context: Context) : AndroidJSPlugin {

    override val name: String = "smart-accounting"

    private val voiceModule by lazy { VoiceNativeModule(context) }
    private val storageModule by lazy { AccountingStorageModule(context) }

    override fun getModules(): List<NativeModule> {
        return listOf(voiceModule, storageModule)
    }

    override fun onRegistered() {
        // Accounting plugin registered
    }

    override fun onDestroy() {
        voiceModule.destroy()
    }
}
