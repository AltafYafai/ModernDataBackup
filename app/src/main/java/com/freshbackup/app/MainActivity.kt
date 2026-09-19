package com.freshbackup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.freshbackup.app.data.cloud.LocalBackend
import com.freshbackup.app.ui.BackupViewModel
import com.freshbackup.app.ui.FreshTheme
import com.freshbackup.app.ui.screens.AppsScreen
import com.freshbackup.app.ui.screens.CloudScreen
import com.freshbackup.app.ui.screens.DeviceScreen
import com.freshbackup.app.ui.screens.HomeScreen
import com.freshbackup.app.ui.screens.RestoreScreen
import com.freshbackup.app.ui.screens.SchedulesScreen
import com.freshbackup.app.ui.screens.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var localBackend: LocalBackend

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FreshTheme {
                FreshNav(localBackend)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
private fun FreshNav(localBackend: LocalBackend) {
    val nav = rememberNavController()
    val vm: BackupViewModel = hiltViewModel()
    // Re-check root + permissions every time the app comes to foreground
    // (e.g. returning from the All-files-access system screen).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refresh()
                vm.refreshPerms()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val tabs = listOf(
        Tab("home", "Home") { Icon(Icons.Filled.Home, null) },
        Tab("apps", "Apps") { Icon(Icons.Filled.PhoneAndroid, null) },
        Tab("restore", "Restore") { Icon(Icons.Filled.Restore, null) },
        Tab("cloud", "Cloud") { Icon(Icons.Filled.Cloud, null) },
        Tab("settings", "Settings") { Icon(Icons.Filled.Settings, null) }
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val current by nav.currentBackStackEntryAsState()
                val route = current?.destination?.route
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = { nav.navigate(tab.route) { launchSingleTop = true } },
                        icon = tab.icon,
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(vm, go = { nav.navigate(it) { launchSingleTop = true } }) }
            composable("apps") { AppsScreen(vm) }
            composable("restore") { RestoreScreen(vm) }
            composable("device") { DeviceScreen(vm, onOpenCloud = { nav.navigate("cloud") }) }
            composable("cloud") { CloudScreen(vm, localBackend) }
            composable("schedules") { SchedulesScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
