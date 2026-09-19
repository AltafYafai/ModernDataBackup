package com.freshbackup.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.freshbackup.app.core.RootShell
import com.freshbackup.app.data.BackupDao
import com.freshbackup.app.data.BackupRecord
import com.freshbackup.app.data.Settings
import com.freshbackup.app.data.backup.Apk
import com.freshbackup.app.data.backup.AppInfo
import com.freshbackup.app.data.backup.BackupOptions
import com.freshbackup.app.data.backup.Engine
import com.freshbackup.app.data.backup.Installer
import com.freshbackup.app.data.backup.Medium
import com.freshbackup.app.data.work.BackupWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class JobState(
    val running: Boolean = false,
    val progress: Float = 0f,
    val status: String = "",
    val log: List<String> = emptyList()
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apk: Apk,
    private val engine: Engine,
    private val installer: Installer,
    private val medium: Medium,
    private val dao: BackupDao,
    private val settings: Settings
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
        }
    }

    fun toggleSelect(pkg: String) {
        val cur = selected.value.toMutableSet()
        if (!cur.add(pkg)) cur.remove(pkg)
        selected.value = cur
    }

    private fun setJob(running: Boolean, progress: Float = 0f, status: String = "") {
        job.value = job.value.copy(running = running, progress = progress, status = status)
    }

    private fun appendLog(line: String) {
        job.value = job.value.copy(log = (job.value.log + line).takeLast(200))
    }

    fun backupSelected(opts: BackupOptions = BackupOptions()) {
        val pkgs = selected.value.toList().ifEmpty { return }
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "starting…")
            pkgs.forEachIndexed { i, pkg ->
                setJob(true, i.toFloat() / pkgs.size, pkg)
                val r = engine.backupApp(pkg, opts) { appendLog("[$pkg] $it") }
                appendLog("[$pkg] ${r.getOrElse { "FAILED: ${it.message}" }}")
            }
            setJob(false, 1f, "done")
            records.value = dao.all()
            apps.value = apk.installed()
        }
    }

    fun restore(pkg: String, opts: BackupOptions = BackupOptions()) {
        viewModelScope.launch(Dispatchers.IO) {
            setJob(true, 0f, "restoring $pkg…")
            val r = engine.restoreApp(pkg, opts, installer) { appendLog("[$pkg] $it") }
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
            }
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

    fun saveSettings(dir: String, keep: Int, passphrase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setBackupDir(dir.ifBlank { Settings.DEFAULT_DIR })
            settings.setKeepVersions(keep)
            settings.setPassphrase(passphrase)
            refresh()
        }
    }

    fun shareApk(pkg: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = File(settings.backupDir(), pkg)
            val first = apk.backedUpApks(dir).firstOrNull()
            if (first != null) {
                context.startActivity(installer.shareIntent(first).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } else {
                appendLog("no backed-up APK for $pkg — back up first")
            }
        }
    }
}
