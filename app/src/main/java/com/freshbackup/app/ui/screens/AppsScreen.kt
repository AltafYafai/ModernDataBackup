package com.freshbackup.app.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.freshbackup.app.data.backup.AppInfo
import com.freshbackup.app.ui.BackupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: BackupViewModel) {
    val apps by vm.apps.collectAsState()
    val records by vm.records.collectAsState()
    val selected by vm.selected.collectAsState()
    val job by vm.job.collectAsState()
    val rooted by vm.rooted.collectAsState()
    var query by remember { mutableStateOf("") }

    val backedUp = remember(records) { records.map { it.packageName }.toSet() }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, true) || it.packageName.contains(query, true)
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
            Text(
                text = when (rooted) {
                    true -> "Rooted — full data backup available"
                    false -> "No root — APKs + device data only"
                    null -> "Checking root…"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(16.dp, 4.dp)
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
                        onToggle = { vm.toggleSelect(app.packageName) },
                        onShare = { vm.shareApk(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    checked: Boolean,
    backedUp: Boolean,
    onToggle: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp).clickable { onToggle() },
        colors = if (checked) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    app.label.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                Text(
                    "apk ${app.apkBytes / 1024} KB · data ${app.dataBytes / 1024} KB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (backedUp) {
                Icon(Icons.Filled.CheckCircle, "backed up", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, "share APK")
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}
