package com.example.androidjs.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.work.*
import com.example.androidjs.R
import com.example.androidjs.core.AndroidJSEngine
import com.example.androidjs.core.script.ScriptManager
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AppWidgetProvider for script-driven widgets.
 * Runs JS engine directly in onUpdate to guarantee content is shown,
 * and schedules periodic Worker for background refresh.
 */
class ScriptWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "onUpdate called for ${appWidgetIds.size} widgets")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = executeWidgetScript(context)
                if (result != null) {
                    applyResultToWidgets(context, appWidgetManager, appWidgetIds, result)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update widget via JS", e)
            } finally {
                pendingResult.finish()
            }
        }

        schedulePeriodicUpdate(context)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        Log.d(TAG, "Widget enabled, scheduling periodic updates")
        schedulePeriodicUpdate(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        Log.d(TAG, "Widget disabled, cancelling periodic updates")
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun schedulePeriodicUpdate(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            24, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    companion object {
        private const val TAG = "ScriptWidgetProvider"
        private const val WORK_NAME = "script_widget_periodic_update"

        /**
         * Run the quran_widget JS script and return raw result.
         */
        suspend fun executeWidgetScript(context: Context): String? {
            val engine = AndroidJSEngine.Builder(context.applicationContext)
                .setExecutionTimeout(10_000)
                .build()

            engine.initialize()

            val scriptManager = ScriptManager(context.applicationContext)
            val cachedPath = scriptManager.getCachedScriptPath("quran_widget")
            val result = if (cachedPath != null) {
                engine.executeFileScript(cachedPath)
            } else {
                engine.executeAssetScript("js/quran_widget.js")
            }
            Log.d(TAG, "JS result: $result")

            engine.destroy()
            return result
        }

        /**
         * Parse JS result JSON and apply text fields to widget RemoteViews.
         * WebView evaluateJavascript wraps string results in quotes.
         */
        fun applyResultToWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            rawResult: String
        ) {
            val jsonStr = if (rawResult.startsWith("\"") && rawResult.endsWith("\"")) {
                rawResult.substring(1, rawResult.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
            } else {
                rawResult
            }

            val json = JSONObject(jsonStr)
            val views = RemoteViews(context.packageName, R.layout.widget_script)

            if (json.has("text_line_1")) {
                views.setTextViewText(R.id.text_line_1, json.getString("text_line_1"))
            }
            if (json.has("text_line_2")) {
                views.setTextViewText(R.id.text_line_2, json.getString("text_line_2"))
            }
            if (json.has("text_line_3")) {
                views.setTextViewText(R.id.text_line_3, json.getString("text_line_3"))
            }
            if (json.has("text_line_4")) {
                views.setTextViewText(R.id.text_line_4, json.getString("text_line_4"))
            }

            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
            Log.d(TAG, "Updated ${appWidgetIds.size} widget(s)")
        }
    }
}
