package com.xayah.core.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

data class AppUiModel(
    val id: String,
    val name: String,
    val packageName: String,
    val size: String,
    val isSystem: Boolean,
    val isBackedUp: Boolean,
    val isSelected: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: All, 1: User, 2: System, 3: Backed Up

    val sampleApps = remember {
        mutableStateListOf(
            AppUiModel("1", "WhatsApp", "com.whatsapp", "1.4 GB", isSystem = false, isBackedUp = true),
            AppUiModel("2", "Telegram", "org.telegram.messenger", "850 MB", isSystem = false, isBackedUp = true),
            AppUiModel("3", "Chrome", "com.android.chrome", "320 MB", isSystem = true, isBackedUp = false),
            AppUiModel("4", "Spotify", "com.spotify.music", "512 MB", isSystem = false, isBackedUp = true),
            AppUiModel("5", "YouTube", "com.google.android.youtube", "640 MB", isSystem = true, isBackedUp = false),
            AppUiModel("6", "Discord", "com.discord", "420 MB", isSystem = false, isBackedUp = false),
            AppUiModel("7", "Settings", "com.android.settings", "12 MB", isSystem = true, isBackedUp = true),
            AppUiModel("8", "Twitter / X", "com.twitter.android", "290 MB", isSystem = false, isBackedUp = true),
            AppUiModel("9", "Camera", "com.android.camera2", "45 MB", isSystem = true, isBackedUp = false),
            AppUiModel("10", "Signal", "org.thoughtcrime.securesms", "380 MB", isSystem = false, isBackedUp = true)
        )
    }

    val filteredApps = sampleApps.filter { app ->
        val matchesQuery = app.name.contains(searchQuery, ignoreCase = true) ||
                app.packageName.contains(searchQuery, ignoreCase = true)
        val matchesFilter = when (selectedFilter) {
            1 -> !app.isSystem
            2 -> app.isSystem
            3 -> app.isBackedUp
            else -> true
        }
        matchesQuery && matchesFilter
    }

    val selectedCount = sampleApps.count { it.isSelected }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search apps or packages...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        // Filter Chips Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf("All", "User", "System", "Backed Up")
            filters.forEachIndexed { index, label ->
                FilterChip(
                    selected = selectedFilter == index,
                    onClick = { selectedFilter = index },
                    label = { Text(label) }
                )
            }
        }

        // Selection & Batch Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Checkbox(
                    checked = selectedCount > 0 && selectedCount == filteredApps.size,
                    onCheckedChange = { checked ->
                        sampleApps.indices.forEach { i ->
                            sampleApps[i] = sampleApps[i].copy(isSelected = checked)
                        }
                    }
                )
                Text(
                    text = if (selectedCount > 0) "$selectedCount selected" else "${filteredApps.size} apps",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (selectedCount > 0) {
                Button(
                    onClick = { /* Batch backup */ },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Backup ($selectedCount)")
                }
            }
        }

        // App List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredApps, key = { it.id }) { app ->
                AppCardItem(
                    app = app,
                    onToggleSelect = {
                        val index = sampleApps.indexOfFirst { it.id == app.id }
                        if (index != -1) {
                            sampleApps[index] = sampleApps[index].copy(isSelected = !app.isSelected)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AppCardItem(
    app: AppUiModel,
    onToggleSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surface
        ),
        onClick = onToggleSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Checkbox(
                checked = app.isSelected,
                onCheckedChange = { onToggleSelect() }
            )

            // App Icon Placeholder
            Surface(
                shape = CircleShape,
                color = if (app.isSystem) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = app.name.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (app.isSystem) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (app.isSystem) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "SYS",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${app.size} • ${if (app.isBackedUp) "Backed up" else "No backup"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (app.isBackedUp) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = { /* Backup single */ }) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Backup",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
