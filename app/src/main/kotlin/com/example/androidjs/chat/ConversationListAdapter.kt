package com.example.androidjs.chat

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import com.example.androidjs.ai.persistence.ConversationEntity

class ConversationListAdapter(
    private val onItemClick: (Long) -> Unit,
    private val onItemLongClick: (Long) -> Unit
) : ListAdapter<ConversationEntity, ConversationListAdapter.ViewHolder>(DIFF) {

    private var selectedId: Long = -1

    fun setSelectedId(id: Long) {
        val old = selectedId
        selectedId = id
        currentList.forEachIndexed { index, item ->
            if (item.id == old || item.id == id) notifyItemChanged(index)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.time.text = DateUtils.getRelativeTimeSpanString(
            item.updatedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        )
        holder.itemView.isActivated = item.id == selectedId
        holder.itemView.setOnClickListener { onItemClick(item.id) }
        holder.itemView.setOnLongClickListener {
            onItemLongClick(item.id)
            true
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tv_title)
        val time: TextView = view.findViewById(R.id.tv_time)
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(a: ConversationEntity, b: ConversationEntity) = a.id == b.id
            override fun areContentsTheSame(a: ConversationEntity, b: ConversationEntity) = a == b
        }
    }
}
