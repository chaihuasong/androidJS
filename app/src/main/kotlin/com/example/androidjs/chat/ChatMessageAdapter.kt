package com.example.androidjs.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.R
import io.noties.markwon.Markwon

/**
 * UI model for chat messages displayed in the RecyclerView.
 */
sealed class ChatItem {
    data class UserMsg(val text: String) : ChatItem()
    data class AssistantMsg(val text: String, val isStreaming: Boolean = false) : ChatItem()
    data class ToolCall(
        val id: String,
        val name: String,
        val input: String = "",
        val result: String? = null,
        val isError: Boolean = false
    ) : ChatItem()
    data class ErrorMsg(val text: String) : ChatItem()
}

class ChatMessageAdapter(
    private val markwon: Markwon
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ChatItem.UserMsg -> TYPE_USER
        is ChatItem.AssistantMsg -> TYPE_ASSISTANT
        is ChatItem.ToolCall -> TYPE_TOOL
        is ChatItem.ErrorMsg -> TYPE_ERROR
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
            TYPE_ASSISTANT -> AssistantViewHolder(inflater.inflate(R.layout.item_chat_assistant, parent, false))
            TYPE_TOOL -> ToolViewHolder(inflater.inflate(R.layout.item_chat_tool_use, parent, false))
            TYPE_ERROR -> ErrorViewHolder(inflater.inflate(R.layout.item_chat_error, parent, false))
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ChatItem.UserMsg -> (holder as UserViewHolder).bind(item)
            is ChatItem.AssistantMsg -> (holder as AssistantViewHolder).bind(item)
            is ChatItem.ToolCall -> (holder as ToolViewHolder).bind(item)
            is ChatItem.ErrorMsg -> (holder as ErrorViewHolder).bind(item)
        }
    }

    inner class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textMessage: TextView = view.findViewById(R.id.text_message)
        fun bind(item: ChatItem.UserMsg) {
            textMessage.text = item.text
        }
    }

    inner class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textMessage: TextView = view.findViewById(R.id.text_message)
        fun bind(item: ChatItem.AssistantMsg) {
            if (item.isStreaming) {
                textMessage.text = item.text + "\u258C" // blinking cursor
            } else {
                markwon.setMarkdown(textMessage, item.text)
            }
        }
    }

    inner class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textToolName: TextView = view.findViewById(R.id.text_tool_name)
        private val textToolInput: TextView = view.findViewById(R.id.text_tool_input)
        private val textToolResult: TextView = view.findViewById(R.id.text_tool_result)

        fun bind(item: ChatItem.ToolCall) {
            textToolName.text = if (item.result == null) {
                "\u2699 ${item.name} ..."
            } else {
                "\u2699 ${item.name} \u2713"
            }

            if (item.input.isNotEmpty()) {
                textToolInput.text = item.input
                textToolInput.visibility = View.VISIBLE
            } else {
                textToolInput.visibility = View.GONE
            }

            if (item.result != null) {
                textToolResult.text = item.result
                textToolResult.visibility = View.VISIBLE
                if (item.isError) {
                    textToolResult.setTextColor(0xFFC62828.toInt())
                } else {
                    textToolResult.setTextColor(0xFF424242.toInt())
                }
            } else {
                textToolResult.visibility = View.GONE
            }
        }
    }

    inner class ErrorViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textError: TextView = view.findViewById(R.id.text_error)
        fun bind(item: ChatItem.ErrorMsg) {
            textError.text = item.text
        }
    }

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_ASSISTANT = 1
        private const val TYPE_TOOL = 2
        private const val TYPE_ERROR = 3

        private val DIFF = object : DiffUtil.ItemCallback<ChatItem>() {
            override fun areItemsTheSame(oldItem: ChatItem, newItem: ChatItem): Boolean {
                // For streaming updates, treat assistant messages at same position as same
                return oldItem === newItem ||
                    (oldItem is ChatItem.ToolCall && newItem is ChatItem.ToolCall && oldItem.id == newItem.id)
            }

            override fun areContentsTheSame(oldItem: ChatItem, newItem: ChatItem) = oldItem == newItem
        }
    }
}
