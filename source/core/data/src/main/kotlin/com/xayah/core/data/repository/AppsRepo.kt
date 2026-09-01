package com.xayah.core.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.xayah.core.database.dao.AppDao
import com.xayah.core.database.entity.AppEntity
import com.xayah.core.util.PathUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppsRepo @Inject constructor(private val appDao: AppDao) {

    fun getAllApps(): Flow<List<AppEntity>> = flow {
        emit(appDao.getAll())
    }

    suspend fun scanAndSyncInstalledApps(context: Context, includeSystem: Boolean = false): List<AppEntity> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val backupDir = PathUtil.getPrimaryBackupDir(context)
        val installedList = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        val appEntities = installedList
            .filter { appInfo ->
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                includeSystem || !isSystem
            }
            .map { appInfo ->
                val label = try {
                    appInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    appInfo.packageName
                }
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val apkFile = File(appInfo.sourceDir ?: "")
                val apkSize = if (apkFile.exists()) apkFile.length() else 0L

                val pkgBackupDir = File(backupDir, appInfo.packageName)
                val isBackedUp = pkgBackupDir.exists() && (pkgBackupDir.list()?.isNotEmpty() == true)

                AppEntity(
                    packageName = appInfo.packageName,
                    label = label,
                    versionName = try { pm.getPackageInfo(appInfo.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" },
                    versionCode = try { pm.getPackageInfo(appInfo.packageName, 0).versionCode.toLong() } catch (e: Exception) { 1L },
                    isSystemApp = isSystem,
                    dataSize = apkSize,
                    enabled = isBackedUp
                )
            }
            .sortedBy { it.label.lowercase() }

        if (appEntities.isNotEmpty()) {
            appDao.clearAll()
            appDao.insertAll(appEntities)
        }
        appEntities
    }

    suspend fun insertApp(app: AppEntity) = appDao.insert(app)
    suspend fun insertAll(apps: List<AppEntity>) = appDao.insertAll(apps)
    suspend fun clearAll() = appDao.clearAll()
}
