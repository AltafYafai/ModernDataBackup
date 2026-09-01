package com.xayah.core.common.util
fun <T> List<T>.chunkedSafe(size: Int): List<List<T>> = if (size <= 0) listOf(this) else chunked(size)
