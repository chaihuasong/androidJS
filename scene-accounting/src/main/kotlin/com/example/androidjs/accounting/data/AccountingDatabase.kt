package com.example.androidjs.accounting.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AccountingDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AccountingDatabase? = null

        fun getInstance(context: Context): AccountingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AccountingDatabase::class.java,
                    "accounting_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
