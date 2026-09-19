package com.freshbackup.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freshbackup.app.data.AppConfig
import com.freshbackup.app.data.BackupRecord
import com.freshbackup.app.data.backup.AppInfo
import com.freshbackup.app.ui.AppIcon
import com.freshbackup.app.ui.BackupViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private enum class AppFilter { ALL, USER, SYSTEM, BACKED_UP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: BackupViewModel) {
    val apps by vm.apps.collectAsState()
    val records by vm.records.collectAsState()
    val selected by vm.selected.collectAsState()
    val job by vm.job.collectAsState()
    val rooted by vm.rooted.collectAsState()
    val labelList by vm.labels.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.ALL) }
    var activeLabel by remember { mutableStateOf<String?>(null) }
    var detailPkg by remember { mutableStateOf<String?>(null) }

    val backedUp = remember(records) { records.map { it.packageName }.toSet() }
    val pkgLabels = remember(records) {
        records.groupBy({ it.packageName }, { it.labels }).mapValues { e ->
            e.value.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        }
    }

    val shown = remember(apps, query, filter, activeLabel, backedUp, pkgLabels) {
        apps.filter { app ->
            when (filter) {
                AppFilter.ALL -> true
                AppFilter.USER -> !app.isSystem
                AppFilter.SYSTEM -> app.isSystem
                AppFilter.BACKED_UP -> app.packageName in backedUp
            }
        }.filter { app ->
            activeLabel == null || pkgLabels[app.packageName]?.contains(activeLabel) == true
        }.filter {
            query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (selected.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { vm.backupSelected() }) {
                    Text("Back up ${selected.size}")
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchBar(
                query = query,
                onQueryChange = { query = it },
                onSearch = {},
                active = false,
                onActiveChange = {},
                placeholder = { Text("Search ${apps.size} apps") },
                modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp)
            ) {}
            LazyRow(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppFilter.entries.toList()) { f ->
                    FilterChip(
                        selected = filter == f,
                        onClick = { filter = f },
                        label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
                items(labelList) { label ->
                    FilterChip(
                        selected = activeLabel == label,
                        onClick = { activeLabel = if (activeLabel == label) null else label },
                        label = { Text("#$label") }
                    )
                }
            }
            Text(
                text = "${shown.size} shown · " + when (rooted) {
                    true -> "rooted"
                    false -> "no root (APKs only)"
                    null -> "checking root…"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(16.dp, 0.dp, 16.dp, 4.dp)
            )
            if (job.running) {
                Column(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) {
                    Text(job.status, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(progress = job.progress, modifier = Modifier.fillMaxWidth())
                }
            }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(shown, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        checked = app.packageName in selected,
                        backedUp = app.packageName in backedUp,
                        load = vm::appIcon,
                        onToggle = { vm.toggleSelect(app.packageName) },
                        onOpen = { detailPkg = app.packageName }
                    )
                }
                item { Spacer(Modifier.height(90.dp)) }
            }
        }
    }

    detailPkg?.let { pkg ->
        val app = apps.firstOrNull { it.packageName == pkg }
        if (app != null) {
            AppDetailSheet(
                vm = vm,
                app = app,
                onClose = { detailPkg = null }
            )
        } else {
            detailPkg = null
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    backedUp: Boolean,
    load: suspend (String) -> androidx.compose.ui.graphics.ImageBitmap?,
    onToggle: () -> Unit,
    onOpen: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp).clickable { onOpen() },
        colors = if (checked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.packageName, app.label, load)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(
                    "apk ${app.apkBytes / 1024} KB · data ${app.dataBytes / 1024} KB" + if (backedUp) " · backed up" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppDetailSheet(vm: BackupViewModel, app: AppInfo, onClose: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val records by vm.records.collectAsState()
    val backupDir by vm.backupDir.collectAsState()
    var config by remember(app.packageName) { mutableStateOf<AppConfig?>(null) }
    var labelsText by remember { mutableStateOf("") }
    var loadedLabels by remember { mutableStateOf(false) }

    LaunchedEffect(app.packageName) {
        val c = vm.configFor(app.packageName)
        config = c
        labelsText = c.labels
        loadedLabels = true
    }

    val versions = remember(records, app.packageName) {
        records.filter { it.packageName == app.packageName }
    }
    val parts = remember(backupDir, app.packageName, records) {
        partSizes(File(backupDir, app.packageName))
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.packageName, app.label, vm::appIcon, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleLarge)
                    Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    Text("${app.versionName} · apk ${app.apkBytes / 1024} KB · data ${app.dataBytes / 1024} KB", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = { vm.shareApk(app.packageName) }) {
                    Icon(Icons.Filled.Share, "share APK")
                }
            }

            if (parts.isNotEmpty()) {
                Text("Backup contents", style = MaterialTheme.typography.titleSmall)
                parts.forEach { (name, bytes) ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${bytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            config?.let { c ->
                Text("Backup contents selection", style = MaterialTheme.typography.titleSmall)
                ToggleRow("APK", c.includeApk) { config = c.copy(includeApk = it); vm.saveConfig(c.copy(includeApk = it)) }
                ToggleRow("App data", c.includeData) { config = c.copy(includeData = it); vm.saveConfig(c.copy(includeData = it)) }
                ToggleRow("Protected data", c.includeDe) { config = c.copy(includeDe = it); vm.saveConfig(c.copy(includeDe = it)) }
                ToggleRow("External data", c.includeExt) { config = c.copy(includeExt = it); vm.saveConfig(c.copy(includeExt = it)) }
                ToggleRow("Media", c.includeMedia) { config = c.copy(includeMedia = it); vm.saveConfig(c.copy(includeMedia = it)) }
                ToggleRow("OBB", c.includeObb) { config = c.copy(includeObb = it); vm.saveConfig(c.copy(includeObb = it)) }
                ToggleRow("Identity (SSAID/keys)", c.includeIdentity) { config = c.copy(includeIdentity = it); vm.saveConfig(c.copy(includeIdentity = it)) }
            }

            if (loadedLabels) {
                OutlinedTextField(
                    value = labelsText,
                    onValueChange = { labelsText = it },
                    label = { Text("Labels (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedButton(onClick = {
                    config?.let { vm.saveConfig(it.copy(labels = labelsText)) }
                }) { Text("Save labels") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch { sheet.hide(); onClose() }
                    vm.backupOne(app.packageName)
                }, modifier = Modifier.weight(1f)) { Text("Back up") }
                OutlinedButton(onClick = {
                    scope.launch { sheet.hide(); onClose() }
                    vm.restore(app.packageName)
                }, modifier = Modifier.weight(1f)) { Text("Restore") }
            }

            if (versions.isNotEmpty()) {
                Text("Versions (${versions.size})", style = MaterialTheme.typography.titleSmall)
                versions.take(5).forEach { rec ->
                    VersionRow(rec,
                        onNote = { vm.setNote(rec.packageName, rec.timestamp, it) },
                        onProtect = { vm.setProtected(rec.packageName, rec.timestamp, it) },
                        onDelete = { vm.deleteVersion(rec.packageName, rec.timestamp) }
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun VersionRow(rec: BackupRecord, onNote: (String) -> Unit, onProtect: (Boolean) -> Unit, onDelete: () -> Unit) {
    var note by remember(rec.timestamp, rec.note) { mutableStateOf(rec.note) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(rec.timestamp)), style = MaterialTheme.typography.bodySmall)
                    Text("${rec.totalBytes / 1024} KB", style = MaterialTheme.typography.labelSmall)
                }
                Text(if (rec.protected) "locked" else "open", style = MaterialTheme.typography.labelSmall)
                Switch(checked = rec.protected, onCheckedChange = onProtect)
            }
            OutlinedTextField(value = note, onValueChange = { note = it; onNote(it) }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (!rec.protected) {
                OutlinedButton(onClick = onDelete) { Text("Delete version") }
            }
        }
    }
}

private fun partSizes(dir: File): List<Pair<String, Long>> {
    if (!dir.exists()) return emptyList()
    val names = mapOf(
        "base.apk" to "APK", "data.tar.gz" to "App data", "data_de.tar.gz" to "Protected data",
        "ext.tar.gz" to "External data", "media.tar.gz" to "Media", "obb.tar.gz" to "OBB",
        "keystore.tar.gz" to "Keystore"
    )
    return dir.listFiles()
        ?.filter { it.isFile && (names.containsKey(it.name) || it.name.endsWith(".apk")) }
        ?.map { (names[it.name] ?: it.name) to it.length() }
        ?: emptyList()
}
