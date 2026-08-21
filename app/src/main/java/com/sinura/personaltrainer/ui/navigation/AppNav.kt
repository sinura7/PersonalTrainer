package com.sinura.personaltrainer.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.exercise.ExerciseDetailScreen
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.library.ExerciseLibraryScreen
import com.sinura.personaltrainer.ui.progress.ProgressScreen
import com.sinura.personaltrainer.ui.routines.RoutineEditorScreen
import com.sinura.personaltrainer.ui.plan.PlanScreen
import com.sinura.personaltrainer.ui.settings.SettingsScreen
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryScreen
import com.sinura.personaltrainer.ui.settings.SettingsViewModel
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextTertiary
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
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
    data object WorkoutSummary : Route("summary/{sessionId}") {
        fun create(sessionId: String) = "summary/$sessionId"
    }
    data object ExerciseDetail : Route("exercise/{exerciseId}") {
        fun create(exerciseId: String) = "exercise/$exerciseId"
    }
    data object SessionDetail : Route("history/{sessionId}") {
        fun create(sessionId: String): String = "history/$sessionId"
    }
    data object Settings : Route("settings")
    data object Progress : Route("progress")
    data object Library : Route("library") {
        fun create(muscle: String? = null): String =
            if (muscle.isNullOrBlank()) path
            else "$path?muscle=${Uri.encode(muscle)}"
    }
}

private data class Tab(
    val route: Route,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    /**
     * What a destination's registered route must equal for this tab to be selected.
     *
     * Plain equality, now that no tab route carries a query parameter. Library used to, which
     * forced a fuzzy prefix match everywhere a tab was identified — and that match is exactly
     * the kind of thing that keeps working while quietly selecting the wrong tab once a second
     * route shares a prefix.
     */
    val matchPattern: String get() = route.path
}

/**
 * One fade-through for every destination change.
 *
 * The host declared no transitions at all, so a lateral tab switch and a hierarchical
 * drill-down were drawn identically — the default cross-fade in both cases — and motion
 * carried no information about where you had just gone. The outgoing screen leaves quickly
 * and the incoming one arrives after it has cleared, rising the last 2% of its scale, so the
 * two never dissolve through each other into a grey frame.
 */
private val ScreenEnter: EnterTransition =
    fadeIn(tween(Motion.BASE, delayMillis = Motion.TAP, easing = Motion.Standard)) +
        scaleIn(
            animationSpec = tween(Motion.BASE, delayMillis = Motion.TAP, easing = Motion.Standard),
            initialScale = SCREEN_ENTER_SCALE,
        )

private val ScreenExit: ExitTransition = fadeOut(tween(Motion.TAP, easing = Motion.Exit))

/**
 * Routes that already own the live session, or exist to start one. The bar is visible
 * everywhere else — including pushed routes, where the tab bar is not.
 */
private val LIVE_BAR_HIDDEN_ROUTES = setOf(
    Route.ActiveWorkout.path,
    Route.WorkoutSummary.path,
    Route.StartWorkout.path,
)

