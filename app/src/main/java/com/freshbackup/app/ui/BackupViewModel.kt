package com.freshbackup.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings as SystemSettings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshbackup.app.core.RootShell
import com.freshbackup.app.data.AppConfig
import com.freshbackup.app.data.AppConfigDao
import com.freshbackup.app.data.BackupDao
import com.freshbackup.app.data.BackupRecord
import com.freshbackup.app.data.Schedule
import com.freshbackup.app.data.ScheduleDao
import com.freshbackup.app.data.Settings
import com.freshbackup.app.data.backup.Apk
import com.freshbackup.app.data.backup.AppInfo
import com.freshbackup.app.data.backup.BackupOptions
import com.freshbackup.app.data.backup.Engine
import com.freshbackup.app.data.backup.Installer
import com.freshbackup.app.data.backup.Medium
import com.freshbackup.app.data.cloud.CloudFactory
import com.freshbackup.app.data.cloud.CloudSync
import com.freshbackup.app.data.work.BackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class JobState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val log: List<String> = emptyList()
)

data class StorageStats(
    val path: String,
    val freeBytes: Long,
    val totalBytes: Long
) {
    val usedFraction: Float = if (totalBytes > 0) (totalBytes - freeBytes).toFloat() / totalBytes else 0f
    fun fmt(v: Long): String = when {
        v >= 1_000_000_000 -> "%.1f GB".format(v / 1_000_000_000.0)
        v >= 1_000_000 -> "%.0f MB".format(v / 1_000_000.0)
        else -> "${v / 1024} KB"
    }
}

