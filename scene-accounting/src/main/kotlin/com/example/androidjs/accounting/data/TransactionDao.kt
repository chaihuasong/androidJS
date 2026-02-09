package com.example.androidjs.accounting.data

import androidx.room.*

@Dao
interface TransactionDao {

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Delete
    suspend fun delete(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE category = :category ORDER BY timestamp DESC")
    suspend fun getByCategory(category: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY timestamp DESC")
    suspend fun getByType(type: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<TransactionEntity>

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = :type GROUP BY category ORDER BY total DESC")
    suspend fun getCategorySummary(type: String): List<CategorySummary>

    @Query("SELECT SUM(amount) FROM transactions WHERE type = :type")
    suspend fun getTotalByType(type: String): Double?

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

data class CategorySummary(
    val category: String,
    val total: Double
)
