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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshbackup.app.data.Schedule
import com.freshbackup.app.ui.BackupViewModel
import java.text.DateFormat
import java.util.Date

val CLOUD_TARGETS = listOf("", "local", "webdav", "s3", "ftp", "sftp", "smb")

@Composable
fun SchedulesScreen(vm: BackupViewModel) {
    val list by vm.schedList.collectAsState()
    var editing by remember { mutableStateOf<Schedule?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = {
                editing = Schedule(name = "Nightly", intervalHours = 24, chargingOnly = true)
            }) {
                Icon(Icons.Filled.Add, null)
                Text(" New schedule")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Schedules", style = MaterialTheme.typography.headlineSmall)
            Text("Each schedule backs up its own apps — optionally straight to cloud.", style = MaterialTheme.typography.bodySmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list, key = { it.id }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name.ifEmpty { "Schedule ${s.id}" }, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "every ${s.intervalHours}h · ${s.packages().size} apps" +
                                        (if (s.cloudBackend.isNotEmpty()) " → ${s.cloudBackend}" else "") +
                                        if (s.lastRun > 0) "\nlast: " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(s.lastRun)) else "",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Switch(checked = s.enabled, onCheckedChange = {
                                vm.saveSchedule(s.copy(enabled = it), s.packages())
                            })
                            IconButton(onClick = { editing = s }) { Text("✎") }
                            IconButton(onClick = { vm.deleteSchedule(s) }) { Icon(Icons.Filled.Delete, "delete") }
                        }
                    }
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    editing?.let { draft ->
        ScheduleEditor(vm = vm, initial = draft, onClose = { editing = null })
    }
}

@Composable
private fun ScheduleEditor(vm: BackupViewModel, initial: Schedule, onClose: () -> Unit) {
    val apps by vm.apps.collectAsState()
    var name by remember { mutableStateOf(initial.name) }
    var hours by remember { mutableStateOf(initial.intervalHours.toString()) }
    var charging by remember { mutableStateOf(initial.chargingOnly) }
    var cloud by remember { mutableStateOf(initial.cloudBackend) }
    var picked by remember { mutableStateOf(initial.packages().toSet()) }
    var showApps by remember { mutableStateOf(false) }
    var cloudMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (initial.id == 0L) "New schedule" else "Edit schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, { Text("Name") }, Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(hours, { hours = it.filter { c -> c.isDigit() }.take(3) }, { Text("Every N hours") }, Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Charging only", Modifier.weight(1f))
                    Switch(charging, { charging = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cloud: ${cloud.ifEmpty { "none" }}", Modifier.weight(1f))
                    OutlinedButton(onClick = { cloudMenu = true }) { Text("Change") }
                    DropdownMenu(cloudMenu, { cloudMenu = false }) {
                        CLOUD_TARGETS.forEach { t ->
                            DropdownMenuItem({ Text(t.ifEmpty { "none" }) }, { cloud = t; cloudMenu = false })
                        }
                    }
                }
                OutlinedButton(onClick = { showApps = !showApps }, Modifier.fillMaxWidth()) {
                    Text("${picked.size} apps selected")
                }
                if (showApps) {
                    LazyColumn(Modifier.fillMaxWidth().height(220.dp)) {
                        items(apps.filter { !it.isSystem }, key = { it.packageName }) { app ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(picked.contains(app.packageName), {
                                    picked = if (it) picked + app.packageName else picked - app.packageName
                                })
                                Text(app.label, Modifier.weight(1f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.saveSchedule(
                    initial.copy(
                        name = name.ifBlank { "Schedule" },
                        intervalHours = hours.toIntOrNull()?.coerceIn(1, 720) ?: 24,
                        chargingOnly = charging,
                        cloudBackend = cloud
                    ),
                    picked.toList()
                )
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } }
    )
}
