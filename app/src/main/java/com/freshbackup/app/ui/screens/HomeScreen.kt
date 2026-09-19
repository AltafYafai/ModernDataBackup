package com.freshbackup.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freshbackup.app.ui.BackupViewModel
import java.text.DateFormat
import java.util.Date

/** Dashboard: storage, stats, quick actions, recent activity. */
@Composable
fun HomeScreen(vm: BackupViewModel, go: (String) -> Unit) {
    val apps by vm.apps.collectAsState()
    val records by vm.records.collectAsState()
    val storage by vm.storage.collectAsState()
    val rooted by vm.rooted.collectAsState()
    val job by vm.job.collectAsState()
    val perms by vm.perms.collectAsState()
    val context = LocalContext.current

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refreshPerms() }

    fun requestRuntime() {
        val list = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        runtimeLauncher.launch(list.toTypedArray())
    }

    val backedUpPkgs = records.map { it.packageName }.toSet()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Gradient header
        Box(
            Modifier.fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text("Fresh Backup", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
                Text(
                    when (rooted) {
                        true -> "Rooted · full protection on"
                        false -> "Standard mode · APKs + device data"
                        null -> "Checking device…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Setup blockers first — nothing can work until these are granted.
            if (!perms.allFiles || !perms.dirWritable) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Backup storage unavailable", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Fresh Backup cannot write to its folder. Grant All-files access to enable all backups.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { context.startActivity(vm.allFilesIntent()) }) {
                            Text("Grant all-files access")
                        }
                    }
                }
            }
            if (!perms.messagingOk) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Device-data permissions", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "SMS and call-log backup need runtime permission.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { requestRuntime() }) {
                            Text("Grant SMS & call permissions")
                        }
                    }
                }
            }
            // Storage card
            storage?.let { st ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Storage, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.size(8.dp))
                            Text("Backup storage", style = MaterialTheme.typography.titleSmall)
                        }
                        Text("${st.fmt(st.freeBytes)} free of ${st.fmt(st.totalBytes)}", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(progress = st.usedFraction, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                        Text(st.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
                    }
                }
            }

            // Stats row
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("${apps.size}", "apps", Modifier.weight(1f))
                StatCard("${backedUpPkgs.size}", "protected", Modifier.weight(1f))
                StatCard("${records.size}", "versions", Modifier.weight(1f))
            }

            if (job.running) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(job.status, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = job.progress, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Text("Quick actions", style = MaterialTheme.typography.titleMedium)
            val actions = listOf(
                Triple("Back up", Icons.Filled.PlayArrow, "apps"),
                Triple("Device data", Icons.Filled.PhoneAndroid, "device"),
                Triple("Cloud sync", Icons.Filled.CloudUpload, "cloud"),
                Triple("Schedules", Icons.Filled.Schedule, "schedules"),
                Triple("Root status", Icons.Filled.Security, "settings"),
                Triple("Settings", Icons.Filled.Settings, "settings")
            )
            actions.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (label, icon, route) ->
                        QuickAction(label, icon, Modifier.weight(1f)) { go(route) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            Text("Recent activity", style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty()) {
                Text("Nothing backed up yet — pick some apps and hit Back up.", style = MaterialTheme.typography.bodyMedium)
            } else {
                records.take(6).forEach { rec ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.label.ifEmpty { rec.packageName }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(rec.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text("${rec.totalBytes / 1024} KB", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Spacer(Modifier.height(90.dp))
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp, 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable { onClick() }) {
        Column(Modifier.padding(12.dp, 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}
