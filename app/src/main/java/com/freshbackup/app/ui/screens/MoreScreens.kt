package com.freshbackup.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.freshbackup.app.data.cloud.LocalBackend
import com.freshbackup.app.data.cloud.WebDavBackend
import com.freshbackup.app.ui.BackupViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CloudScreen(vm: BackupViewModel, local: LocalBackend) {
    val dir by vm.backupDir.collectAsState()
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Cloud & storage", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("This device", style = MaterialTheme.typography.titleMedium)
                Text(dir, style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    scope.launch {
                        local.configure(File(dir))
                        result = if (local.test()) "local storage OK" else "local storage NOT writable"
                    }
                }) { Text("Test local storage") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WebDAV (Nextcloud / NAS)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(url, { url = it }, { Text("https://…/remote.php/dav/files/user/") }, Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(user, { user = it }, { Text("Username") }, Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(pass, { pass = it }, { Text("Password / app token") }, Modifier.fillMaxWidth(), singleLine = true)
                OutlinedButton(onClick = {
                    scope.launch {
                        result = "testing…"
                        result = if (WebDavBackend(url, user, pass).test()) "WebDAV OK" else "WebDAV connection failed"
                    }
                }) { Text("Test connection") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Coming next", style = MaterialTheme.typography.titleMedium)
                Text("Google Drive · S3 · SMB · SFTP · FTP — same upload/download interface, tracked for v1.1.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (result.isNotEmpty()) Text(result, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun SettingsScreen(vm: BackupViewModel) {
    val dir by vm.backupDir.collectAsState()
    val keep by vm.keepVersions.collectAsState()
    var dirText by remember(dir) { mutableStateOf(dir) }
    var keepText by remember(keep) { mutableStateOf(keep.toString()) }
    var passText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Backup location", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(dirText, { dirText = it }, { Text("/sdcard/FreshBackup") }, Modifier.fillMaxWidth(), singleLine = true)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Versions & encryption", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(keepText, { keepText = it.filter { c -> c.isDigit() }.take(2) }, { Text("Keep versions (1–20)") }, Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(passText, { passText = it }, { Text("Extra passphrase (optional)") }, Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { vm.saveSettings(dirText, keepText.toIntOrNull() ?: 3, passText) }) {
                    Text("Save")
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Text("Fresh Backup 1.0.0 — APKs without root, full app data with root, SMS/calls/wallpaper/WiFi/folders, schedules and WebDAV.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}