data class PermState(
    val sms: Boolean = false,
    val calls: Boolean = false,
    val notifications: Boolean = false,
    val allFiles: Boolean = false,
    val dirWritable: Boolean = false
) {
    val messagingOk: Boolean get() = sms && calls
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apk: Apk,
    private val engine: Engine,
    private val installer: Installer,
    private val medium: Medium,
    private val dao: BackupDao,
    private val configs: AppConfigDao,
    private val schedules: ScheduleDao,
    private val settings: Settings,
    private val clouds: CloudFactory,
    private val sync: CloudSync
) : ViewModel() {

    val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val records = MutableStateFlow<List<BackupRecord>>(emptyList())
    val job = MutableStateFlow(JobState())
    val rooted = MutableStateFlow<Boolean?>(null)
    val backupDir = MutableStateFlow(Settings.DEFAULT_DIR)
    val keepVersions = MutableStateFlow(Settings.DEFAULT_KEEP)
    val schedEnabled = MutableStateFlow(false)
    val schedHours = MutableStateFlow(24)
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val labels: StateFlow<List<String>> = MutableStateFlow(emptyList())
    val schedList = MutableStateFlow<List<Schedule>>(emptyList())
    val storage = MutableStateFlow<StorageStats?>(null)
    val activeBackend = MutableStateFlow("local")
    val perms = MutableStateFlow(PermState())
    val diagnostics = MutableStateFlow<List<String>>(emptyList())

    private val iconCache = LruCache<String, Bitmap>(128)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            rooted.value = RootShell.isRooted(true)
            try {
                apps.value = apk.installed()
            } catch (e: Exception) { }
            records.value = dao.all()
            backupDir.value = settings.backupDir()
            keepVersions.value = settings.keepVersions()
            schedEnabled.value = settings.scheduleEnabled()
            schedHours.value = settings.scheduleHours()
            activeBackend.value = settings.activeBackend()
            refreshSchedules()
            refreshLabels()
            refreshStorage()
        }
        refreshPerms()
    }

    /** Fast permission snapshot — safe on the main thread, re-run on resume. */
    fun refreshPerms() {
        val sms = context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        val calls = context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val allFiles = Environment.isExternalStorageManager()
        val dir = try {
            val d = File(backupDir.value.ifBlank { Settings.DEFAULT_DIR })
            d.mkdirs()
            val probe = File(d, ".write_test")
            (probe.createNewFile() && probe.delete()) || d.canWrite()
        } catch (e: Exception) {
            false
        }
        perms.value = PermState(sms, calls, notif, allFiles, dir)
    }

    fun refreshDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            val lines = mutableListOf<String>()
            lines += RootShell.probe()
            val p = perms.value
            lines += "all-files access: ${if (p.allFiles) "YES" else "NO — required for /sdcard backups"}"
            lines += "backup dir writable: ${if (p.dirWritable) "YES (${backupDir.value})" else "NO (${backupDir.value})"}"
            lines += "SMS permission: ${if (p.sms) "YES" else "NO"}"
            lines += "call-log permission: ${if (p.calls) "YES" else "NO"}"
            lines += "notifications: ${if (p.notifications) "YES" else "NO"}"
            lines += "apps indexed: ${apps.value.size}"
            lines += "backup versions in DB: ${dao.all().size}"
            lines += "schedules: ${schedules.all().size}"
            diagnostics.value = lines
        }
    }

    private suspend fun refreshLabels() {
        val fromConfigs = configs.all().flatMap { it.labels.split(",") }
        val fromRecords = dao.all().flatMap { it.labels.split(",") }
        (labels as MutableStateFlow).value = (fromConfigs + fromRecords)
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    private suspend fun refreshStorage() {
        try {
            val dir = File(settings.backupDir()).apply { mkdirs() }
            val stat = StatFs(dir.path)
            storage.value = StorageStats(
                path = dir.absolutePath,
                freeBytes = stat.availableBlocksLong * stat.blockSizeLong,
                totalBytes = stat.blockCountLong * stat.blockSizeLong
            )
        } catch (e: Exception) { }
    }

    fun toggleSelect(pkg: String) {
        val cur = selected.value.toMutableSet()
        if (!cur.add(pkg)) cur.remove(pkg)
        selected.value = cur
    }

    fun clearSelection() {
        selected.value = emptySet()
    }

    private fun setJob(running: Boolean, progress: Float = 0f, status: String = "") {
        job.value = job.value.copy(running = running, progress = progress, status = status)
    }

    private fun appendLog(line: String) {
        job.value = job.value.copy(log = (job.value.log + line).takeLast(200))
    }

    suspend fun configFor(pkg: String): AppConfig =
        withContext(Dispatchers.IO) { configs.get(pkg) ?: AppConfig(pkg) }

    fun saveConfig(config: AppConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            configs.save(config)
            refreshLabels()
        }
    }

    fun optionsFor(config: AppConfig): BackupOptions = BackupOptions(
        includeApk = config.includeApk,
        includeData = config.includeData,
        includeDe = config.includeDe,
        includeExt = config.includeExt,
        includeMedia = config.includeMedia,
        includeObb = config.includeObb,
        includeIdentity = config.includeIdentity
    )

    suspend fun appIcon(pkg: String): ImageBitmap? = withContext(Dispatchers.IO) {
        iconCache.get(pkg)?.asImageBitmap() ?: try {
            @Suppress("DEPRECATION")
            val drawable = context.packageManager.getApplicationIcon(pkg)
            val bmp = drawable.toBitmap(96, 96)
            iconCache.put(pkg, bmp)
            bmp.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    fun backupSelected(opts: BackupOptions = BackupOptions()) {
        val pkgs = selected.value.toList().ifEmpty { return }
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "starting…")
            pkgs.forEachIndexed { i, pkg ->
                setJob(true, i.toFloat() / pkgs.size, pkg)
                val cfg = configs.get(pkg)
                val r = engine.backupApp(pkg, if (cfg != null) optionsFor(cfg) else opts) { appendLog("[$pkg] $it") }
                appendLog("[$pkg] ${r.getOrElse { "FAILED: ${it.message}" }}")
            }
            setJob(false, 1f, "done")
            records.value = dao.all()
            apps.value = apk.installed()
            refreshLabels()
            refreshStorage()
        }
    }

    fun backupOne(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "backing up $pkg…")
            val cfg = configs.get(pkg)
            val r = engine.backupApp(pkg, if (cfg != null) optionsFor(cfg) else BackupOptions()) { appendLog("[$pkg] $it") }
            appendLog("[$pkg] ${r.getOrElse { "FAILED: ${it.message}" }}")
            setJob(false, 1f, "done")
            records.value = dao.all()
            refreshStorage()
        }
    }

    fun restore(pkg: String, opts: BackupOptions = BackupOptions()) {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "restoring $pkg…")
            val cfg = configs.get(pkg)
            val r = engine.restoreApp(pkg, if (cfg != null) optionsFor(cfg) else opts, installer) { appendLog("[$pkg] $it") }
            r.exceptionOrNull()?.let { appendLog("FAILED: ${it.message}") }
            setJob(false, 1f, r.getOrDefault("done"))
        }
    }

    fun deleteVersion(pkg: String, ts: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val rec = dao.versions(pkg).firstOrNull { it.timestamp == ts }
            rec?.let {
                File(it.path).deleteRecursively()
                dao.deleteVersion(pkg, ts)
                records.value = dao.all()
                refreshStorage()
            }
        }
    }

    fun setNote(pkg: String, ts: Long, note: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setNote(pkg, ts, note)
            records.value = dao.all()
        }
    }

    fun setProtected(pkg: String, ts: Long, isProtected: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setProtected(pkg, ts, isProtected)
            records.value = dao.all()
        }
    }

    fun backupMedium(what: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "backing up $what…")
            val root = engine.backupRoot()
            val dir = medium.dir(root)
            val userId = settings.userId()
            val pass = settings.passphrase()
            val msg = when (what) {
                "sms" -> "${medium.backupSms(dir, userId, pass)} messages"
                "calls" -> "${medium.backupCalls(dir, userId, pass)} entries"
                "wallpaper" -> if (medium.backupWallpaper(dir)) "saved" else "failed"
                "wifi" -> if (medium.backupWifi(dir)) "saved" else "needs root"
                else -> "unknown"
            }
            appendLog("$what: $msg")
            setJob(false, 1f, msg)
            refreshStorage()
        }
    }

    fun restoreMedium(what: String) {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "restoring $what…")
            val dir = medium.dir(File(settings.backupDir()))
            val userId = settings.userId()
            val pass = settings.passphrase()
            val msg = when (what) {
                "sms" -> "${medium.restoreSms(dir, userId, pass)} restored"
                "calls" -> "${medium.restoreCalls(dir, userId, pass)} restored"
                "wallpaper" -> if (medium.restoreWallpaper(dir)) "applied" else "failed"
                "wifi" -> if (medium.restoreWifi(dir)) "applied" else "needs root"
                else -> "unknown"
            }
            appendLog("$what: $msg")
            setJob(false, 1f, msg)
        }
    }

    fun backupFolders(paths: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "backing up folders…")
            val dir = medium.dir(engine.backupRoot())
            paths.forEach { path ->
                val src = File(path)
                if (!src.exists()) {
                    appendLog("skip missing: $path")
                    return@forEach
                }
                try {
                    val n = medium.backupFolder(src, dir, src.name) { appendLog(it) }
                    appendLog("${src.name}: $n changed")
                } catch (e: Exception) {
                    appendLog("${src.name} FAILED: ${e.message}")
                }
            }
            setJob(false, 1f, "folders done")
            refreshStorage()
        }
    }

    fun hasAllFiles(): Boolean = Environment.isExternalStorageManager()

    fun allFilesIntent(): Intent = Intent(
        SystemSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun applySchedule(enabled: Boolean, hours: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setScheduleEnabled(enabled)
            settings.setScheduleHours(hours)
            schedEnabled.value = enabled
            schedHours.value = hours
            if (enabled) {
                val pkgs = apps.value.filter { !it.isSystem }.map { it.packageName }
                BackupWorker.schedule(context, pkgs, hours.toLong(), true)
                appendLog("schedule: every ${hours}h, ${pkgs.size} apps")
            } else {
                BackupWorker.cancel(context)
                appendLog("schedule disabled")
            }
        }
    }

    fun refreshSchedules() {
        viewModelScope.launch(Dispatchers.IO) {
            schedList.value = schedules.all()
        }
    }

    fun saveSchedule(s: Schedule, pkgs: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = schedules.save(s.copy(packagesCsv = pkgs.joinToString(",")))
            val saved = if (s.id == 0L) schedules.get(id) ?: s.copy(id = id) else s.copy(packagesCsv = pkgs.joinToString(","))
            if (saved.enabled) {
                BackupWorker.scheduleNamed(context, saved.id, saved.intervalHours.toLong(), saved.chargingOnly, saved.cloudBackend.isNotEmpty())
                appendLog("schedule '${saved.name}': every ${saved.intervalHours}h")
            } else {
                BackupWorker.cancelNamed(context, saved.id)
            }
            schedList.value = schedules.all()
        }
    }

    fun deleteSchedule(s: Schedule) {
        viewModelScope.launch(Dispatchers.IO) {
            BackupWorker.cancelNamed(context, s.id)
            schedules.delete(s.id)
            schedList.value = schedules.all()
        }
    }

    fun saveSettings(dir: String, keep: Int, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setBackupDir(dir.ifBlank { Settings.DEFAULT_DIR })
            settings.setKeepVersions(keep)
            settings.setPassphrase(passphrase)
            refresh()
        }
    }

    fun setActiveBackend(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setActiveBackend(id)
            activeBackend.value = id
        }
    }

    suspend fun cloudConfig(id: String): String = withContext(Dispatchers.IO) { settings.cloudConfig(id) }

    fun saveCloudConfig(id: String, json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setCloudConfig(id, json)
            appendLog("cloud config saved: $id")
        }
    }

    fun testBackend(id: String, json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            appendLog("testing $id…")
            val b = clouds.build(id, json, File(settings.backupDir()))
            val ok = try {
                b?.test() == true
            } catch (e: Exception) {
                appendLog("$id error: ${e.message}")
                false
            }
            appendLog(if (ok) "$id: connection OK" else "$id: connection FAILED")
        }
    }

    fun cloudUpload() {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "uploading to cloud…")
            try {
                val backend = clouds.active()
                val root = engine.backupRoot()
                val n = sync.uploadDir(root, backend, "") { appendLog(it) }
                appendLog("uploaded $n files → ${backend.displayName}")
                setJob(false, 1f, "uploaded $n files")
            } catch (e: Exception) {
                appendLog("upload FAILED: ${e.message}")
                setJob(false, 0f, "failed")
            }
        }
    }

    fun cloudDownload() {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "downloading from cloud…")
            try {
                val backend = clouds.active()
                val n = sync.downloadDir(backend, "", File(settings.backupDir())) { appendLog(it) }
                appendLog("downloaded $n files ← ${backend.displayName}")
                setJob(false, 1f, "downloaded $n files")
                records.value = dao.all()
            } catch (e: Exception) {
                appendLog("download FAILED: ${e.message}")
                setJob(false, 0f, "failed")
            }
        }
    }

    fun shareApk(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(settings.backupDir(), pkg)
            val first = apk.backedUpApks(dir).firstOrNull()
            if (first != null) {
                context.startActivity(installer.shareIntent(first).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                appendLog("no backed-up APK for $pkg — back up first")
            }
        }
    }
}
