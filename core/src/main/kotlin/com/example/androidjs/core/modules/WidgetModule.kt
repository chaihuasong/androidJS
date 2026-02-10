package com.example.androidjs.core.modules

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generic widget update module.
 * JS passes field name → text mappings; view IDs are resolved by name via getIdentifier().
 */
class WidgetModule(
    private val context: Context,
    private val providerClass: Class<*>,
    private val layoutResId: Int
) : NativeModule {

    override val name: String = "widget"

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "updateText" -> {
                val fields = json.decodeFromString<JsonObject>(argsJson)
                updateText(fields)
                null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun updateText(fields: JsonObject) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, providerClass)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, layoutResId)
            for ((key, value) in fields) {
                val viewId = context.resources.getIdentifier(key, "id", context.packageName)
                if (viewId != 0) {
                    views.setTextViewText(viewId, value.jsonPrimitive.content)
                } else {
                    Log.w(TAG, "View ID not found for key: $key")
                }
            }
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        Log.d(TAG, "Widget updated with ${fields.size} fields")
    }

    companion object {
        private const val TAG = "WidgetModule"
    }
}
