package com.xayah.core.model

data class BackupTarget(
    val packageName: String,
    val appLabel: String,
    val dataSize: Long,
    val selected: Boolean
)