package com.xayah.core.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xayah.core.data.repository.*
import com.xayah.core.database.entity.AppEntity
import com.xayah.core.database.entity.TaskEntity
import com.xayah.core.util.PathUtil
import com.xayah.core.util.RootUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiSettings(
    val backupPath: String = "/sdcard/moderndatabackup",
    val compressionLevel: Float = 3f,
    val includeData: Boolean = true,
    val includeApk: Boolean = true,
    val includeDeData: Boolean = true,
    val includeExtData: Boolean = true,
    val includeMedia: Boolean = true,
    val includeObb: Boolean = true,
    val includePermissions: Boolean = true,
    val includeSsaid: Boolean = true,
    val includeSystem: Boolean = false,
    val autoBackup: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val appsRepo: AppsRepo,
    private val backupRestoreEngine: BackupRestoreEngine,
    private val taskRepository: TaskRepository
) : ViewModel() {

    private val _installedApps = MutableStateFlow<List<AppEntity>>(emptyList())
    val installedApps: StateFlow<List<AppEntity>> = _installedApps.asStateFlow()

    private val _availableBackups = MutableStateFlow<List<BackupManifest>>(emptyList())
    val availableBackups: StateFlow<List<BackupManifest>> = _availableBackups.asStateFlow()

    private val _storageLocations = MutableStateFlow<List<StorageLocation>>(emptyList())
    val storageLocations: StateFlow<List<StorageLocation>> = _storageLocations.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _storageInfo = MutableStateFlow(
        StorageSpaceInfo(32_000_000_000L, 128_000_000_000L, "32.0 GB", "128.0 GB", 0.75f, "/sdcard/moderndatabackup")
    )
    val storageInfo: StateFlow<StorageSpaceInfo> = _storageInfo.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

    private val _rootType = MutableStateFlow("Detecting...")
    val rootType: StateFlow<String> = _rootType.asStateFlow()

    private val _selinuxMode = MutableStateFlow("Enforcing")
    val selinuxMode: StateFlow<String> = _selinuxMode.asStateFlow()

    private val _history = MutableStateFlow<List<TaskEntity>>(emptyList())
    val history: StateFlow<List<TaskEntity>> = _history.asStateFlow()

    private val _currentOperation = MutableStateFlow<String?>(null)
    val currentOperation: StateFlow<String?> = _currentOperation.asStateFlow()

    private val _operationProgress = MutableStateFlow(0f)
    val operationProgress: StateFlow<Float> = _operationProgress.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _settings = MutableStateFlow(UiSettings())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    // Cloud connection state
    private val _serverHost = MutableStateFlow("192.168.1.100")
    val serverHost: StateFlow<String> = _serverHost.asStateFlow()

    private val _serverPort = MutableStateFlow("445")
    val serverPort: StateFlow<String> = _serverPort.asStateFlow()

    private val _remotePath = MutableStateFlow("/backups/android")
    val remotePath: StateFlow<String> = _remotePath.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _connectionResult = MutableStateFlow<Boolean?>(null)
    val connectionResult: StateFlow<Boolean?> = _connectionResult.asStateFlow()

    fun initData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            refreshRootStatus()
            val primaryDir = PathUtil.DEFAULT_BACKUP_PATH
            _settings.value = _settings.value.copy(backupPath = primaryDir)
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context, primaryDir)
            _storageLocations.value = backupRestoreEngine.getAvailableStorageLocations(context)
            loadHistory()
            scanInstalledApps(context)
            loadAvailableBackups(context)
        }
    }

    fun refreshRootStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val isRoot = RootUtil.isRootAvailable(forceCheck = true)
            _isRootGranted.value = isRoot
            _rootType.value = RootUtil.getRootType()
            _selinuxMode.value = RootUtil.getSelinuxMode()
        }
    }

    fun requestRootAccess() {
        viewModelScope.launch(Dispatchers.IO) {
            val granted = RootUtil.requestRoot()
            _isRootGranted.value = granted
            _rootType.value = RootUtil.getRootType()
            _selinuxMode.value = RootUtil.getSelinuxMode()
            _snackbarMessage.value = if (granted) "Root access granted (${_rootType.value})!" else "Root permission denied"
        }
    }

    fun switchStorage(context: Context, location: StorageLocation) {
        viewModelScope.launch(Dispatchers.IO) {
            _settings.value = _settings.value.copy(backupPath = location.path)
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context, location.path)
            _snackbarMessage.value = "Storage switched to: ${location.name}"
            loadAvailableBackups(context)
            scanInstalledApps(context)
        }
    }

    fun setCustomBackupPath(context: Context, customPath: String) {
        if (customPath.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _settings.value = _settings.value.copy(backupPath = customPath.trim())
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context, customPath.trim())
            _snackbarMessage.value = "Backup destination set to: ${customPath.trim()}"
            loadAvailableBackups(context)
            scanInstalledApps(context)
        }
    }

    fun loadAvailableBackups(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val backups = backupRestoreEngine.getAvailableBackups(_settings.value.backupPath, context)
                _availableBackups.value = backups
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun scanInstalledApps(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val apps = appsRepo.scanAndSyncInstalledApps(
                    context,
                    includeSystem = _settings.value.includeSystem
                )
                _installedApps.value = apps
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to scan apps: ${e.message}"
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun loadHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskRepository.getAllTasks().collect { list ->
                    _history.value = list
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun backupSingle(context: Context, app: AppEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentOperation.value = "Backing up ${app.label} (APK + Data + Media + Logins)..."
            _operationProgress.value = 0.3f
            val res = backupRestoreEngine.backupApp(
                context = context,
                packageName = app.packageName,
                label = app.label,
                includeApk = _settings.value.includeApk,
                includeData = _settings.value.includeData,
                includeDeData = _settings.value.includeDeData,
                includeExtData = _settings.value.includeExtData,
                includeMedia = _settings.value.includeMedia,
                includeObb = _settings.value.includeObb,
                includePermissions = _settings.value.includePermissions,
                includeSsaid = _settings.value.includeSsaid,
                customBackupPath = _settings.value.backupPath
            )
            _operationProgress.value = 1f
            _currentOperation.value = null
            if (res.isSuccess) {
                _snackbarMessage.value = "Backup completed: ${app.label}"
                val updated = _installedApps.value.map {
                    if (it.packageName == app.packageName) it.copy(enabled = true) else it
                }
                _installedApps.value = updated
            } else {
                _snackbarMessage.value = "Backup failed: ${res.exceptionOrNull()?.message}"
            }
            loadHistory()
            loadAvailableBackups(context)
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context, _settings.value.backupPath)
        }
    }

    fun restoreSingle(context: Context, packageName: String, label: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _currentOperation.value = "Restoring $label (APK + Data + Media + Logins)..."
                _operationProgress.value = 0.3f
                val res = backupRestoreEngine.restoreApp(
                    context = context,
                    packageName = packageName,
                    label = label,
                    restoreApk = _settings.value.includeApk,
                    restoreData = _settings.value.includeData,
                    restoreDeData = _settings.value.includeDeData,
                    restoreExtData = _settings.value.includeExtData,
                    restoreMedia = _settings.value.includeMedia,
                    restoreObb = _settings.value.includeObb,
                    restorePermissions = _settings.value.includePermissions,
                    restoreSsaid = _settings.value.includeSsaid,
                    customBackupPath = _settings.value.backupPath
                )
                _operationProgress.value = 1f
                _currentOperation.value = null
                if (res.isSuccess) {
                    _snackbarMessage.value = "Restored successfully: $label"
                } else {
                    _snackbarMessage.value = "Restore failed: ${res.exceptionOrNull()?.message}"
                }
                loadHistory()
                scanInstalledApps(context)
            } catch (t: Throwable) {
                _currentOperation.value = null
                _snackbarMessage.value = "Restore error: ${t.message}"
            }
        }
    }

    fun backupBatch(context: Context, apps: List<AppEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (apps.isEmpty()) return@launch
                var successCount = 0
                val total = apps.size
                apps.forEachIndexed { index, app ->
                    _currentOperation.value = "Backing up (${index + 1}/$total): ${app.label}"
                    _operationProgress.value = (index.toFloat() / total.toFloat())
                    val res = backupRestoreEngine.backupApp(
                        context = context,
                        packageName = app.packageName,
                        label = app.label,
                        includeApk = _settings.value.includeApk,
                        includeData = _settings.value.includeData,
                        includeDeData = _settings.value.includeDeData,
                        includeExtData = _settings.value.includeExtData,
                        includeMedia = _settings.value.includeMedia,
                        includeObb = _settings.value.includeObb,
                        includePermissions = _settings.value.includePermissions,
                        includeSsaid = _settings.value.includeSsaid,
                        customBackupPath = _settings.value.backupPath
                    )
                    if (res.isSuccess) successCount++
                }
                _currentOperation.value = null
                _operationProgress.value = 0f
                _snackbarMessage.value = "Batch backup complete: $successCount of $total apps backed up"
                scanInstalledApps(context)
                loadAvailableBackups(context)
                loadHistory()
                _storageInfo.value = backupRestoreEngine.getStorageSpace(context, _settings.value.backupPath)
            } catch (t: Throwable) {
                _currentOperation.value = null
                _snackbarMessage.value = "Backup error: ${t.message}"
            }
        }
    }

    fun restoreBatch(context: Context, backups: List<BackupManifest>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (backups.isEmpty()) return@launch
                var successCount = 0
                val total = backups.size
                backups.forEachIndexed { index, backup ->
                    _currentOperation.value = "Restoring (${index + 1}/$total): ${backup.label}"
                    _operationProgress.value = (index.toFloat() / total.toFloat())
                    val res = backupRestoreEngine.restoreApp(
                        context = context,
                        packageName = backup.packageName,
                        label = backup.label,
                        restoreApk = _settings.value.includeApk,
                        restoreData = _settings.value.includeData,
                        restoreDeData = _settings.value.includeDeData,
                        restoreExtData = _settings.value.includeExtData,
                        restoreMedia = _settings.value.includeMedia,
                        restoreObb = _settings.value.includeObb,
                        restorePermissions = _settings.value.includePermissions,
                        restoreSsaid = _settings.value.includeSsaid,
                        customBackupPath = _settings.value.backupPath
                    )
                    if (res.isSuccess) successCount++
                }
                _currentOperation.value = null
                _operationProgress.value = 0f
                _snackbarMessage.value = "Batch restore complete: $successCount of $total apps restored"
                scanInstalledApps(context)
                loadHistory()
            } catch (t: Throwable) {
                _currentOperation.value = null
                _snackbarMessage.value = "Batch restore error: ${t.message}"
            }
        }
    }

    fun testCloudConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            _isTestingConnection.value = true
            _connectionResult.value = null
            val port = _serverPort.value.toIntOrNull() ?: 445
            val isSuccess = backupRestoreEngine.testServerConnection(_serverHost.value, port)
            _connectionResult.value = isSuccess
            _isTestingConnection.value = false
            _snackbarMessage.value = if (isSuccess) "Connection test successful!" else "Connection failed to ${_serverHost.value}:$port"
        }
    }

    fun saveServerConfig(host: String, port: String, path: String) {
        _serverHost.value = host
        _serverPort.value = port
        _remotePath.value = path
        _snackbarMessage.value = "Configuration saved"
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                taskRepository.clearAll()
                _history.value = emptyList()
                _snackbarMessage.value = "History cleared"
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    fun updateSettings(
        backupPath: String = _settings.value.backupPath,
        compressionLevel: Float = _settings.value.compressionLevel,
        includeData: Boolean = _settings.value.includeData,
        includeApk: Boolean = _settings.value.includeApk,
        includeDeData: Boolean = _settings.value.includeDeData,
        includeExtData: Boolean = _settings.value.includeExtData,
        includeMedia: Boolean = _settings.value.includeMedia,
        includeObb: Boolean = _settings.value.includeObb,
        includePermissions: Boolean = _settings.value.includePermissions,
        includeSsaid: Boolean = _settings.value.includeSsaid,
        includeSystem: Boolean = _settings.value.includeSystem,
        autoBackup: Boolean = _settings.value.autoBackup,
        context: Context? = null
    ) {
        val oldIncludeSystem = _settings.value.includeSystem
        _settings.value = UiSettings(
            backupPath = backupPath,
            compressionLevel = compressionLevel,
            includeData = includeData,
            includeApk = includeApk,
            includeDeData = includeDeData,
            includeExtData = includeExtData,
            includeMedia = includeMedia,
            includeObb = includeObb,
            includePermissions = includePermissions,
            includeSsaid = includeSsaid,
            includeSystem = includeSystem,
            autoBackup = autoBackup
        )
        if (context != null && oldIncludeSystem != includeSystem) {
            scanInstalledApps(context)
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}
