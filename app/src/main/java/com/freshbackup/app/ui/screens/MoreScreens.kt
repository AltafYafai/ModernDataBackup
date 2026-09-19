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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.freshbackup.app.data.cloud.LocalBackend
import com.freshbackup.app.ui.BackupViewModel
import org.json.JSONObject

private data class BackendDef(val id: String, val title: String, val blurb: String)

private val BACKENDS = listOf(
    BackendDef("local", "This device", "Internal storage / SD card"),
    BackendDef("webdav", "WebDAV", "Nextcloud, ownCloud, NAS"),
    BackendDef("s3", "S3 storage", "AWS S3 or MinIO"),
    BackendDef("ftp", "FTP / FTPS", "FTP servers with TLS"),
    BackendDef("sftp", "SFTP", "SSH file transfer"),
    BackendDef("smb", "SMB", "Windows / Samba shares")
)

@Composable
fun CloudScreen(vm: BackupViewModel, local: LocalBackend) {
    val active by vm.activeBackend.collectAsState()
    val job by vm.job.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cloud & storage", style = MaterialTheme.typography.headlineSmall)
        if (job.running) {
            Text(job.status, style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = job.progress, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.cloudUpload() }, modifier = Modifier.weight(1f)) { Text("Upload now") }
            OutlinedButton(onClick = { vm.cloudDownload() }, modifier = Modifier.weight(1f)) { Text("Download") }
        }
        Text("Uploads the whole backup folder to: $active", style = MaterialTheme.typography.bodySmall)
        BACKENDS.forEach { def ->
            BackendCard(vm, def, isActive = active == def.id, onActivate = { vm.setActiveBackend(def.id) })
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun BackendCard(vm: BackupViewModel, def: BackendDef, isActive: Boolean, onActivate: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var fields by remember { mutableStateOf(mapOf<String, String>()) }
    var tls by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded && !loaded) {
            loaded = true
            try {
                val o = JSONObject(vm.cloudConfig(def.id))
                fields = fieldNames(def.id).associateWith { o.optString(it) }
                if (o.has("tls")) tls = o.optBoolean("tls", true)
            } catch (e: Exception) { }
        }
    }

    fun currentJson(): String {
        val o = JSONObject()
        fields.forEach { (k, v) -> o.put(k, v) }
        o.put("tls", tls)
        return o.toString()
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = if (isActive) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        else CardDefaults.cardColors()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(def.title + if (isActive) " · active" else "", style = MaterialTheme.typography.titleMedium)
                    Text(def.blurb, style = MaterialTheme.typography.bodySmall)
                }
                if (!isActive) {
                    OutlinedButton(onClick = onActivate) { Text("Use") }
                }
            }
            if (def.id != "local") {
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide settings" else "Settings")
                }
            }
            if (expanded) {
                fieldNames(def.id).forEach { name ->
                    OutlinedTextField(
                        value = fields[name] ?: "",
                        onValueChange = { fields = fields + (name to it) },
                        label = { Text(fieldLabel(name)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (def.id == "ftp") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Explicit TLS", Modifier.weight(1f))
                        Checkbox(tls, { tls = it })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveCloudConfig(def.id, currentJson()) }) { Text("Save") }
                    OutlinedButton(onClick = { vm.testBackend(def.id, currentJson()) }) { Text("Test") }
                }
            }
        }
    }
}

private fun fieldNames(id: String): List<String> = when (id) {
    "webdav" -> listOf("url", "user", "pass")
    "s3" -> listOf("endpoint", "region", "bucket", "access", "secret", "prefix")
    "ftp" -> listOf("host", "port", "user", "pass", "base")
    "sftp" -> listOf("host", "port", "user", "pass", "base")
    "smb" -> listOf("host", "share", "domain", "user", "pass", "base")
    else -> emptyList()
}

private fun fieldLabel(name: String): String = when (name) {
    "url" -> "URL (https://…/remote.php/dav/files/user/)"
    "user" -> "Username"
    "pass" -> "Password / token"
    "endpoint" -> "Endpoint (https://s3… or http://minio:9000)"
    "region" -> "Region (us-east-1)"
    "bucket" -> "Bucket"
    "access" -> "Access key"
    "secret" -> "Secret key"
    "prefix" -> "Prefix (FreshBackup/)"
    "host" -> "Host"
    "port" -> "Port"
    "base" -> "Base folder"
    "share" -> "Share name"
    "domain" -> "Domain (optional)"
    else -> name
}

@Composable
fun SettingsScreen(vm: BackupViewModel) {
    val dir by vm.backupDir.collectAsState()
    val keep by vm.keepVersions.collectAsState()
    val labelList by vm.labels.collectAsState()
    val context = LocalContext.current
    var dirText by remember(dir) { mutableStateOf(dir) }
    var keepText by remember(keep) { mutableStateOf(keep.toString()) }
    var passText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup location", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = dirText, onValueChange = { dirText = it }, label = { Text("/sdcard/FreshBackup") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (!vm.hasAllFiles()) {
                    OutlinedButton(onClick = { context.startActivity(vm.allFilesIntent()) }) {
                        Text("Grant all-files access")
                    }
                } else {
                    Text("All-files access granted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Versions & encryption", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = keepText, onValueChange = { keepText = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Keep versions (1–20)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = passText, onValueChange = { passText = it }, label = { Text("Extra passphrase (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { vm.saveSettings(dirText, keepText.toIntOrNull() ?: 3, passText) }) {
                    Text("Save")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Labels (${labelList.size})", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (labelList.isEmpty()) "No labels yet — add them in an app's detail sheet."
                    else labelList.joinToString(", ") { "#$it" },
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Fresh Backup 1.1.0 — APKs without root, full app data + keystore with root, SMS/calls/wallpaper/WiFi/folders, schedules, and 6 cloud targets.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}
