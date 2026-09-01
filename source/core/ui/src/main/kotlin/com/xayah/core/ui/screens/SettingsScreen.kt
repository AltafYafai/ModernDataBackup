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

        // Scope Settings Section
        item {
            Text(
                text = "Backup Scope",
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
                        title = "Include Application Data",
                        subtitle = "Back up databases, shared prefs, and internal files",
                        checked = settings.includeData,
                        onCheckedChange = { viewModel.updateSettings(includeData = it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingToggleRow(
                        title = "Include APK Files",
                        subtitle = "Save base and split APK packages",
                        checked = settings.includeApk,
                        onCheckedChange = { viewModel.updateSettings(includeApk = it) }
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
                text = "About & Diagnostics",
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
                        text = "Version 1.0.0 (Material You 3)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Root Access", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isRoot) "Granted" else "Not available (Standard Mode)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
