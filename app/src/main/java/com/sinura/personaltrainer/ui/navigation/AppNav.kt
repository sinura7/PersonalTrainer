package com.sinura.personaltrainer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sinura.personaltrainer.TrainerViewModel
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.profile.ProfileScreen
import com.sinura.personaltrainer.ui.progress.ProgressScreen
import com.sinura.personaltrainer.ui.session.SessionScreen
import com.sinura.personaltrainer.ui.workouts.WorkoutDetailScreen
import com.sinura.personaltrainer.ui.workouts.WorkoutsScreen

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Workouts : Route("workouts")
    data object Progress : Route("progress")
    data object Profile : Route("profile")
    data object WorkoutDetail : Route("workout/{workoutId}") {
        fun create(workoutId: String): String = "workout/$workoutId"
    }
    data object Session : Route("session/{workoutId}") {
        fun create(workoutId: String): String = "session/$workoutId"
    }
}

private data class Tab(
    val route: Route,
    val label: String,
    val icon: ImageVector,
)

@Composable
fun PersonalTrainerNav(
    viewModel: TrainerViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val tabs = listOf(
        Tab(Route.Home, "Home", Icons.Outlined.Home),
        Tab(Route.Workouts, "Workouts", Icons.Outlined.FitnessCenter),
        Tab(Route.Progress, "Progress", Icons.Outlined.Insights),
        Tab(Route.Profile, "Profile", Icons.Outlined.Person),
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
                    viewModel = viewModel,
                    onOpenWorkout = { navController.navigate(Route.WorkoutDetail.create(it)) },
                    onStartWorkout = { navController.navigate(Route.Session.create(it)) },
                )
            }
            composable(Route.Workouts.path) {
                WorkoutsScreen(
                    viewModel = viewModel,
                    onOpenWorkout = { navController.navigate(Route.WorkoutDetail.create(it)) },
                )
            }
            composable(Route.Progress.path) {
                ProgressScreen(viewModel = viewModel)
            }
            composable(Route.Profile.path) {
                ProfileScreen(viewModel = viewModel)
            }
            composable(
                route = Route.WorkoutDetail.path,
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
            ) { entry ->
                val workoutId = entry.arguments?.getString("workoutId").orEmpty()
                WorkoutDetailScreen(
                    workoutId = workoutId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onStart = { navController.navigate(Route.Session.create(workoutId)) },
                )
            }
            composable(
                route = Route.Session.path,
                arguments = listOf(navArgument("workoutId") { type = NavType.StringType }),
            ) { entry ->
                val workoutId = entry.arguments?.getString("workoutId").orEmpty()
                SessionScreen(
                    workoutId = workoutId,
                    viewModel = viewModel,
                    onFinished = {
                        navController.popBackStack(Route.Home.path, inclusive = false)
                    },
                    onExit = { navController.popBackStack() },
                )
            }
        }
    }
}
