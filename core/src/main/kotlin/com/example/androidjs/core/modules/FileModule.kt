package com.example.androidjs.core.modules

import android.content.Context
import android.util.Log
import com.example.androidjs.core.bridge.NativeModule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * File operations module restricted to app internal storage.
 */
class FileModule(private val context: Context) : NativeModule {

    override val name: String = "file"

    private val json = Json { ignoreUnknownKeys = true }
    private val baseDir: File get() = context.filesDir

    override fun invoke(method: String, argsJson: String): String? {
        return when (method) {
            "readText" -> {
                val args = json.decodeFromString<PathArgs>(argsJson)
                readText(args.path)
            }
            "writeText" -> {
                val args = json.decodeFromString<WriteArgs>(argsJson)
                writeText(args.path, args.content)
            }
            "listFiles" -> {
                val args = json.decodeFromString<DirArgs>(argsJson)
                listFiles(args.dir)
            }
            "delete" -> {
                val args = json.decodeFromString<PathArgs>(argsJson)
                deleteFile(args.path)
            }
            else -> {
                Log.w(TAG, "Unknown method: $method")
                null
            }
        }
    }

    private fun resolveSafe(path: String): File? {
        val resolved = File(baseDir, path).canonicalFile
        // Ensure the resolved path is still within baseDir (prevent path traversal)
        return if (resolved.path.startsWith(baseDir.canonicalPath)) resolved else null
    }

    private fun readText(path: String): String {
        val file = resolveSafe(path)
            ?: return """{"error": "Invalid path"}"""
        return if (file.exists()) {
            val content = file.readText()
            json.encodeToString(ReadResult.serializer(), ReadResult(content))
        } else {
            """{"error": "File not found"}"""
        }
    }

    private fun writeText(path: String, content: String): String {
        val file = resolveSafe(path)
            ?: return """{"error": "Invalid path"}"""
        file.parentFile?.mkdirs()
        file.writeText(content)
        return """{"success": true}"""
    }

    private fun listFiles(dir: String?): String {
        val directory = if (dir.isNullOrEmpty()) baseDir else resolveSafe(dir)
            ?: return """{"error": "Invalid path"}"""
        val files = directory.listFiles()?.map { f ->
            FileInfo(
                name = f.name,
                isDirectory = f.isDirectory,
                size = f.length()
            )
        } ?: emptyList()
        return json.encodeToString(FileListResult.serializer(), FileListResult(files))
    }

    private fun deleteFile(path: String): String {
        val file = resolveSafe(path)
            ?: return """{"error": "Invalid path"}"""
        val deleted = file.delete()
        return """{"success": $deleted}"""
    }

    @Serializable
    private data class PathArgs(val path: String)

    @Serializable
    private data class WriteArgs(val path: String, val content: String)

    @Serializable
    private data class DirArgs(val dir: String? = null)

    @Serializable
    private data class ReadResult(val content: String)

    @Serializable
    private data class FileInfo(val name: String, val isDirectory: Boolean, val size: Long)

    @Serializable
    private data class FileListResult(val files: List<FileInfo>)

    companion object {
        private const val TAG = "FileModule"
    }
}
