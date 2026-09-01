package com.xayah.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xayah.core.data.repository.BackupManifest
import com.xayah.core.database.entity.AppEntity
import com.xayah.core.ui.viewmodel.MainViewModel
import com.xayah.core.util.FileUtil
import com.xayah.core.util.toDateString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val installedApps by viewModel.installedApps.collectAsState()
    val availableBackups by viewModel.availableBackups.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val currentOp by viewModel.currentOperation.collectAsState()
    val progress by viewModel.operationProgress.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Backup (Installed), 1: Restore (Backups on Storage)
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: User, 2: System, 3: Backed Up

    val selectedBackupPackages = remember { mutableStateListOf<String>() }
    val selectedRestorePackages = remember { mutableStateListOf<String>() }

    val filteredInstalledApps = installedApps.filter { app ->
        val matchesQuery = app.label.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            1 -> !app.isSystemApp
            2 -> app.isSystemApp
            3 -> app.enabled
            else -> true
        }
        matchesQuery && matchesFilter
    }

    val filteredBackups = availableBackups.filter { backup ->
        backup.label.contains(searchQuery, ignoreCase = true) ||
                backup.packageName.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Top Segmented Primary Tab (Backup vs Restore - Swift Backup Style)
        PrimaryTabRow(
            selectedTabIndex = activeTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Backup (${installedApps.size} Installed)") },
                icon = { Icon(Icons.Default.CloudUpload, contentDescription = null) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = {
                    activeTab = 1
                    viewModel.loadAvailableBackups(context)
                },
                text = { Text("Restore (${availableBackups.size} Saved)") },
                icon = { Icon(Icons.Default.Restore, contentDescription = null) }
            )
        }

        // Active Operation Progress Banner
        if (currentOp != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = currentOp ?: "Processing...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f)
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(if (activeTab == 0) "Search installed apps..." else "Search saved backups...")
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        if (activeTab == 0) {
            // Filter Chips Row for Installed Apps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    "All (${installedApps.size})",
                    "User (${installedApps.count { !it.isSystemApp }})",
                    "System (${installedApps.count { it.isSystemApp }})",
                    "Backed Up (${installedApps.count { it.enabled }})"
                )
                filters.forEachIndexed { index, label ->
                    FilterChip(
                        selected = selectedFilter == index,
                        onClick = { selectedFilter = index },
                        label = { Text(label) }
                    )
                }
            }

            // Selection & Batch Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = selectedBackupPackages.isNotEmpty() && selectedBackupPackages.size == filteredInstalledApps.size,
                        onCheckedChange = { checked ->
                            selectedBackupPackages.clear()
                            if (checked) {
                                selectedBackupPackages.addAll(filteredInstalledApps.map { it.packageName })
                            }
                        }
                    )
                    Text(
                        text = if (selectedBackupPackages.isNotEmpty()) "${selectedBackupPackages.size} selected" else "${filteredInstalledApps.size} apps found",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = {
                        viewModel.scanInstalledApps(context)
                        viewModel.loadAvailableBackups(context)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Apps")
                    }
                    if (selectedBackupPackages.isNotEmpty()) {
                        Button(
                            onClick = {
                                val targets = installedApps.filter { selectedBackupPackages.contains(it.packageName) }
                                viewModel.backupBatch(context, targets)
                            },
                            enabled = currentOp == null,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup (${selectedBackupPackages.size})")
                        }
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (filteredInstalledApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No applications found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredInstalledApps, key = { it.packageName }) { app ->
                        val isSelected = selectedBackupPackages.contains(app.packageName)
                        AppItemCard(
                            app = app,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedBackupPackages.remove(app.packageName)
                                } else {
                                    selectedBackupPackages.add(app.packageName)
                                }
                            },
                            onBackup = { viewModel.backupSingle(context, app) },
                            onRestore = { viewModel.restoreSingle(context, app.packageName, app.label) }
                        )
                    }
                }
            }
        } else {
            // Restore Tab Content (Swift Backup Style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = selectedRestorePackages.isNotEmpty() && selectedRestorePackages.size == filteredBackups.size,
                        onCheckedChange = { checked ->
                            selectedRestorePackages.clear()
                            if (checked) {
                                selectedRestorePackages.addAll(filteredBackups.map { it.packageName })
                            }
                        }
                    )
                    Text(
                        text = if (selectedRestorePackages.isNotEmpty()) "${selectedRestorePackages.size} selected" else "${filteredBackups.size} backups available",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { viewModel.loadAvailableBackups(context) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Backups")
                    }
                    if (selectedRestorePackages.isNotEmpty()) {
                        Button(
                            onClick = {
                                val targets = availableBackups.filter { selectedRestorePackages.contains(it.packageName) }
                                viewModel.restoreBatch(context, targets)
                            },
                            enabled = currentOp == null,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore (${selectedRestorePackages.size})")
                        }
                    }
                }
            }

            if (filteredBackups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "No backups found on active storage destination.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredBackups, key = { it.packageName }) { backup ->
                        val isSelected = selectedRestorePackages.contains(backup.packageName)
                        RestoreBackupCard(
                            backup = backup,
                            isSelected = isSelected,
                            onToggleSelect = {
                                if (isSelected) {
                                    selectedRestorePackages.remove(backup.packageName)
                                } else {
                                    selectedRestorePackages.add(backup.packageName)
                                }
                            },
                            onRestore = { viewModel.restoreSingle(context, backup.packageName, backup.label) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppItemCard(
    app: AppEntity,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        onClick = onToggleSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )

            Surface(
                shape = CircleShape,
                color = if (app.isSystemApp) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = app.label.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isSystemApp) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (app.isSystemApp) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "SYS",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val totalAppSize = (app.apkSize + app.dataSize + app.mediaSize + app.obbSize + app.extDataSize).coerceAtLeast(app.dataSize)
                Text(
                    text = "Total: ${FileUtil.formatBytes(totalAppSize)} • ${if (app.enabled) "Backed up" else "Not backed up"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (app.apkSize > 0) ComponentBadge("APK: ${FileUtil.formatBytes(app.apkSize)}")
                    if (app.dataSize > 0) ComponentBadge("Data: ${FileUtil.formatBytes(app.dataSize)}")
                    if (app.mediaSize > 0) ComponentBadge("Media: ${FileUtil.formatBytes(app.mediaSize)}")
                    if (app.obbSize > 0) ComponentBadge("OBB: ${FileUtil.formatBytes(app.obbSize)}")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (app.enabled) {
                    IconButton(onClick = onRestore) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = "Restore",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                IconButton(onClick = onBackup) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Backup",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun RestoreBackupCard(
    backup: BackupManifest,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        onClick = onToggleSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.FolderZip,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = backup.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = backup.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Total Archive: ${FileUtil.formatBytes(backup.totalSize)} • ${backup.backupTime.toDateString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (backup.hasApk) ComponentBadge(if (backup.apkSize > 0) "APK ${FileUtil.formatBytes(backup.apkSize)}" else "APK")
                    if (backup.hasData) ComponentBadge(if (backup.dataSize > 0) "Data ${FileUtil.formatBytes(backup.dataSize)}" else "DATA")
                    if (backup.hasMedia) ComponentBadge(if (backup.mediaSize > 0) "Media ${FileUtil.formatBytes(backup.mediaSize)}" else "MEDIA")
                    if (backup.hasObb) ComponentBadge(if (backup.obbSize > 0) "OBB ${FileUtil.formatBytes(backup.obbSize)}" else "OBB")
                    if (backup.hasSsaid) ComponentBadge("LOGINS")
                }
            }

            Button(
                onClick = onRestore,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore")
            }
        }
    }
}

@Composable
fun ComponentBadge(name: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
