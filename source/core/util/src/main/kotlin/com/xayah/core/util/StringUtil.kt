package com.xayah.core.util
fun String.formatSize(): String { val bytes = this.toLongOrNull() ?: 0L; return "%.2f MB".format(bytes / 1_048_576.0) }
