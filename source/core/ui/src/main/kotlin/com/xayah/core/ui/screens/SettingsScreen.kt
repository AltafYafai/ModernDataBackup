package com.xayah.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xayah.core.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val isRoot by viewModel.isRootGranted.collectAsState()
    val rootType by viewModel.rootType.collectAsState()
    val selinuxMode by viewModel.selinuxMode.collectAsState()

    var backupPathInput by remember(settings.backupPath) { mutableStateOf(settings.backupPath) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Storage & Compression Section
        item {
            Text(
                text = "Storage & Compression",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = backupPathInput,
                        onValueChange = {
                            backupPathInput = it
                            viewModel.updateSettings(backupPath = it)
                        },
                        label = { Text("Backup Location") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "ZSTD Compression Level",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Level ${settings.compressionLevel.toInt()} (Balanced)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = settings.compressionLevel,
                            onValueChange = { viewModel.updateSettings(compressionLevel = it) },
                            valueRange = 1f..19f,
                            steps = 17
                        )
                    }
                }
            }
        }

        // Scope Settings Section (Swift Backup style)
        item {
            Text(
                text = "Backup & Restore Scope",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingToggleRow(
                        title = "Include APK Files",
                        subtitle = "Base APK and split configuration APKs",
                        checked = settings.includeApk,
                        onCheckedChange = { viewModel.updateSettings(includeApk = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "Internal App Data (/data/data)",
                        subtitle = "Databases, shared preferences, and private files",
                        checked = settings.includeData,
                        onCheckedChange = { viewModel.updateSettings(includeData = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "Device Protected Data (/data/user_de)",
                        subtitle = "Direct boot and encryption-aware storage",
                        checked = settings.includeDeData,
                        onCheckedChange = { viewModel.updateSettings(includeDeData = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "External App Data & OBB",
                        subtitle = "/sdcard/Android/data and expansion assets",
                        checked = settings.includeExtData,
                        onCheckedChange = { viewModel.updateSettings(includeExtData = it, includeObb = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "Runtime Permissions",
                        subtitle = "Save and auto-grant application runtime permissions",
                        checked = settings.includePermissions,
                        onCheckedChange = { viewModel.updateSettings(includePermissions = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "Include System Apps",
                        subtitle = "Show and back up pre-installed system packages",
                        checked = settings.includeSystem,
                        onCheckedChange = { viewModel.updateSettings(includeSystem = it, context = context) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "Scheduled Auto-Backup",
                        subtitle = "Automatically create daily snapshots while charging",
                        checked = settings.autoBackup,
                        onCheckedChange = { viewModel.updateSettings(autoBackup = it) }
                    )
                }
            }
        }

        // App Info & Diagnostics
        item {
            Text(
                text = "Diagnostics & System",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        item {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "ModernDataBackup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Version 1.0.0 (SwiftBackup-grade Root Engine)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Root Engine", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isRoot) rootType else "Not Rooted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "SELinux Status", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = selinuxMode,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Compression Engine", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "Native ZSTD v1.5.5",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
