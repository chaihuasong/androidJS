package com.example.androidjs.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Native module providing widget update capabilities to JS.
 */
class WidgetNativeModule(private val context: Context) : NativeModule {

    override val name: String = "widget"

    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "updateQuranWidget" -> {
                val data = json.decodeFromString<QuranVerseData>(argsJson)
                updateWidget(data)
                null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    fun updateWidget(data: QuranVerseData) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, QuranWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

        for (widgetId in widgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quran)
            views.setTextViewText(R.id.text_arabic, data.arabic)
            views.setTextViewText(R.id.text_translation, data.translation)
            views.setTextViewText(R.id.text_reference, data.reference)
            views.setTextViewText(R.id.text_date, data.date)
            appWidgetManager.updateAppWidget(widgetId, views)
        }

        Log.d(TAG, "Widget updated with verse: ${data.reference}")
    }

    @Serializable
    data class QuranVerseData(
        val arabic: String = "",
        val translation: String = "",
        val surah: String = "",
        val ayah: String = "",
        val reference: String = "",
        val date: String = ""
    )

    companion object {
        private const val TAG = "WidgetNativeModule"
    }
}
