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

    @Query("SELECT SUM(totalBytes) FROM backups WHERE timestamp = (SELECT MAX(timestamp) FROM backups b2 WHERE b2.packageName = backups.packageName)")
    suspend fun latestTotalBytes(): Long?

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

/** Per-app custom backup/restore config + labels (Swift "custom configuration"). */
@Entity(tableName = "appconfig", primaryKeys = ["packageName"])
data class AppConfig(
    val packageName: String,
    val labels: String = "",
    val includeApk: Boolean = true,
    val includeData: Boolean = true,
    val includeDe: Boolean = true,
    val includeExt: Boolean = true,
    val includeMedia: Boolean = true,
    val includeObb: Boolean = true,
    val includeIdentity: Boolean = true
)

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM appconfig WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): AppConfig?

    @Query("SELECT * FROM appconfig")
    suspend fun all(): List<AppConfig>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: AppConfig)

    @Query("DELETE FROM appconfig WHERE packageName = :pkg")
    suspend fun clear(pkg: String)
}

/** Recurring backup schedules, each with its own app set and cloud target. */
@Entity(tableName = "schedules")
data class Schedule(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val packagesCsv: String = "",
    val intervalHours: Int = 24,
    val chargingOnly: Boolean = true,
    val cloudBackend: String = "",
    val enabled: Boolean = true,
    val lastRun: Long = 0
) {
    fun packages(): List<String> = packagesCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY id")
    suspend fun all(): List<Schedule>

    @Query("SELECT * FROM schedules WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): Schedule?

    @Query("SELECT * FROM schedules WHERE enabled = 1")
    suspend fun enabled(): List<Schedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(schedule: Schedule): Long

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE schedules SET lastRun = :ts WHERE id = :id")
    suspend fun markRun(id: Long, ts: Long)
}

@Database(entities = [BackupRecord::class, AppConfig::class, Schedule::class], version = 2, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun backups(): BackupDao
    abstract fun configs(): AppConfigDao
    abstract fun schedules(): ScheduleDao
}
