package com.freshbackup.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * One row per backup version. Mirrors Swift Backup's history: multiple
 * versions per app, user notes, protected flag, labels and per-app config.
 */
@Entity(tableName = "backups", primaryKeys = ["packageName", "timestamp"])
data class BackupRecord(
    val packageName: String,
    val timestamp: Long,
    val label: String = "",
    val versionName: String = "",
    val versionCode: Long = 0,
    val path: String = "",
    val totalBytes: Long = 0,
    val hasApk: Boolean = false,
    val hasData: Boolean = false,
    val note: String = "",
    val protected: Boolean = false,
    val labels: String = "",
    val includeApk: Boolean = true,
    val includeData: Boolean = true,
    val includeExt: Boolean = true,
    val includeMedia: Boolean = true,
    val includeObb: Boolean = true
)

@Dao
interface BackupDao {
    @Query("SELECT * FROM backups ORDER BY timestamp DESC")
    suspend fun all(): List<BackupRecord>

    @Query("SELECT * FROM backups WHERE packageName = :pkg ORDER BY timestamp DESC")
    suspend fun versions(pkg: String): List<BackupRecord>

    @Query("SELECT DISTINCT packageName FROM backups")
    suspend fun backedUpPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: BackupRecord)

    @Query("UPDATE backups SET note = :note WHERE packageName = :pkg AND timestamp = :ts")
    suspend fun setNote(pkg: String, ts: Long, note: String)

    @Query("UPDATE backups SET protected = :isProtected WHERE packageName = :pkg AND timestamp = :ts")
    suspend fun setProtected(pkg: String, ts: Long, isProtected: Boolean)

    @Query("DELETE FROM backups WHERE packageName = :pkg AND timestamp = :ts AND `protected` = 0")
    suspend fun deleteVersion(pkg: String, ts: Long)

    @Query("DELETE FROM backups WHERE packageName = :pkg AND `protected` = 0 AND timestamp NOT IN (SELECT timestamp FROM backups WHERE packageName = :pkg ORDER BY timestamp DESC LIMIT :keep)")
    suspend fun prune(pkg: String, keep: Int)
}

@Database(entities = [BackupRecord::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun backups(): BackupDao
}
