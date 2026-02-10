package com.example.androidjs.core.modules

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * Generic database module using raw SQLiteDatabase.
 * JS defines table structure and queries; native only provides execution.
 */
class DatabaseModule(context: Context) : NativeModule {

    override val name: String = "database"

    private val json = Json { ignoreUnknownKeys = true }
    private val dbHelper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {}
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    }

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "exec" -> {
                val args = json.decodeFromString<ExecArgs>(argsJson)
                exec(args)
            }
            "query" -> {
                val args = json.decodeFromString<ExecArgs>(argsJson)
                query(args)
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun exec(args: ExecArgs): String {
        val db = dbHelper.writableDatabase
        val params = args.params
        if (params != null && params.isNotEmpty()) {
            val bindArgs = params.map { elem ->
                when (elem) {
                    is JsonNull -> null
                    is JsonPrimitive -> {
                        if (elem.isString) elem.content
                        else elem.longOrNull?.toString() ?: elem.doubleOrNull?.toString() ?: elem.content
                    }
                    else -> elem.toString()
                }
            }.toTypedArray()
            db.execSQL(args.sql, bindArgs)
        } else {
            db.execSQL(args.sql)
        }
        return json.encodeToString(ExecResult.serializer(), ExecResult(success = true))
    }

    private fun query(args: ExecArgs): String {
        val db = dbHelper.readableDatabase
        val selectionArgs = args.params?.map { elem ->
            when (elem) {
                is JsonNull -> null
                is JsonPrimitive -> {
                    if (elem.isString) elem.content
                    else elem.content
                }
                else -> elem.toString()
            }
        }?.toTypedArray()

        val cursor = db.rawQuery(args.sql, selectionArgs)
        val columns = cursor.columnNames.toList()
        val rows = mutableListOf<Map<String, String?>>()
        while (cursor.moveToNext()) {
            val row = mutableMapOf<String, String?>()
            for (i in columns.indices) {
                row[columns[i]] = if (cursor.isNull(i)) null else cursor.getString(i)
            }
            rows.add(row)
        }
        cursor.close()

        return json.encodeToString(QueryResult.serializer(), QueryResult(columns = columns, rows = rows))
    }

    override fun destroy() {
        dbHelper.close()
    }

    @Serializable
    data class ExecArgs(
        val sql: String,
        val params: JsonArray? = null
    )

    @Serializable
    data class ExecResult(val success: Boolean)

    @Serializable
    data class QueryResult(
        val columns: List<String>,
        val rows: List<Map<String, String?>>
    )

    companion object {
        private const val TAG = "DatabaseModule"
        private const val DB_NAME = "androidjs.db"
        private const val DB_VERSION = 1
    }
}
