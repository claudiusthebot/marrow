package rocks.talon.marrow.phone.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import rocks.talon.marrow.phone.MarrowViewModel
import rocks.talon.marrow.phone.ui.about.AboutTab
import rocks.talon.marrow.phone.ui.detail.SectionDetail
import rocks.talon.marrow.phone.ui.device.DeviceTab
import rocks.talon.marrow.phone.ui.icons.MarrowIcons
import rocks.talon.marrow.phone.ui.watch.WatchTab

object Routes {
    const val DEVICE = "device"
    const val WATCH = "watch"
    const val ABOUT = "about"
    const val DETAIL = "detail/{sectionId}"
    const val SETTINGS = "settings"
    fun detail(sectionId: String) = "detail/$sectionId"
}

@Composable
fun MarrowApp(vm: MarrowViewModel) {
    val settings by vm.settings.collectAsState()
    MarrowTheme(themeMode = settings.themeMode) {
        val nav = rememberNavController()
        val backStack by nav.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val showBottomBar = currentRoute in setOf(Routes.DEVICE, Routes.WATCH, Routes.ABOUT)

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                if (showBottomBar) {
                    MarrowBottomBar(
                        active = currentRoute ?: Routes.DEVICE,
                        onTab = { route ->
                            nav.navigate(route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0),
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (showBottomBar) padding.calculateBottomPadding() else 0.dp),
            ) {
                NavHost(
                    navController = nav,
                    startDestination = Routes.DEVICE,
                    enterTransition = { fadeIn(tween(220)) },
                    exitTransition = { fadeOut(tween(160)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = { fadeOut(tween(160)) },
                ) {
                    composable(Routes.DEVICE) {
                        DeviceTab(vm = vm, onSection = { id -> nav.navigate(Routes.detail(id)) })
                    }
                    composable(Routes.WATCH) {
                        WatchTab(vm = vm, onSection = { id -> nav.navigate(Routes.detail("watch:$id")) })
                    }
                    composable(Routes.ABOUT) {
                        AboutTab(vm = vm, onOpenSettings = { nav.navigate(Routes.SETTINGS) })
                    }
                    composable(Routes.DETAIL) { entry: NavBackStackEntry ->
                        val raw = entry.arguments?.getString("sectionId") ?: return@composable
                        val (source, id) = if (raw.startsWith("watch:")) "watch" to raw.removePrefix("watch:") else "phone" to raw
                        SectionDetail(
                            vm = vm,
                            sectionId = id,
                            source = source,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}

@Composable
private fun MarrowBottomBar(active: String, onTab: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        NavBarTab(active, Routes.DEVICE, "Device", MarrowIcons.Device, onTab)
        NavBarTab(active, Routes.WATCH, "Watch", MarrowIcons.Watch, onTab)
        NavBarTab(active, Routes.ABOUT, "About", MarrowIcons.Wordmark, onTab)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavBarTab(
    active: String,
    route: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onTab: (String) -> Unit,
) {
    NavigationBarItem(
        selected = active == route,
        onClick = { onTab(route) },
        icon = {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            }
        },
        label = { Text(label) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTextColor = MaterialTheme.colorScheme.onSurface,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}
