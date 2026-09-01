package com.xayah.core.database.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "appentity")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val versionName: String = "",
    val versionCode: Long = 0,
    val isSystemApp: Boolean = false,
    val dataSize: Long = 0,
    val apkSize: Long = 0,
    val mediaSize: Long = 0,
    val obbSize: Long = 0,
    val extDataSize: Long = 0,
    val enabled: Boolean = true
)
