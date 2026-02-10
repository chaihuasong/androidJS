package com.example.androidjs.widget

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.androidjs.R
import com.example.androidjs.core.AndroidJSEngine
import com.example.androidjs.core.modules.WidgetModule
import com.example.androidjs.core.script.ScriptManager

/**
 * WorkManager Worker that executes the Quran JS script
 * and updates the widget with the daily verse.
 * The JS script itself calls widget.updateText() to update the widget.
 */
class WidgetUpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting widget update work")

        return try {
            val widgetModule = WidgetModule(
                applicationContext,
                ScriptWidgetProvider::class.java,
                R.layout.widget_script
            )

            val engine = AndroidJSEngine.Builder(applicationContext)
                .addModule(widgetModule)
                .setExecutionTimeout(10_000)
                .build()

            engine.initialize()

            val scriptManager = ScriptManager(applicationContext)
            val cachedPath = scriptManager.getCachedScriptPath("quran_widget")
            val result = if (cachedPath != null) {
                engine.executeFileScript(cachedPath)
            } else {
                engine.executeAssetScript("js/quran_widget.js")
            }
            Log.d(TAG, "JS result: $result")

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
