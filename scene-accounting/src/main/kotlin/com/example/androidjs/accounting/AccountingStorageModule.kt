package com.example.androidjs.accounting

import android.content.Context
import android.util.Log
import com.example.androidjs.accounting.data.AccountingDatabase
import com.example.androidjs.accounting.data.TransactionEntity
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Native module bridging Room database operations to JS for accounting.
 */
class AccountingStorageModule(context: Context) : NativeModule {

    override val name: String = "accountingStorage"

    private val db = AccountingDatabase.getInstance(context)
    private val dao = db.transactionDao()
    private val json = Json { ignoreUnknownKeys = true }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "insert" -> {
                val args = json.decodeFromString<InsertArgs>(argsJson)
                val entity = TransactionEntity(
                    amount = args.amount,
                    category = args.category,
                    description = args.description,
                    type = args.type,
                    rawInput = args.rawInput
                )
                val id = runBlocking { dao.insert(entity) }
                json.encodeToString(InsertResult.serializer(), InsertResult(id))
            }
            "getAll" -> {
                val transactions = runBlocking { dao.getAll() }
                json.encodeToString(
                    TransactionListResult.serializer(),
                    TransactionListResult(transactions.map { it.toDto() })
                )
            }
            "getByCategory" -> {
                val args = json.decodeFromString<CategoryArgs>(argsJson)
                val transactions = runBlocking { dao.getByCategory(args.category) }
                json.encodeToString(
                    TransactionListResult.serializer(),
                    TransactionListResult(transactions.map { it.toDto() })
                )
            }
            "getCategorySummary" -> {
                val args = json.decodeFromString<TypeArgs>(argsJson)
                val summary = runBlocking { dao.getCategorySummary(args.type) }
                json.encodeToString(
                    CategorySummaryResult.serializer(),
                    CategorySummaryResult(summary.map { CategorySummaryDto(it.category, it.total) })
                )
            }
            "getTotal" -> {
                val args = json.decodeFromString<TypeArgs>(argsJson)
                val total = runBlocking { dao.getTotalByType(args.type) } ?: 0.0
                json.encodeToString(TotalResult.serializer(), TotalResult(total))
            }
            "delete" -> {
                val args = json.decodeFromString<DeleteArgs>(argsJson)
                val entity = runBlocking { dao.getById(args.id) }
                if (entity != null) {
                    runBlocking { dao.delete(entity) }
                }
                null
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun TransactionEntity.toDto() = TransactionDto(
        id = id,
        amount = amount,
        category = category,
        description = description,
        type = type,
        rawInput = rawInput,
        timestamp = timestamp
    )

    @Serializable
    data class InsertArgs(
        val amount: Double,
        val category: String,
        val description: String,
        val type: String,
        val rawInput: String
    )

    @Serializable
    data class InsertResult(val id: Long)

    @Serializable
    data class TransactionDto(
        val id: Long,
        val amount: Double,
        val category: String,
        val description: String,
        val type: String,
        val rawInput: String,
        val timestamp: Long
    )

    @Serializable
    data class TransactionListResult(val transactions: List<TransactionDto>)

    @Serializable
    data class CategoryArgs(val category: String)

    @Serializable
    data class TypeArgs(val type: String)

    @Serializable
    data class DeleteArgs(val id: Long)

    @Serializable
    data class CategorySummaryDto(val category: String, val total: Double)

    @Serializable
    data class CategorySummaryResult(val summary: List<CategorySummaryDto>)

    @Serializable
    data class TotalResult(val total: Double)

    companion object {
        private const val TAG = "AcctStorageModule"
    }
}
