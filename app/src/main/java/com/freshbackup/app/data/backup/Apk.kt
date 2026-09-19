package com.freshbackup.app.data.backup

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.freshbackup.app.core.RootShell
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val apkBytes: Long,
    val dataBytes: Long
)

/** APK handling + installed-app scanning. Works without root. */
@Singleton
class Apk @Inject constructor(@ApplicationContext private val context: Context) {

    @Suppress("DEPRECATION")
    suspend fun installed(includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val rooted = RootShell.isRooted()
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { ai ->
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (e: Exception) {
                    ai.packageName
                }
                var apkBytes = 0L
                try {
                    File(ai.sourceDir).takeIf { it.exists() }?.let { apkBytes += it.length() }
                    ai.splitSourceDirs?.forEach { p ->
                        File(p).takeIf { it.exists() }?.let { apkBytes += it.length() }
                    }
                } catch (e: Exception) { }
                val info = try {
                    pm.getPackageInfo(ai.packageName, 0)
                } catch (e: Exception) {
                    null
                }
                var dataBytes = 0L
                val internal = File(ai.dataDir ?: "/data/data/${ai.packageName}")
                if (internal.canRead()) {
                    dataBytes = dirSize(internal)
                } else if (rooted) {
                    val r = RootShell.exec("du -sb '${internal.absolutePath}' 2>/dev/null")
                    if (r.isSuccess && r.out.isNotEmpty()) {
                        dataBytes = r.out.first().substringBefore("\t").trim().toLongOrNull() ?: 0L
                    }
                }
                AppInfo(
                    packageName = ai.packageName,
                    label = label,
                    versionName = info?.versionName ?: "",
                    versionCode = info?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0,
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    apkBytes = apkBytes,
                    dataBytes = dataBytes
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Copies base + split APKs into [destDir]. Returns bytes copied. */
    @Suppress("DEPRECATION")
    suspend fun backupApks(packageName: String, destDir: File, rooted: Boolean): Long = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val paths = mutableListOf<String>()
        if (rooted) {
            val r = RootShell.exec("pm path $packageName")
            if (r.isSuccess) {
                r.out.forEach { line ->
                    val p = line.substringAfter("package:").trim()
                    if (p.isNotEmpty()) paths.add(p)
                }
            }
        }
        if (paths.isEmpty()) {
            val ai = pm.getApplicationInfo(packageName, 0)
            ai.sourceDir?.let { paths.add(it) }
            ai.splitSourceDirs?.forEach { paths.add(it) }
        }
        var total = 0L
        destDir.mkdirs()
        // Clear stale APKs from previous versions.
        destDir.listFiles { f -> f.name.endsWith(".apk") }?.forEach { it.delete() }
        paths.forEach { src ->
            val srcFile = File(src)
            val dst = File(destDir, srcFile.name)
            if (rooted) {
                RootShell.exec("cp -f '$src' '${dst.absolutePath}' && chmod 644 '${dst.absolutePath}'")
                if (dst.exists()) total += dst.length()
            } else if (srcFile.canRead()) {
                srcFile.copyTo(dst, overwrite = true)
                total += dst.length()
            }
        }
        total
    }

    fun backedUpApks(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") }?.toList() ?: emptyList()

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length()
        return dir.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }
}