@Composable
fun PersonalTrainerNav(
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel(),
) {
    val weightUnit by settingsViewModel.weightUnit.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    // Icon and destination have to agree: a flame reads as a streak or a calorie burn to
    // every fitness user alive, and it was labelling a muscle heat map; a book reads as
    // reading, and it was labelling a grid of exercises.
    val tabs = listOf(
        Tab(Route.Home, "Home", Icons.Outlined.Home, Icons.Filled.Home),
        Tab(Route.Progress, "Body", Icons.Outlined.AccessibilityNew, Icons.Filled.AccessibilityNew),
        Tab(Route.Routines, "Plan", Icons.Outlined.FitnessCenter, Icons.Filled.FitnessCenter),
        // Library is not a tab. It is a catalog you visit to answer a question — "what could I
        // do for hamstrings" — and it was holding a fifth of the bottom bar for something
        // nobody navigates to as a destination. Every path that used to reach it as a tab now
        // pushes it with the filter already applied, which is how it was actually being used.
        Tab(Route.History, "History", Icons.Outlined.History, Icons.Filled.History),
    )
    val liveBarViewModel: LiveSessionBarViewModel = viewModel()
    val liveSession by liveBarViewModel.uiState.collectAsStateWithLifecycle()
    val finishedNavigation by liveBarViewModel.finishedNavigation.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Hidden exactly where the session already owns the screen, or where starting one is the
    // whole point. Everywhere else — tabs and pushed routes alike — the bar is present, which
    // is the difference between "the workout is somewhere" and "the workout is right here".
    val showLiveBar = liveSession != null &&
        currentDestination?.route !in LIVE_BAR_HIDDEN_ROUTES
    // Before the back-stack flow emits, currentDestination is null. The start destination
    // is a tab, so treat that first frame as one — otherwise the bar slides up from nothing
    // on every cold start.
    val showBottomBar = navBackStackEntry == null || tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.matchPattern } == true
    }

    LaunchedEffect(finishedNavigation) {
        val sessionId = finishedNavigation ?: return@LaunchedEffect
        navController.navigate(Route.WorkoutSummary.create(sessionId)) {
            popUpTo(Route.Home.path) { inclusive = false }
            launchSingleTop = true
        }
        liveBarViewModel.onFinishNavigationHandled()
    }

    LaunchedEffect(openSessionId) {
        val sessionId = openSessionId ?: return@LaunchedEffect
        navController.navigate(Route.ActiveWorkout.create(sessionId)) {
            launchSingleTop = true
        }
        onOpenSessionConsumed()
    }

    fun goToTab(path: String) {
        navController.navigate(path) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalWeightUnit provides weightUnit) {
        Scaffold(
            bottomBar = {
              Column {
                AnimatedVisibility(
                    visible = showLiveBar,
                    enter = slideInVertically(
                        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(Motion.BASE, easing = Motion.Exit),
                    ) { it },
                ) {
                    liveSession?.let { live ->
                        LiveSessionBar(
                            state = live,
                            // When the tab bar is hidden the bar is the bottom-most thing on
                            // screen and must own the gesture inset itself.
                            applyNavInsets = !showBottomBar,
                            onResume = {
                                navController.navigate(Route.ActiveWorkout.create(live.sessionId)) {
                                    launchSingleTop = true
                                }
                            },
                            onFinish = liveBarViewModel::finishFromBar,
                            onDiscard = liveBarViewModel::discardFromBar,
                        )
                    }
                }
                // Slides rather than disappears: entering a workout used to delete the bar in
                // one frame and let the content jolt down into the space it had been holding.
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(
                        animationSpec = tween(Motion.BASE, easing = Motion.Standard),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(Motion.BASE, easing = Motion.Exit),
                    ) { it },
                ) {
                    InstrumentNavBar(
                        tabs = tabs,
                        isSelected = { tab ->
                            currentDestination?.hierarchy?.any { it.route == tab.matchPattern } == true
                        },
                        onSelect = { tab -> goToTab(tab.route.path) },
                    )
                }
              }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Route.Home.path,
                // consumeWindowInsets is what stops the double inset: this Scaffold has no top
                // bar, so its padding already contains the status-bar height, and without
                // consuming it every screen that mounts its own Scaffold applied that height a
                // second time — a dead strip above the title on every screen but Home.
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                enterTransition = { ScreenEnter },
                exitTransition = { ScreenExit },
                popEnterTransition = { ScreenEnter },
                popExitTransition = { ScreenExit },
            ) {
                composable(Route.Home.path) {
                    HomeScreen(
                        onStartWorkout = { navController.navigate(Route.StartWorkout.path) },
                        onResumeWorkout = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenRoutines = { goToTab(Route.Routines.path) },
                        onOpenHistory = { goToTab(Route.History.path) },
                        onOpenProgress = { goToTab(Route.Progress.path) },
                        onOpenPlan = { goToTab(Route.Routines.path) },
                        // A plain push now that Library is not a tab. The old version was a tab
                        // navigation with restoreState turned off — a hack that existed only to
                        // stop a filtered jump landing on the previous, unfiltered scroll state.
                        onOpenLibraryMuscle = { muscle ->
                            navController.navigate(Route.Library.create(muscle))
                        },
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onOpenSession = { navController.navigate(Route.SessionDetail.create(it)) },
                        onOpenSettings = { navController.navigate(Route.Settings.path) },
                    )
                }
                composable(Route.Progress.path) {
                    ProgressScreen(
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onOpenLibrary = { muscle -> navController.navigate(Route.Library.create(muscle)) },
                        onStartWorkout = { navController.navigate(Route.StartWorkout.path) },
                        onOpenRoutines = { goToTab(Route.Routines.path) },
                    )
                }
                composable(
                    route = "library?muscle={muscle}",
                    arguments = listOf(
                        navArgument("muscle") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    ExerciseLibraryScreen(
                        onBack = { navController.popBackStack() },
                        onCreateRoutine = { navController.navigate(Route.RoutineEditor.create("new")) },
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        initialMuscle = entry.arguments?.getString("muscle"),
                    )
                }
                composable(Route.Settings.path) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        viewModel = settingsViewModel,
                    )
                }
                composable(Route.Routines.path) {
                    PlanScreen(
                        onCreateRoutine = { navController.navigate(Route.RoutineEditor.create("new")) },
                        onOpenRoutine = { navController.navigate(Route.RoutineEditor.create(it)) },
                        // No popUpTo: the Plan tab stays underneath the workout, matching how
                        // Home starts one.
                        onWorkoutStarted = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenLibrary = { navController.navigate(Route.Library.create(null)) },
                        onOpenSettings = { navController.navigate(Route.Settings.path) },
                    )
                }
                composable(Route.History.path) {
                    HistoryScreen(
                        onOpenSession = { navController.navigate(Route.SessionDetail.create(it)) },
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onStartWorkout = { navController.navigate(Route.StartWorkout.path) },
                        onOpenActiveSession = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Route.StartWorkout.path) {
                    StartWorkoutScreen(
                        onBack = { navController.popBackStack() },
                        onWorkoutStarted = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
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
                        onFinished = { sessionId ->
                            // The finished workout leaves the stack: back from the summary goes
                            // Home, never into a session that no longer accepts sets.
                            navController.navigate(Route.WorkoutSummary.create(sessionId)) {
                                popUpTo(Route.Home.path) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = Route.SessionDetail.path,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    SessionDetailScreen(
                        onBack = { navController.popBackStack() },
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onOpenActiveSession = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = Route.WorkoutSummary.path,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    WorkoutSummaryScreen(
                        onDone = {
                            navController.popBackStack(Route.Home.path, inclusive = false)
                        },
                        onOpenSession = { sessionId ->
                            navController.navigate(Route.SessionDetail.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = Route.ExerciseDetail.path,
                    arguments = listOf(navArgument("exerciseId") { type = NavType.StringType }),
                ) {
                    ExerciseDetailScreen(
                        onBack = { navController.popBackStack() },
                        // launchSingleTop so bouncing between a session and one of its lifts does
                        // not stack a new copy of the same screen on every hop.
                        onOpenSession = { sessionId ->
                            navController.navigate(Route.SessionDetail.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * The bottom bar, hand-rolled.
 *
 * Material's `NavigationBar` was the loudest stock-template signal left in the product: a
 * lavender pill sliding under the active icon, on a container a step lighter than the window
 * it sits on. Here the bar *is* the window colour, separated by one hairline, and the active
 * destination is marked the way an instrument marks a live channel — a volt tick over the
 * icon, with the icon and its label in the accent and every other tab in [TextTertiary].
 *
 * No ripple: a bloom spreading out of a 24dp icon is Material's own signature, and on a
 * near-black field a pressed fill says the same thing without the animation.
 */
@Composable
private fun InstrumentNavBar(
    tabs: List<Tab>,
    isSelected: (Tab) -> Boolean,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Pit),
    ) {
        HairlineDivider(startIndent = 0.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Material's NavigationBar applied this; without it the tabs are five
                // unrelated controls to a screen reader rather than "tab 2 of 5".
                .selectableGroup()
                // The inset sits below the row rather than inside it, so the 64dp of touch
                // target survives on a phone with gesture navigation.
                .navigationBarsPadding()
                .height(NAV_BAR_HEIGHT),
        ) {
            tabs.forEach { tab ->
                NavTab(
                    tab = tab,
                    selected = isSelected(tab),
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavTab(
    tab: Tab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val content by animateColorAsState(
        targetValue = if (selected) Volt else TextTertiary,
        animationSpec = tween(Motion.FAST),
        label = "nav-tab-content",
    )
    val tick by animateColorAsState(
        targetValue = if (selected) Volt else Color.Transparent,
        animationSpec = tween(Motion.FAST),
        label = "nav-tab-tick",
    )
    val background by animateColorAsState(
        targetValue = if (pressed) SurfacePressed else Pit,
        animationSpec = tween(Motion.TAP),
        label = "nav-tab-press",
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(background)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = {
                    Haptics.tick(view)
                    onClick()
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Metrics.space1, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier
                .size(width = NAV_TICK_WIDTH, height = NAV_TICK_HEIGHT)
                .background(tick, CircleShape),
        )
        Icon(
            if (selected) tab.selectedIcon else tab.icon,
            // The label below is the accessible name; describing the icon too would announce
            // every tab twice.
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(NAV_ICON_SIZE),
        )
        Kicker(tab.label, color = content)
    }
}

private const val SCREEN_ENTER_SCALE = 0.98f
private val NAV_BAR_HEIGHT = 64.dp
private val NAV_ICON_SIZE = 24.dp
private val NAV_TICK_WIDTH = 16.dp
private val NAV_TICK_HEIGHT = 3.dp
