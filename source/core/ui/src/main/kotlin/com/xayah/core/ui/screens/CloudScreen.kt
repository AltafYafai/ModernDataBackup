package com.xayah.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xayah.core.ui.viewmodel.MainViewModel

@Composable
fun CloudScreen(
    viewModel: MainViewModel
) {
    val serverHost by viewModel.serverHost.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val remotePath by viewModel.remotePath.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val connResult by viewModel.connectionResult.collectAsState()

    var hostInput by remember(serverHost) { mutableStateOf(serverHost) }
    var portInput by remember(serverPort) { mutableStateOf(serverPort) }
    var pathInput by remember(remotePath) { mutableStateOf(remotePath) }
    var selectedProtocol by remember { mutableStateOf(0) } // 0: SMB, 1: SFTP, 2: WebDAV, 3: Telegram, 4: Local

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cloud Provider Selection
        item {
            Text(
                text = "Storage Medium & Cloud Destination",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val protocols = listOf("SMB/CIFS", "SFTP", "WebDAV", "Telegram", "Local")
                protocols.forEachIndexed { index, name ->
                    FilterChip(
                        selected = selectedProtocol == index,
                        onClick = {
                            selectedProtocol = index
                            when (index) {
                                0 -> { hostInput = "192.168.1.100"; portInput = "445"; pathInput = "/backups/android" }
                                1 -> { hostInput = "backup.server.com"; portInput = "22"; pathInput = "/home/backups" }
                                2 -> { hostInput = "dav.nextcloud.com"; portInput = "443"; pathInput = "/remote.php/webdav" }
                                3 -> { hostInput = "api.telegram.org"; portInput = "443"; pathInput = "Bot Token & Chat ID" }
                                4 -> { hostInput = "localhost"; portInput = "0"; pathInput = "/storage/emulated/0/ModernDataBackup" }
                            }
                        },
                        label = { Text(name) }
                    )
                }
            }
        }

        // Connection Status Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (connResult) {
                        true -> MaterialTheme.colorScheme.primaryContainer
                        false -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = when (connResult) {
                            true -> MaterialTheme.colorScheme.primary
                            false -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.secondary
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (connResult == false) Icons.Default.ErrorOutline else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedProtocol == 3) "Telegram Cloud Destination" else "Remote Sync Status",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when (connResult) {
                                true -> "Ready: $hostInput:$portInput"
                                false -> "Connection Check Failed"
                                else -> "Configured: $hostInput:$portInput"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = { viewModel.testCloudConnection() },
                        enabled = !isTesting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Test")
                        }
                    }
                }
            }
        }

        // Configuration Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (selectedProtocol == 3) "Telegram Bot Configuration" else "Server Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (selectedProtocol == 3) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("Telegram Bot Token (e.g. 123456:ABC...)") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = pathInput,
                            onValueChange = { pathInput = it },
                            label = { Text("Telegram Chat ID / Channel ID") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("Server Host / IP") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = portInput,
                                onValueChange = { portInput = it },
                                label = { Text("Port") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = pathInput,
                                onValueChange = { pathInput = it },
                                label = { Text("Remote Directory") },
                                modifier = Modifier.weight(2f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = { viewModel.saveServerConfig(hostInput, portInput, pathInput) },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Configuration")
                        }
                    }
                }
            }
        }
    }
}
