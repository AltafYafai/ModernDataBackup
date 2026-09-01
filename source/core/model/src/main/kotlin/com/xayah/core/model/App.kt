package com.xayah.core.model

data class AppInfo(
    val packageName: String,
    val appLabel: String,
    val dataSize: Long,
    val isSystem: Boolean,
    val dataPath: String
)