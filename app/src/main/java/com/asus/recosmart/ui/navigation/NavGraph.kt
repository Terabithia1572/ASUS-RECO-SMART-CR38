package com.asus.recosmart.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.asus.recosmart.ui.about.AboutScreen
import com.asus.recosmart.ui.connection.ConnectionScreen
import com.asus.recosmart.ui.debug.DebugConsoleScreen
import com.asus.recosmart.ui.files.FileManagerScreen
import com.asus.recosmart.ui.preview.LivePreviewScreen
import com.asus.recosmart.ui.settings.SettingsScreen
import com.asus.recosmart.ui.theme.PrimaryCyan

sealed class NavItem(val route: String, val title: String, val icon: ImageVector) {
    object Connection : NavItem("connection", "Bağlantı", Icons.Default.Router)
    object LivePreview : NavItem("live_preview", "Canlı", Icons.Default.Videocam)
    object Files : NavItem("files", "Kayıtlar", Icons.Default.Folder)
    object Settings : NavItem("settings", "Ayarlar", Icons.Default.Settings)
    object DebugConsole : NavItem("debug_console", "Protokol", Icons.Default.Terminal)
    object About : NavItem("about", "Hakkında", Icons.Default.Info)
}

val bottomNavItems = listOf(
    NavItem.Connection,
    NavItem.LivePreview,
    NavItem.Files,
    NavItem.Settings,
    NavItem.DebugConsole,
    NavItem.About
)

@Composable
fun MainAppNavigation(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 8.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                bottomNavItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 10.sp,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        },
                        selected = currentRoute == item.route,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryCyan,
                            selectedTextColor = PrimaryCyan,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavItem.Connection.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavItem.Connection.route) { ConnectionScreen() }
            composable(NavItem.LivePreview.route) { LivePreviewScreen() }
            composable(NavItem.Files.route) { FileManagerScreen() }
            composable(NavItem.Settings.route) { SettingsScreen() }
            composable(NavItem.DebugConsole.route) { DebugConsoleScreen() }
            composable(NavItem.About.route) { AboutScreen() }
        }
    }
}

