package com.sinura.personaltrainer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.routines.RoutineEditorScreen
import com.sinura.personaltrainer.ui.routines.RoutinesScreen
import com.sinura.personaltrainer.ui.workout.ActiveWorkoutScreen
import com.sinura.personaltrainer.ui.workout.StartWorkoutScreen

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Routines : Route("routines")
    data object History : Route("history")
    data object StartWorkout : Route("startWorkout")
    data object RoutineEditor : Route("routine/{routineId}") {
        fun create(routineId: String): String = "routine/$routineId"
    }
    data object ActiveWorkout : Route("session/{sessionId}") {
        fun create(sessionId: String): String = "session/$sessionId"
    }
    data object SessionDetail : Route("history/{sessionId}") {
        fun create(sessionId: String): String = "history/$sessionId"
    }
}

private data class Tab(
    val route: Route,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun PersonalTrainerNav() {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Route.Home, "Home", Icons.Outlined.Home),
        Tab(Route.Routines, "Routines", Icons.Outlined.FitnessCenter),
        Tab(Route.History, "History", Icons.Outlined.History),
    )
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route.path } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = currentDestination?.hierarchy?.any { it.route == tab.route.path } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route.path) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Route.Home.path,
            modifier = Modifier.padding(padding),
        ) {
            composable(Route.Home.path) {
                HomeScreen(
                    onStartWorkout = { navController.navigate(Route.StartWorkout.path) },
                    onResumeWorkout = { navController.navigate(Route.ActiveWorkout.create(it)) },
                    onOpenRoutines = { navController.navigate(Route.Routines.path) },
                    onOpenHistory = { navController.navigate(Route.History.path) },
                    onOpenSession = { navController.navigate(Route.SessionDetail.create(it)) },
                )
            }
            composable(Route.Routines.path) {
                RoutinesScreen(
                    onCreateRoutine = { navController.navigate(Route.RoutineEditor.create("new")) },
                    onOpenRoutine = { navController.navigate(Route.RoutineEditor.create(it)) },
                )
            }
            composable(Route.History.path) {
                HistoryScreen(
                    onOpenSession = { navController.navigate(Route.SessionDetail.create(it)) },
                )
            }
            composable(Route.StartWorkout.path) {
                StartWorkoutScreen(
                    onBack = { navController.popBackStack() },
                    onWorkoutStarted = { sessionId ->
                        navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                            popUpTo(Route.StartWorkout.path) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Route.RoutineEditor.path,
                arguments = listOf(navArgument("routineId") { type = NavType.StringType }),
            ) {
                RoutineEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Route.ActiveWorkout.path,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                ActiveWorkoutScreen(
                    onExit = { navController.popBackStack() },
                    onFinished = {
                        navController.popBackStack(Route.Home.path, inclusive = false)
                    },
                )
            }
            composable(
                route = Route.SessionDetail.path,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) {
                SessionDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
