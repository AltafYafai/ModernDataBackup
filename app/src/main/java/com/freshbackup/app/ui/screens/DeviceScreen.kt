package com.freshbackup.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshbackup.app.ui.BackupViewModel

/** SMS, calls, wallpaper, WiFi and folders — the no-root half of Swift Backup. */
@Composable
fun DeviceScreen(vm: BackupViewModel, onOpenCloud: () -> Unit) {
    val job by vm.job.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device data", style = MaterialTheme.typography.headlineSmall)
        if (job.running) {
            Text(job.status, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = job.progress, modifier = Modifier.fillMaxWidth())
        }
        MediumRow("Messages", "Back up & restore SMS")({ vm.backupMedium("sms") }, { vm.restoreMedium("sms") })
        MediumRow("Call log", "Back up & restore calls")({ vm.backupMedium("calls") }, { vm.restoreMedium("calls") })
        MediumRow("Wallpaper", "Back up & re-apply")({ vm.backupMedium("wallpaper") }, { vm.restoreMedium("wallpaper") })
        MediumRow("WiFi networks", "Root required")({ vm.backupMedium("wifi") }, { vm.restoreMedium("wifi") })
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Cloud & folders", style = MaterialTheme.typography.titleMedium)
                Text("WebDAV upload and incremental folder backup live here next.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onOpenCloud) { Text("Open cloud") }
            }
        }
        if (job.log.isNotEmpty()) {
            Text("Log", style = MaterialTheme.typography.titleSmall)
            job.log.takeLast(30).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun MediumRow(title: String, subtitle: String, onBackup: () -> Unit, onRestore: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackup) { Text("Back up") }
                OutlinedButton(onClick = onRestore) { Text("Restore") }
            }
        }
    }
}
