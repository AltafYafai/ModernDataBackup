package com.xayah.core.model

data class AppSettings(
    val autoBackup: Boolean,
    val backupMedium: String,
    val includeSystemApps: Boolean,
    val compressionLevel: Int
)