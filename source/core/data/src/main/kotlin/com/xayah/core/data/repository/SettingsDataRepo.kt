package com.xayah.core.data.repository
import com.xayah.core.datastore.DbPreferencesDataSource
import com.xayah.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class SettingsDataRepo @Inject constructor(private val prefs: DbPreferencesDataSource) {
    val settingsFlow: Flow<AppSettings> = prefs.data.map {
        AppSettings(
            autoBackup = it[DbPreferencesDataSource.KEY_AUTO_BACKUP] as? Boolean ?: false,
            backupMedium = it[DbPreferencesDataSource.KEY_BACKUP_MEDIUM] as? String ?: "INTERNAL",
            includeSystemApps = it[DbPreferencesDataSource.KEY_INCLUDE_SYSTEM] as? Boolean ?: false,
            compressionLevel = it[DbPreferencesDataSource.KEY_COMPRESSION] as? Int ?: 3
        )
    }
}
