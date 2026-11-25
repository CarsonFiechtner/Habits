package io.github.carsonfiechtner.keepup.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.carsonfiechtner.keepup.R
import io.github.carsonfiechtner.keepup.ui.archived.ArchivedScreen
import io.github.carsonfiechtner.keepup.ui.edit.EditHabitScreen
import io.github.carsonfiechtner.keepup.ui.history.HistoryScreen
import io.github.carsonfiechtner.keepup.ui.units.UnitsScreen
import io.github.carsonfiechtner.keepup.ui.settings.SettingsScreen
import io.github.carsonfiechtner.keepup.ui.today.TodayScreen
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable object TodayRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable object UnitsRoute
@Serializable object ArchivedRoute

/** Create (habitId = 0) or edit a habit. */
@Serializable data class EditHabitRoute(val habitId: Long = 0)

private enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    val icon: ImageVector,
    @StringRes val label: Int,
) {
    TODAY(TodayRoute, TodayRoute::class, Icons.Filled.CheckCircle, R.string.nav_today),
    HISTORY(HistoryRoute, HistoryRoute::class, Icons.Filled.DateRange, R.string.nav_history),
    SETTINGS(SettingsRoute, SettingsRoute::class, Icons.Filled.Settings, R.string.nav_settings),
}

@Composable
fun KeepUpApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = currentDestination == null ||
        TopLevelDestination.entries.any { top -> currentDestination.hierarchy.any { it.hasRoute(top.routeClass) } }

    Scaffold(
        // Each screen handles its own insets (top app bars, the bottom bar); avoid double padding.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.routeClass) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<TodayRoute> {
                TodayScreen(
                    onNewHabit = { navController.navigate(EditHabitRoute()) },
                    onEditHabit = { id -> navController.navigate(EditHabitRoute(id)) },
                )
            }
            composable<HistoryRoute> { HistoryScreen() }
            composable<SettingsRoute> {
                SettingsScreen(
                    onOpenUnits = { navController.navigate(UnitsRoute) },
                    onOpenArchived = { navController.navigate(ArchivedRoute) },
                )
            }
            composable<EditHabitRoute> { EditHabitScreen(onDone = { navController.popBackStack() }) }
            composable<UnitsRoute> { UnitsScreen(onBack = { navController.popBackStack() }) }
            composable<ArchivedRoute> { ArchivedScreen(onBack = { navController.popBackStack() }) }
        }
    }
}
