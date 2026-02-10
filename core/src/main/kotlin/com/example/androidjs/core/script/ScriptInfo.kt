package com.example.androidjs.core.script

import kotlinx.serialization.Serializable

@Serializable
data class ScriptInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val version: Int,
    val url: String,
    val requiredModules: List<String> = emptyList(),
    val icon: String = "",
    val color: String = ""
)

@Serializable
data class ScriptManifest(
    val scripts: List<ScriptInfo>,
    val manifestVersion: Int = 1
)

@Serializable
data class CachedScript(
    val info: ScriptInfo,
    val localPath: String,
    val cachedAt: Long
)
