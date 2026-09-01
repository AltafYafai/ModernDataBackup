package com.xayah.core.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import com.xayah.core.database.dao.AppDao
import com.xayah.core.database.entity.AppEntity
import com.xayah.core.util.FileUtil
import com.xayah.core.util.PathUtil
import com.xayah.core.util.RootUtil
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
        val isRoot = RootUtil.isRootAvailable()

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

                // 1. APK Size (Base + Splits)
                var apkSize = 0L
                if (appInfo.sourceDir != null) {
                    val baseFile = File(appInfo.sourceDir)
                    if (baseFile.exists()) apkSize += baseFile.length()
                }
                appInfo.splitSourceDirs?.forEach { splitPath ->
                    val splitFile = File(splitPath)
                    if (splitFile.exists()) apkSize += splitFile.length()
                }

                // 2. Media Size (/sdcard/Android/media/<pkg>)
                val mediaDir = File(Environment.getExternalStorageDirectory(), "Android/media/${appInfo.packageName}")
                val mediaSize = if (mediaDir.exists()) FileUtil.getSize(mediaDir) else 0L

                // 3. OBB Size (/sdcard/Android/obb/<pkg>)
                val obbDir = File(Environment.getExternalStorageDirectory(), "Android/obb/${appInfo.packageName}")
                val obbSize = if (obbDir.exists()) FileUtil.getSize(obbDir) else 0L

                // 4. External Data Size (/sdcard/Android/data/<pkg>)
                val extDataDir = File(Environment.getExternalStorageDirectory(), "Android/data/${appInfo.packageName}")
                val extDataSize = if (extDataDir.exists()) FileUtil.getSize(extDataDir) else 0L

                // 5. Internal Data Size (/data/data/<pkg>)
                var dataSize = 0L
                val internalDataDir = File(appInfo.dataDir ?: "/data/data/${appInfo.packageName}")
                if (internalDataDir.exists() && internalDataDir.canRead()) {
                    dataSize = FileUtil.getSize(internalDataDir)
                } else if (isRoot) {
                    val duRes = RootUtil.executeCommand("du -sb '${internalDataDir.absolutePath}' 2>/dev/null", useRoot = true)
                    if (duRes.isSuccess && duRes.out.isNotEmpty()) {
                        dataSize = duRes.out.first().substringBefore("\t").trim().toLongOrNull() ?: 0L
                    }
                }

                val pkgBackupDir = File(backupDir, appInfo.packageName)
                val isBackedUp = pkgBackupDir.exists() && (pkgBackupDir.list()?.isNotEmpty() == true)

                AppEntity(
                    packageName = appInfo.packageName,
                    label = label,
                    versionName = try { pm.getPackageInfo(appInfo.packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" },
                    versionCode = try { pm.getPackageInfo(appInfo.packageName, 0).versionCode.toLong() } catch (e: Exception) { 1L },
                    isSystemApp = isSystem,
                    dataSize = dataSize,
                    apkSize = apkSize,
                    mediaSize = mediaSize,
                    obbSize = obbSize,
                    extDataSize = extDataSize,
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
