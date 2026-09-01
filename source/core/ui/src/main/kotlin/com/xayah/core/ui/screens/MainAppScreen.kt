package com.xayah.core.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xayah.core.ui.viewmodel.MainViewModel
import com.xayah.core.ui.viewmodel.MainViewModelFactory

sealed class NavTab(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Dashboard : NavTab("Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    object Apps : NavTab("Apps", Icons.Filled.Apps, Icons.Outlined.Apps)
    object Cloud : NavTab("Cloud", Icons.Filled.Cloud, Icons.Outlined.Cloud)
    object History : NavTab("History", Icons.Filled.History, Icons.Outlined.History)
    object Settings : NavTab("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(LocalContext.current))
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf<NavTab>(NavTab.Dashboard) }
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.initData(context)
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    val tabs = listOf(
        NavTab.Dashboard,
        NavTab.Apps,
        NavTab.Cloud,
        NavTab.History,
        NavTab.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Modern Data Backup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = selectedTab.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.scanInstalledApps(context)
                        viewModel.loadHistory()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                is NavTab.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToApps = { selectedTab = NavTab.Apps },
                    onNavigateToCloud = { selectedTab = NavTab.Cloud },
                    onNavigateToHistory = { selectedTab = NavTab.History },
                    onNavigateToSettings = { selectedTab = NavTab.Settings }
                )
                is NavTab.Apps -> AppsScreen(viewModel = viewModel)
                is NavTab.Cloud -> CloudScreen(viewModel = viewModel)
                is NavTab.History -> HistoryScreen(viewModel = viewModel)
                is NavTab.Settings -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
