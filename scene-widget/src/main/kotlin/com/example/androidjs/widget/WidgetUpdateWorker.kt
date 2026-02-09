package com.example.androidjs.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.androidjs.core.AndroidJSEngine
import kotlinx.serialization.json.Json

/**
 * WorkManager Worker that executes the Quran JS script
 * and updates the widget with the daily verse.
 */
class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting widget update work")

        return try {
            val widgetModule = WidgetNativeModule(applicationContext)
            val plugin = WidgetPlugin(applicationContext)

            val engine = AndroidJSEngine.Builder(applicationContext)
                .addPlugin(plugin)
                .setExecutionTimeout(10_000)
                .build()

            engine.initialize()

            val result = engine.executeAssetScript("js/quran_widget.js")
            Log.d(TAG, "JS result: $result")

            if (result != null) {
                // Clean the result string (WebView returns quoted JSON strings)
                val cleanResult = result.trim().let {
                    if (it.startsWith("\"") && it.endsWith("\"")) {
                        // Unescape the JSON string
                        it.substring(1, it.length - 1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                    } else it
                }

                val verseData = json.decodeFromString<WidgetNativeModule.QuranVerseData>(cleanResult)
                widgetModule.updateWidget(verseData)
            }

            engine.destroy()

            Log.d(TAG, "Widget update completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Widget update failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "WidgetUpdateWorker"
    }
}
