package com.xayah.core.util
import java.io.File
object FileUtil { fun getSize(file: File): Long = if (file.isDirectory) file.walk().filter { it.isFile }.sumOf { it.length() } else file.length() }
