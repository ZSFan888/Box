package com.proxymax.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*
import com.proxymax.ui.dashboard.DashboardScreen
import com.proxymax.ui.proxies.ProxiesScreen
import com.proxymax.ui.settings.SettingsScreen
import com.proxymax.ui.logs.LogsScreen
import com.proxymax.ui.perrapp.PerAppScreen

sealed class Screen(val route: String, val label: String, val icon: @Composable () -> Unit) {
    object Dashboard : Screen("dashboard", "主页",   { Icon(Icons.Default.Home,     "主页") })
    object Proxies   : Screen("proxies",   "节点",   { Icon(Icons.Default.List,     "节点") })
    object Logs      : Screen("logs",      "日志",   { Icon(Icons.Default.Article,  "日志") })
    object Settings  : Screen("settings",  "设置",   { Icon(Icons.Default.Settings, "设置") })
}

val bottomScreens = listOf(Screen.Dashboard, Screen.Proxies, Screen.Logs, Screen.Settings)

@Composable
fun ProxyMaxNavHost() {
    val navController = rememberNavController()
    val currentRoute  by navController.currentBackStackEntryAsState()

    Scaffold(
        bottomBar = {
            // 仅在主 Tab 页显示底部导航（子页面不显示）
            val showBar = currentRoute?.destination?.route in bottomScreens.map { it.route }
            if (showBar) {
                NavigationBar {
                    bottomScreens.forEach { screen ->
                        NavigationBarItem(
                            selected = currentRoute?.destination?.route == screen.route,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true; restoreState = true
                                }
                            },
                            icon  = { screen.icon() },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController    = navController,
            startDestination = Screen.Dashboard.route,
            modifier         = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreen() }
            composable(Screen.Proxies.route)   { ProxiesScreen() }
            composable(Screen.Logs.route)      { LogsScreen() }
            composable(Screen.Settings.route)  { SettingsScreen(onNavigatePerApp = {
                navController.navigate("per_app")
            }) }
            composable("per_app") { PerAppScreen() }
        }
    }
}
