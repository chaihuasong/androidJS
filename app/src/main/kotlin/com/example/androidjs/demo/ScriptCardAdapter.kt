package com.example.androidjs.demo

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import com.example.androidjs.core.script.CachedScript
import com.example.androidjs.core.script.ScriptInfo
import com.google.android.material.button.MaterialButton

data class ScriptItem(
    val info: ScriptInfo,
    val cached: CachedScript?,
    val isLoading: Boolean = false
) {
    val isCached: Boolean get() = cached != null
    val hasUpdate: Boolean get() = cached != null && info.version > cached.info.version
}

class ScriptCardAdapter(
    private val onActionClick: (ScriptItem) -> Unit
) : ListAdapter<ScriptItem, ScriptCardAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_script_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onActionClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val layoutBg: LinearLayout = itemView.findViewById(R.id.layout_card_bg)
        private val textIcon: TextView = itemView.findViewById(R.id.text_icon)
        private val textName: TextView = itemView.findViewById(R.id.text_name)
        private val textDescription: TextView = itemView.findViewById(R.id.text_description)
        private val textStatus: TextView = itemView.findViewById(R.id.text_status)
        private val progressDownload: ProgressBar = itemView.findViewById(R.id.progress_download)
        private val btnAction: MaterialButton = itemView.findViewById(R.id.btn_action)

        fun bind(item: ScriptItem, onActionClick: (ScriptItem) -> Unit) {
            textIcon.text = item.info.icon
            textName.text = item.info.name
            textDescription.text = item.info.description

            try {
                layoutBg.setBackgroundColor(Color.parseColor(item.info.color))
            } catch (_: Exception) {
                layoutBg.setBackgroundColor(Color.parseColor("#37474F"))
            }

            progressDownload.visibility = if (item.isLoading) View.VISIBLE else View.GONE
            btnAction.isEnabled = !item.isLoading

            when {
                item.isLoading -> {
                    textStatus.text = "下载中..."
                    btnAction.text = "下载中"
                }
                item.hasUpdate -> {
                    textStatus.text = "已缓存 v${item.cached!!.info.version} → 可更新到 v${item.info.version}"
                    btnAction.text = "更新"
                }
                item.isCached -> {
                    textStatus.text = "已缓存 v${item.cached!!.info.version}"
                    btnAction.text = "运行"
                }
                else -> {
                    textStatus.text = "未下载"
                    btnAction.text = "下载"
                }
            }

            btnAction.setOnClickListener { onActionClick(item) }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ScriptItem>() {
        override fun areItemsTheSame(old: ScriptItem, new: ScriptItem) =
            old.info.id == new.info.id

        override fun areContentsTheSame(old: ScriptItem, new: ScriptItem) =
            old == new
    }
}
