package com.xayah.core.model

data class AppData(
    val packageName: String,
    val appLabel: String,
    val dataPath: String,
    val dataSize: Long,
    val timestamp: Long
)