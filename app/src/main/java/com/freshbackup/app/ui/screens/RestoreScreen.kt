package com.freshbackup.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshbackup.app.data.BackupRecord
import com.freshbackup.app.ui.BackupViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun RestoreScreen(vm: BackupViewModel) {
    val records by vm.records.collectAsState()
    val grouped = remember(records) { records.groupBy { it.packageName }.toList().sortedBy { -it.second.maxOf { r -> r.timestamp } } }
    var confirmDelete by remember { mutableStateOf<BackupRecord?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Backups", style = MaterialTheme.typography.headlineSmall)
        Text("${records.size} versions · ${grouped.size} apps", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            grouped.forEach { (pkg, versions) ->
                item(key = pkg) {
                    Text(versions.first().label.ifEmpty { pkg }, style = MaterialTheme.typography.titleSmall)
                    Text(pkg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                items(versions, key = { it.timestamp }) { rec ->
                    VersionCard(
                        rec = rec,
                        onRestore = { vm.restore(pkg) },
                        onDelete = { confirmDelete = rec }
                    )
                }
            }
        }
    }

    confirmDelete?.let { rec ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete backup?") },
            text = { Text("Protected backups cannot be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteVersion(rec.packageName, rec.timestamp)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun VersionCard(rec: BackupRecord, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getDateTimeInstance().format(Date(rec.timestamp)), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${rec.totalBytes / 1024} KB" +
                            (if (rec.hasApk) " · apk" else "") +
                            (if (rec.hasData) " · data" else "") +
                            (if (rec.protected) " · protected" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    if (rec.note.isNotEmpty()) Text(rec.note, style = MaterialTheme.typography.bodySmall)
                }
                if (!rec.protected) {
                    IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "delete") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRestore) {
                    Icon(Icons.Filled.Refresh, null)
                    Text(" Restore")
                }
            }
        }
    }
}
