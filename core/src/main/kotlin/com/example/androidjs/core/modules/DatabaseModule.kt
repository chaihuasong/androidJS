package com.example.androidjs.core.modules

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Generic SQLite database module.
 * JS scripts define their own tables and queries.
 */
class DatabaseModule(context: Context) : NativeModule {

    override val name: String = "database"

    private val json = Json { ignoreUnknownKeys = true }

    private val dbHelper = object : SQLiteOpenHelper(
        context, DB_NAME, null, DB_VERSION
    ) {
        override fun onCreate(db: SQLiteDatabase) { /* JS creates tables */ }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) { /* no-op */ }
    }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "exec" -> {
                val args = json.decodeFromString<SqlArgs>(argsJson)
                executeExec(args)
            }
            "query" -> {
                val args = json.decodeFromString<SqlArgs>(argsJson)
                executeQuery(args)
            }
            "listTables" -> listTables()
            "describeTable" -> {
                val args = json.decodeFromString<TableNameArgs>(argsJson)
                describeTable(args.name)
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun executeExec(args: SqlArgs): String {
        return try {
            val db = dbHelper.writableDatabase
            if (args.params != null) {
                val bindArgs = args.params.map { it.jsonPrimitive.content }.toTypedArray()
                db.execSQL(args.sql, bindArgs)
            } else {
                db.execSQL(args.sql)
            }
            """{"success": true}"""
        } catch (e: Exception) {
            Log.e(TAG, "exec failed", e)
            """{"success": false, "error": "${e.message?.replace("\"", "\\\"")}"}"""
        }
    }

    private fun executeQuery(args: SqlArgs): String {
        return try {
            val db = dbHelper.readableDatabase
            val selectionArgs = args.params?.map { it.jsonPrimitive.content }?.toTypedArray()
            val cursor = db.rawQuery(args.sql, selectionArgs)
            val rows = mutableListOf<Map<String, String?>>()
            cursor.use {
                val columns = it.columnNames
                while (it.moveToNext()) {
                    val row = mutableMapOf<String, String?>()
                    for (col in columns) {
                        val idx = it.getColumnIndex(col)
                        row[col] = if (it.isNull(idx)) null else it.getString(idx)
                    }
                    rows.add(row)
                }
            }
            json.encodeToString(QueryResult.serializer(), QueryResult(rows))
        } catch (e: Exception) {
            Log.e(TAG, "query failed", e)
            """{"rows": [], "error": "${e.message?.replace("\"", "\\\"")}"}"""
        }
    }

    private fun listTables(): String {
        return try {
            val db = dbHelper.readableDatabase
            val cursor = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%'",
                null
            )
            val tables = mutableListOf<String>()
            cursor.use {
                while (it.moveToNext()) {
                    tables.add(it.getString(0))
                }
            }
            json.encodeToString(TablesResult.serializer(), TablesResult(tables))
        } catch (e: Exception) {
            Log.e(TAG, "listTables failed", e)
            """{"tables": [], "error": "${e.message?.replace("\"", "\\\"")}"}"""
        }
    }

    private fun describeTable(tableName: String): String {
        return try {
            val db = dbHelper.readableDatabase
            // Sanitize table name to prevent SQL injection
            val safeName = tableName.replace(Regex("[^a-zA-Z0-9_]"), "")
            val cursor = db.rawQuery("PRAGMA table_info($safeName)", null)
            val columns = mutableListOf<ColumnInfo>()
            cursor.use {
                while (it.moveToNext()) {
                    columns.add(
                        ColumnInfo(
                            name = it.getString(it.getColumnIndexOrThrow("name")),
                            type = it.getString(it.getColumnIndexOrThrow("type")),
                            notnull = it.getInt(it.getColumnIndexOrThrow("notnull")) == 1,
                            pk = it.getInt(it.getColumnIndexOrThrow("pk")) == 1
                        )
                    )
                }
            }
            json.encodeToString(TableDescResult.serializer(), TableDescResult(columns))
        } catch (e: Exception) {
            Log.e(TAG, "describeTable failed", e)
            """{"columns": [], "error": "${e.message?.replace("\"", "\\\"")}"}"""
        }
    }

    @Serializable
    private data class SqlArgs(val sql: String, val params: JsonArray? = null)

    @Serializable
    private data class TableNameArgs(val name: String)

    @Serializable
    private data class QueryResult(val rows: List<Map<String, String?>>)

    @Serializable
    private data class TablesResult(val tables: List<String>)

    @Serializable
    private data class ColumnInfo(
        val name: String,
        val type: String,
        val notnull: Boolean,
        val pk: Boolean
    )

    @Serializable
    private data class TableDescResult(val columns: List<ColumnInfo>)

    companion object {
        private const val TAG = "DatabaseModule"
        private const val DB_NAME = "androidjs_db"
        private const val DB_VERSION = 1
    }
}
