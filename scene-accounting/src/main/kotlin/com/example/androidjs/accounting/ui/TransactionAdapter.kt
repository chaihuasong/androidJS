package com.example.androidjs.accounting.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.androidjs.accounting.R
import com.example.androidjs.accounting.data.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

class TransactionAdapter : ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction, parent, false)
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

        private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        private val categoryColors = mapOf(
            "餐饮" to "#FF7043",
            "交通" to "#42A5F5",
            "购物" to "#AB47BC",
            "娱乐" to "#FFA726",
            "住房" to "#26A69A",
            "医疗" to "#EF5350",
            "教育" to "#5C6BC0",
            "通讯" to "#66BB6A",
            "人情" to "#EC407A",
            "其他" to "#78909C"
        )

        fun bind(transaction: TransactionEntity) {
            textCategoryTag.text = transaction.category
            val bgColor = categoryColors[transaction.category] ?: "#78909C"
            textCategoryTag.background.setTint(Color.parseColor(bgColor))

            textDescription.text = transaction.description

            val isExpense = transaction.type == "expense"
            val prefix = if (isExpense) "-¥" else "+¥"
            textAmount.text = "$prefix${String.format("%.2f", transaction.amount)}"
            textAmount.setTextColor(Color.parseColor(if (isExpense) "#F44336" else "#4CAF50"))

            textTime.text = dateFormat.format(Date(transaction.timestamp))
            textRawInput.text = "「${transaction.rawInput}」"
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TransactionEntity>() {
        override fun areItemsTheSame(old: TransactionEntity, new: TransactionEntity) = old.id == new.id
        override fun areContentsTheSame(old: TransactionEntity, new: TransactionEntity) = old == new
    }
}
