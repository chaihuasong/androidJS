package com.example.androidjs.accounting.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val category: String,
    val description: String,
    val type: String,           // "expense" or "income"
    val rawInput: String,       // Original user input text
    val timestamp: Long = System.currentTimeMillis()
)
