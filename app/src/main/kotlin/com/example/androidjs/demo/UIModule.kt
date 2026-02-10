package com.example.androidjs.demo

import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import com.example.androidjs.core.bridge.NativeModule
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * NativeModule that allows JS to drive the ScriptActivity UI.
 */
class UIModule(private val activity: AppCompatActivity) : NativeModule {

    override val name: String = "ui"

    private val json = Json { ignoreUnknownKeys = true }

    var voiceLocale: String = "en-US"
        private set
    var showVoice: Boolean = false
        private set

    private var listAdapter: ScriptListAdapter? = null

    fun setListAdapter(adapter: ScriptListAdapter) {
        listAdapter = adapter
    }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "configure" -> {
                val args = json.decodeFromString<ConfigureArgs>(argsJson)
                configure(args)
                null
            }
            "showResult" -> {
                val args = json.decodeFromString<ShowResultArgs>(argsJson)
                showResult(args)
                null
            }
            "updateList" -> {
                val args = json.decodeFromString<UpdateListArgs>(argsJson)
                updateList(args)
                null
            }
            "updateSummary" -> {
                val args = json.decodeFromString<UpdateSummaryArgs>(argsJson)
                updateSummary(args)
                null
            }
            "clearInput" -> {
                clearInput()
                null
            }
            "showToast" -> {
                val args = json.decodeFromString<ToastArgs>(argsJson)
                showToast(args.message)
                null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun configure(args: ConfigureArgs) {
        voiceLocale = args.voiceLocale
        showVoice = args.showVoice
        activity.runOnUiThread {
            activity.findViewById<TextView>(R.id.text_title)?.text = args.title
            activity.findViewById<EditText>(R.id.edit_input)?.hint = args.inputHint
            activity.findViewById<MaterialButton>(R.id.btn_submit)?.text = args.submitText
            activity.findViewById<ImageButton>(R.id.btn_voice)?.visibility =
                if (args.showVoice) View.VISIBLE else View.GONE

            // Configure summary labels
            val labels = args.summaryLabels
            val colors = args.summaryColors
            if (labels.size >= 2) {
                activity.findViewById<TextView>(R.id.text_summary_label_1)?.text = labels[0]
                activity.findViewById<TextView>(R.id.text_summary_label_2)?.text = labels[1]
            }
            if (colors.size >= 2) {
                try {
                    activity.findViewById<TextView>(R.id.text_summary_value_1)
                        ?.setTextColor(android.graphics.Color.parseColor(colors[0]))
                    activity.findViewById<TextView>(R.id.text_summary_value_2)
                        ?.setTextColor(android.graphics.Color.parseColor(colors[1]))
                } catch (_: Exception) {}
            }
        }
    }

    private fun showResult(args: ShowResultArgs) {
        activity.runOnUiThread {
            val cardResult = activity.findViewById<MaterialCardView>(R.id.card_result) ?: return@runOnUiThread
            val textResult = activity.findViewById<TextView>(R.id.text_result) ?: return@runOnUiThread
            textResult.text = args.text
            cardResult.visibility = View.VISIBLE
            if (args.success) {
                cardResult.setCardBackgroundColor(
                    ContextCompat.getColor(activity, android.R.color.white)
                )
                textResult.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
            } else {
                cardResult.setCardBackgroundColor(
                    ContextCompat.getColor(activity, android.R.color.white)
                )
                textResult.setTextColor(android.graphics.Color.parseColor("#C62828"))
            }
        }
    }

    private fun updateList(args: UpdateListArgs) {
        activity.runOnUiThread {
            val recycler = activity.findViewById<RecyclerView>(R.id.recycler_list)
            val textEmpty = activity.findViewById<TextView>(R.id.text_empty)
            if (args.items.isEmpty()) {
                recycler?.visibility = View.GONE
                textEmpty?.visibility = View.VISIBLE
            } else {
                recycler?.visibility = View.VISIBLE
                textEmpty?.visibility = View.GONE
            }
            listAdapter?.submitList(args.items)
        }
    }

    private fun updateSummary(args: UpdateSummaryArgs) {
        activity.runOnUiThread {
            val values = args.values
            if (values.isNotEmpty()) {
                activity.findViewById<TextView>(R.id.text_summary_value_1)?.text = values[0]
            }
            if (values.size >= 2) {
                activity.findViewById<TextView>(R.id.text_summary_value_2)?.text = values[1]
            }
        }
    }

    private fun clearInput() {
        activity.runOnUiThread {
            activity.findViewById<EditText>(R.id.edit_input)?.text?.clear()
        }
    }

    private fun showToast(message: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }
    }

    @Serializable
    data class ConfigureArgs(
        val title: String = "",
        val inputHint: String = "",
        val submitText: String = "",
        val showVoice: Boolean = false,
        val voiceLocale: String = "en-US",
        val summaryLabels: List<String> = emptyList(),
        val summaryColors: List<String> = emptyList()
    )

    @Serializable
    data class ShowResultArgs(
        val text: String = "",
        val success: Boolean = true
    )

    @Serializable
    data class UpdateListArgs(
        val items: List<ScriptListItem> = emptyList()
    )

    @Serializable
    data class UpdateSummaryArgs(
        val values: List<String> = emptyList()
    )

    @Serializable
    data class ToastArgs(val message: String = "")

    companion object {
        private const val TAG = "UIModule"
    }
}
