package com.freshbackup.app.data.backup

import android.content.Context
import com.freshbackup.app.core.RootShell
import com.freshbackup.app.data.BackupDao
import com.freshbackup.app.data.BackupRecord
import com.freshbackup.app.data.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class BackupOptions(
    val includeApk: Boolean = true,
    val includeData: Boolean = true,
    val includeDe: Boolean = true,
    val includeExt: Boolean = true,
    val includeMedia: Boolean = true,
    val includeObb: Boolean = true,
    val includeIdentity: Boolean = true
)

/** Orchestrates full app backup/restore into <root>/<package>/ + meta.json. */
@Singleton
class Engine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apk: Apk,
    private val appData: AppData,
    private val dao: BackupDao,
    private val settings: Settings
) {

    suspend fun backupApp(pkg: String, opts: BackupOptions, log: (String) -> Unit): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = File(settings.backupDir())
            val dir = File(root, pkg)
            val rooted = RootShell.isRooted()
            if (rooted) {
                RootShell.forceStop(pkg)
                RootShell.mkdir(dir.absolutePath)
            } else {
                dir.mkdirs()
            }

            @Suppress("DEPRECATION")
            val pm = context.packageManager
            val label = try {
                pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
            } catch (e: Exception) {
                pkg
            }
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(pkg, 0)

            var apkBytes = 0L
            if (opts.includeApk) {
                log("backing up APK…")
                apkBytes = apk.backupApks(pkg, dir, rooted)
            }
            var dataBytes = 0L
            if ((opts.includeData || opts.includeDe || opts.includeExt || opts.includeMedia || opts.includeObb) && rooted) {
                log("backing up data…")
                dataBytes = appData.backup(pkg, dir, opts.includeData, opts.includeDe, opts.includeExt, opts.includeMedia, opts.includeObb) { log(it) }.values.sum()
            } else if (!rooted) {
                log("no root: app data skipped")
            }

            val meta = JSONObject()
                .put("label", label)
                .put("versionName", info.versionName ?: "")
                .put("time", System.currentTimeMillis())
                .put("apkBytes", apkBytes)
                .put("dataBytes", dataBytes)
            File(dir, "meta.json").writeText(meta.toString())

            val total = apkBytes + dataBytes
            dao.insert(
                BackupRecord(
                    packageName = pkg,
                    timestamp = System.currentTimeMillis(),
                    label = label,
                    versionName = info.versionName ?: "",
                    path = dir.absolutePath,
                    totalBytes = total,
                    hasApk = apkBytes > 0,
                    hasData = dataBytes > 0,
                    includeApk = opts.includeApk,
                    includeData = opts.includeData,
                    includeExt = opts.includeExt,
                    includeMedia = opts.includeMedia,
                    includeObb = opts.includeObb
                )
            )
            dao.prune(pkg, settings.keepVersions())
            log("done: $label (${total / 1024} KB)")
            Result.success(label)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreApp(pkg: String, opts: BackupOptions, installer: Installer, log: (String) -> Unit): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (pkg == context.packageName) return@withContext Result.failure(Exception("cannot restore self while running"))
                val dir = File(settings.backupDir(), pkg)
                if (!dir.exists()) return@withContext Result.failure(Exception("no backup for $pkg"))
                val rooted = RootShell.isRooted()

                val apks = apk.backedUpApks(dir)
                if (opts.includeApk && apks.isNotEmpty()) {
                    if (rooted) {
                        log("installing APK (root)…")
                        log(installer.installBatchRoot(apks, pkg))
                    } else if (apks.size == 1) {
                        log("opening system installer…")
                        installer.installManual(apks.first())
                        return@withContext Result.success("installer opened")
                    } else {
                        return@withContext Result.failure(Exception("split APKs need root for batch install"))
                    }
                    RootShell.forceStop(pkg)
                }
                if (rooted && (opts.includeData || opts.includeDe || opts.includeExt || opts.includeMedia || opts.includeObb)) {
                    log("restoring data…")
                    appData.restore(pkg, dir, opts.includeData, opts.includeDe, opts.includeExt, opts.includeMedia, opts.includeObb, opts.includeIdentity, log)
                }
                log("restore complete")
                Result.success(pkg)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun backupRoot(): File = withContext(Dispatchers.IO) {
        val root = File(settings.backupDir())
        root.mkdirs()
        root
    }
}
