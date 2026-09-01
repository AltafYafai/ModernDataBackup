package com.xayah.core.util
fun Long.toDateString(): String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(this))
