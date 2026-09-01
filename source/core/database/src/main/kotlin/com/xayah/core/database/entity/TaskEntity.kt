package com.xayah.core.database.entity
import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "taskentity")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val label: String,
    val status: String = "PENDING",
    val timestamp: Long = System.currentTimeMillis(),
    val backupPath: String = "",
    val isBackup: Boolean = true
)
