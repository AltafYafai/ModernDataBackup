package com.xayah.core.datastore
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
private val Context.dataStore by preferencesDataStore(name = "settings")
@Singleton
class DbPreferencesDataSource @Inject constructor(@ApplicationContext private val context: Context) {
    val data = context.dataStore.data
    suspend fun getBackupPath(): String = context.dataStore.data.map { it[Keys.BACKUP_PATH] ?: "/storage/emulated/0/Backup" }.first()
    suspend fun setBackupPath(path: String) { context.dataStore.edit { it[Keys.BACKUP_PATH] = path } }
    object Keys {
        val BACKUP_PATH = stringPreferencesKey("backup_path")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val COMPRESSION = intPreferencesKey("compression")
        val ENCRYPT = booleanPreferencesKey("encrypt")
    }
    companion object {
        val KEY_AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val KEY_BACKUP_MEDIUM = stringPreferencesKey("backup_medium")
        val KEY_INCLUDE_SYSTEM = booleanPreferencesKey("include_system")
        val KEY_COMPRESSION = intPreferencesKey("compression_level")
    }
}
