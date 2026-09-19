package com.freshbackup.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.prefs by preferencesDataStore("freshbackup")

/** App settings + backup account identity (backups are tied to userId). */
@Singleton
class Settings @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        val BACKUP_DIR = stringPreferencesKey("backup_dir")
        val USER_ID = stringPreferencesKey("user_id")
        val PASSPHRASE = stringPreferencesKey("passphrase")
        val KEEP = intPreferencesKey("keep_versions")
        val SCHED_ENABLED = booleanPreferencesKey("sched_enabled")
        val SCHED_HOURS = intPreferencesKey("sched_hours")
    }

    companion object {
        const val DEFAULT_DIR = "/sdcard/FreshBackup"
        const val DEFAULT_KEEP = 3
    }

    suspend fun backupDir(): String =
        context.prefs.data.map { it[Keys.BACKUP_DIR] ?: DEFAULT_DIR }.first()

    suspend fun setBackupDir(path: String) {
        context.prefs.edit { it[Keys.BACKUP_DIR] = path }
    }

    suspend fun userId(): String {
        val existing = context.prefs.data.map { it[Keys.USER_ID] }.first()
        if (!existing.isNullOrEmpty()) return existing
        val fresh = UUID.randomUUID().toString()
        context.prefs.edit { it[Keys.USER_ID] = fresh }
        return fresh
    }

    suspend fun passphrase(): String =
        context.prefs.data.map { it[Keys.PASSPHRASE] ?: "" }.first()

    suspend fun setPassphrase(value: String) {
        context.prefs.edit { it[Keys.PASSPHRASE] = value }
    }

    suspend fun keepVersions(): Int =
        context.prefs.data.map { it[Keys.KEEP] ?: DEFAULT_KEEP }.first()

    suspend fun setKeepVersions(n: Int) {
        context.prefs.edit { it[Keys.KEEP] = n.coerceIn(1, 20) }
    }

    suspend fun scheduleEnabled(): Boolean =
        context.prefs.data.map { it[Keys.SCHED_ENABLED] ?: false }.first()

    suspend fun setScheduleEnabled(enabled: Boolean) {
        context.prefs.edit { it[Keys.SCHED_ENABLED] = enabled }
    }

    suspend fun scheduleHours(): Int =
        context.prefs.data.map { it[Keys.SCHED_HOURS] ?: 24 }.first()

    suspend fun setScheduleHours(hours: Int) {
        context.prefs.edit { it[Keys.SCHED_HOURS] = hours.coerceIn(1, 168) }
    }
}
