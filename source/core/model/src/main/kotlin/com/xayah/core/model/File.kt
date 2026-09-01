package com.xayah.core.model

data class ScannedFile(
    val path: String,
    val name: String,
    val size: Long,
    val hash: String?,
    val isSelected: Boolean
)