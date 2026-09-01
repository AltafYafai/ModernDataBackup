package com.xayah.core.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xayah.core.data.repository.AppsRepo
import com.xayah.core.data.repository.BackupRestoreEngine
import com.xayah.core.data.repository.StorageSpaceInfo
import com.xayah.core.data.repository.TaskRepository
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
    val backupPath: String = "/storage/emulated/0/ModernDataBackup",
    val compressionLevel: Float = 3f,
    val includeData: Boolean = true,
    val includeApk: Boolean = true,
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

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _storageInfo = MutableStateFlow(
        StorageSpaceInfo(32_000_000_000L, 128_000_000_000L, "32.0 GB", "128.0 GB", 0.75f)
    )
    val storageInfo: StateFlow<StorageSpaceInfo> = _storageInfo.asStateFlow()

    private val _isRootGranted = MutableStateFlow(false)
    val isRootGranted: StateFlow<Boolean> = _isRootGranted.asStateFlow()

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
            _isRootGranted.value = RootUtil.isRootAvailable()
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context)
            _settings.value = _settings.value.copy(
                backupPath = PathUtil.getPrimaryBackupDir(context).absolutePath
            )
            loadHistory()
            scanInstalledApps(context)
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
            taskRepository.getAllTasks().collect { list ->
                _history.value = list
            }
        }
    }

    fun backupSingle(context: Context, app: AppEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentOperation.value = "Backing up ${app.label}..."
            _operationProgress.value = 0.3f
            val res = backupRestoreEngine.backupApp(
                context = context,
                packageName = app.packageName,
                label = app.label,
                includeApk = _settings.value.includeApk,
                includeData = _settings.value.includeData,
                customBackupPath = _settings.value.backupPath
            )
            _operationProgress.value = 1f
            _currentOperation.value = null
            if (res.isSuccess) {
                _snackbarMessage.value = "Backup completed: ${app.label}"
                // Refresh app enabled state in list
                val updated = _installedApps.value.map {
                    if (it.packageName == app.packageName) it.copy(enabled = true) else it
                }
                _installedApps.value = updated
            } else {
                _snackbarMessage.value = "Backup failed: ${res.exceptionOrNull()?.message}"
            }
            loadHistory()
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context)
        }
    }

    fun restoreSingle(context: Context, app: AppEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentOperation.value = "Restoring ${app.label}..."
            _operationProgress.value = 0.3f
            val res = backupRestoreEngine.restoreApp(
                context = context,
                packageName = app.packageName,
                label = app.label,
                customBackupPath = _settings.value.backupPath
            )
            _operationProgress.value = 1f
            _currentOperation.value = null
            if (res.isSuccess) {
                _snackbarMessage.value = "Restored: ${app.label}"
            } else {
                _snackbarMessage.value = "Restore failed: ${res.exceptionOrNull()?.message}"
            }
            loadHistory()
        }
    }

    fun backupBatch(context: Context, apps: List<AppEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
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
                    customBackupPath = _settings.value.backupPath
                )
                if (res.isSuccess) successCount++
            }
            _currentOperation.value = null
            _operationProgress.value = 0f
            _snackbarMessage.value = "Batch backup complete: $successCount of $total apps backed up"
            scanInstalledApps(context)
            loadHistory()
            _storageInfo.value = backupRestoreEngine.getStorageSpace(context)
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
        _snackbarMessage.value = "Server configuration saved"
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.clearAll()
            _history.value = emptyList()
            _snackbarMessage.value = "History cleared"
        }
    }

    fun updateSettings(
        backupPath: String = _settings.value.backupPath,
        compressionLevel: Float = _settings.value.compressionLevel,
        includeData: Boolean = _settings.value.includeData,
        includeApk: Boolean = _settings.value.includeApk,
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
