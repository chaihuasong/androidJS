package com.example.androidjs.demo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import kotlinx.serialization.Serializable

@Serializable
data class ScriptListItem(
    val tag: String = "",
    val tagColor: String = "#78909C",
    val title: String = "",
    val subtitle: String = "",
    val value: String = "",
    val valueColor: String = "#424242",
    val time: String = ""
)

class ScriptListAdapter : ListAdapter<ScriptListItem, ScriptListAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textCategoryTag: TextView = itemView.findViewById(R.id.text_category_tag)
        private val textDescription: TextView = itemView.findViewById(R.id.text_description)
        private val textAmount: TextView = itemView.findViewById(R.id.text_amount)
        private val textTime: TextView = itemView.findViewById(R.id.text_time)
        private val textRawInput: TextView = itemView.findViewById(R.id.text_raw_input)

        fun bind(item: ScriptListItem) {
            textCategoryTag.text = item.tag
            try {
                textCategoryTag.background.setTint(Color.parseColor(item.tagColor))
            } catch (_: Exception) {
                textCategoryTag.background.setTint(Color.parseColor("#78909C"))
            }

            textDescription.text = item.title
            textAmount.text = item.value
            try {
                textAmount.setTextColor(Color.parseColor(item.valueColor))
            } catch (_: Exception) {
                textAmount.setTextColor(Color.parseColor("#424242"))
            }

            textTime.text = item.time
            textRawInput.text = item.subtitle
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScriptListItem>() {
        override fun areItemsTheSame(old: ScriptListItem, new: ScriptListItem) =
            old.tag == new.tag && old.title == new.title && old.time == new.time

        override fun areContentsTheSame(old: ScriptListItem, new: ScriptListItem) = old == new
    }
}
