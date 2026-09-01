package com.xayah.core.database.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "fileentity")
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val path: String,
    val name: String,
    val size: Long = 0,
    val hash: String? = null
)
