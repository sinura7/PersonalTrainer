package com.sinura.personaltrainer.ui.navigation

import android.app.Application
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
import com.sinura.personaltrainer.appContainer
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.CustomWeekLaunch
import com.sinura.personaltrainer.domain.DataHealthCopy
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.TemperIcons
import com.sinura.personaltrainer.ui.history.HistoryScreen
import com.sinura.personaltrainer.ui.exercise.ExerciseDetailScreen
import com.sinura.personaltrainer.ui.history.SessionDetailScreen
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.library.ExerciseLibraryScreen
import com.sinura.personaltrainer.ui.progress.ProgressScreen
import com.sinura.personaltrainer.ui.routines.CustomWeekScreen
import com.sinura.personaltrainer.ui.routines.RoutineEditorScreen
import com.sinura.personaltrainer.ui.onboarding.OnboardingGate
import com.sinura.personaltrainer.ui.onboarding.OnboardingGateViewModel
import com.sinura.personaltrainer.ui.onboarding.OnboardingScreen
import com.sinura.personaltrainer.ui.plan.PlanDayScreen
import com.sinura.personaltrainer.ui.plan.PlanScreen
import com.sinura.personaltrainer.ui.settings.SettingsScreen
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryScreen
import com.sinura.personaltrainer.ui.settings.SettingsViewModel
import com.sinura.personaltrainer.ui.theme.Haptics
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Motion
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.SurfacePressed
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.instrumentTween
import com.sinura.personaltrainer.ui.theme.Volt
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.units.LocalClockFormat
import com.sinura.personaltrainer.ui.activity.ActivityComposerScreen
import com.sinura.personaltrainer.ui.activity.ActivityDetailScreen
import com.sinura.personaltrainer.ui.activity.LiveCardioScreen
import com.sinura.personaltrainer.ui.workout.ActiveWorkoutScreen
import com.sinura.personaltrainer.ui.workout.RestTimerScreen

sealed class Route(val path: String) {
    data object Home : Route("home")
    data object Routines : Route("routines")
    data object History : Route("history")
    data object RoutineEditor : Route("routine/{routineId}") {
        fun create(routineId: String): String = "routine/$routineId"
    }
    data object ActiveWorkout : Route("session/{sessionId}") {
        fun create(sessionId: String): String = "session/$sessionId"
    }
    data object RestTimer : Route("session/{sessionId}/rest") {
        fun create(sessionId: String): String = "session/$sessionId/rest"
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
    data object ActivityComposer : Route("log/{mode}") {
        fun create(mode: String): String = "log/$mode"
    }
    data object LiveCardio : Route("cardio/{sessionId}") {
        fun create(sessionId: String): String = "cardio/$sessionId"
    }
    data object ActivityDetail : Route("activity/{activityId}") {
        fun create(activityId: String): String = "activity/$activityId"
    }
    data object ActivitySummary : Route("activity-summary/{activityId}") {
        fun create(activityId: String): String = "activity-summary/$activityId"
    }
    data object Settings : Route("settings")
    data object Onboarding : Route("onboarding")
    data object CustomWeek : Route("custom-week")
    data object PlanDay : Route("plan-day/{epochDay}?add={add}") {
        fun create(epochDay: Long, add: Boolean = false): String =
            "plan-day/$epochDay?add=$add"
    }
    data object Progress : Route("progress")
    data object Library : Route("library") {
        /**
         * The muscle filter crosses the nav boundary as an enum NAME, never as display text.
         *
         * It used to travel as `catalogLabel` — "Quads" — which meant the sending screen, the
         * URL and the receiving screen all had to agree on a human-readable string that exists
         * to be shown to humans. Renaming a label for the UI would silently break the filter,
         * and nothing would fail: the Library would simply open unfiltered. An enum name has no
         * reason to change and no other job.
         */
        fun create(muscle: CanonicalMuscle? = null): String =
            if (muscle == null) path else "$path?muscle=${Uri.encode(muscle.name)}"

        /** Reads the argument back. The rule lives in the domain so it can be tested. */
        fun parseMuscle(raw: String?): CanonicalMuscle? = MuscleNormalizer.fromRouteArgument(raw)
    }
}

internal data class Tab(
    val route: Route,
    val label: String,
    val icon: ImageVector,
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
 * Shipping tab order. Settings is a tab (ADR-014). Library stays pushed. Goals UI is gone (ADR-016).
 */
internal val shippingTabs = listOf(
    Tab(Route.Home, "Home", TemperIcons.Home),
    Tab(Route.Progress, "Body", TemperIcons.Body),
    Tab(Route.Routines, "Plan", TemperIcons.Plan),
    Tab(Route.History, "History", TemperIcons.History),
    Tab(Route.Settings, "Settings", TemperIcons.Settings),
)

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
    Route.RestTimer.path,
    Route.WorkoutSummary.path,
    Route.LiveCardio.path,
    Route.ActivitySummary.path,
    // The start interstitial that used to be listed here is gone (amends Phase 1a's settled
    // decision 2). Its replacement, StartOptionsSheet, is a MODAL surface the user deliberately
    // opened, not ambient chrome — so the bar staying visible behind it is still exactly one
    // live-session affordance, and the sheet itself shows "Go to session" rather than any start.
)

@Composable
fun PersonalTrainerNav(
    openSessionId: String? = null,
    onOpenSessionConsumed: () -> Unit = {},
    openOccurrenceId: String? = null,
    onOpenOccurrenceConsumed: () -> Unit = {},
    settingsViewModel: SettingsViewModel = viewModel(),
    gateViewModel: OnboardingGateViewModel = viewModel(),
) {
    val weightUnit by settingsViewModel.weightUnit.collectAsStateWithLifecycle()
    val clockFormat by settingsViewModel.clockFormat.collectAsStateWithLifecycle()
    val gate by gateViewModel.gate.collectAsStateWithLifecycle()
    val application = LocalContext.current.applicationContext as Application
    val container = remember(application) { application.appContainer() }

    when (gate) {
        // Nothing, deliberately. DataStore has not spoken; flashing Home or the
        // settings-failed empty state on that frame is worse than a blank one.
        OnboardingGate.UNKNOWN -> return
        OnboardingGate.UNAVAILABLE -> {
            EmptyState(
                title = DataHealthCopy.SETTINGS_TITLE,
                body = DataHealthCopy.SETTINGS_BODY,
                actionLabel = DataHealthCopy.RETRY,
                onAction = gateViewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Metrics.gutter),
            )
            return
        }
        OnboardingGate.APP -> Unit
    }

    val reduceMotion = LocalReducedMotion.current
    val screenEnter = if (reduceMotion) EnterTransition.None else ScreenEnter
    val screenExit = if (reduceMotion) ExitTransition.None else ScreenExit
    val barMs = if (reduceMotion) 0 else Motion.BASE
    val navController = rememberNavController()
    // Temper plates, not Material house/person/dumbbell/clock. The selected tab is volt
    // through tint; the drawings themselves stay monochrome so heat never sits on the chrome.
    // Library is not a tab. It is a catalog you visit to answer a question — "what could I
    // do for hamstrings" — and it was holding a fifth of the bottom bar for something
    // nobody navigates to as a destination. Every path that used to reach it as a tab now
    // pushes it with the filter already applied, which is how it was actually being used.
    // Settings *is* a tab (ADR-014): a dedicated space, not a gear on Home or Plan.
    val tabs = shippingTabs
    val liveBarViewModel: LiveSessionBarViewModel = viewModel()
    val liveSession by liveBarViewModel.uiState.collectAsStateWithLifecycle()
    val finishedNavigation by liveBarViewModel.finishedNavigation.collectAsStateWithLifecycle()
    val finishedActivityNavigation by liveBarViewModel.finishedActivityNavigation.collectAsStateWithLifecycle()
    val liveBarActionError by liveBarViewModel.actionError.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    // Hidden exactly where the session already owns the screen, or where starting one is the
    // whole point. Everywhere else — tabs and pushed routes alike — the bar is present, which
    // is the difference between "the workout is somewhere" and "the workout is right here".
    val showLiveBar = liveSession != null &&
        currentDestination?.route !in LIVE_BAR_HIDDEN_ROUTES
    val hidesLiveBar = currentDestination?.route in LIVE_BAR_HIDDEN_ROUTES
    LaunchedEffect(hidesLiveBar) {
        liveBarViewModel.setRouteHidesBar(hidesLiveBar)
    }
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

    LaunchedEffect(finishedActivityNavigation) {
        val activityId = finishedActivityNavigation ?: return@LaunchedEffect
        navController.navigate(Route.ActivitySummary.create(activityId)) {
            popUpTo(Route.Home.path) { inclusive = false }
            launchSingleTop = true
        }
        liveBarViewModel.onFinishNavigationHandled()
    }

    LaunchedEffect(openSessionId) {
        val sessionId = openSessionId ?: return@LaunchedEffect
        navController.navigate(Route.ActiveWorkout.create(sessionId)) {
            popUpTo(Route.ActiveWorkout.path) { inclusive = false }
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

    CompositionLocalProvider(
        LocalWeightUnit provides weightUnit,
        LocalClockFormat provides clockFormat,
    ) {
        Scaffold(
            bottomBar = {
              Column {
                AnimatedVisibility(
                    visible = showLiveBar,
                    enter = slideInVertically(
                        animationSpec = tween(barMs, easing = Motion.Standard),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(barMs, easing = Motion.Exit),
                    ) { it },
                ) {
                    liveSession?.let { live ->
                        LiveSessionBar(
                            state = live,
                            // When the tab bar is hidden the bar is the bottom-most thing on
                            // screen and must own the gesture inset itself.
                            applyNavInsets = !showBottomBar,
                            onResume = {
                                if (live.kind == LiveBarKind.ACTIVITY) {
                                    navController.navigate(Route.LiveCardio.create(live.sessionId)) {
                                        launchSingleTop = true
                                    }
                                } else {
                                    navController.navigate(Route.ActiveWorkout.create(live.sessionId)) {
                                        popUpTo(Route.ActiveWorkout.path) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            onFinish = liveBarViewModel::finishFromBar,
                            onDiscard = liveBarViewModel::discardFromBar,
                            actionError = liveBarActionError,
                            onActionErrorShown = liveBarViewModel::onActionErrorShown,
                        )
                    }
                }
                // Slides rather than disappears: entering a workout used to delete the bar in
                // one frame and let the content jolt down into the space it had been holding.
                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(
                        animationSpec = tween(barMs, easing = Motion.Standard),
                    ) { it },
                    exit = slideOutVertically(
                        animationSpec = tween(barMs, easing = Motion.Exit),
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
                enterTransition = { screenEnter },
                exitTransition = { screenExit },
                popEnterTransition = { screenEnter },
                popExitTransition = { screenExit },
            ) {
                composable(Route.Home.path) {
                    HomeScreen(
                        pendingOccurrenceStartId = openOccurrenceId,
                        onPendingOccurrenceConsumed = onOpenOccurrenceConsumed,
                        onResumeWorkout = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                        onLogActivity = { mode ->
                            navController.navigate(Route.ActivityComposer.create(mode))
                        },
                        onOpenLiveCardio = { sessionId ->
                            navController.navigate(Route.LiveCardio.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenPlan = { goToTab(Route.Routines.path) },
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onGenerateSchedule = { navController.navigate(Route.Onboarding.path) },
                        onBuildWeek = {
                            container.pendingCustomWeek.value = CustomWeekLaunch()
                            navController.navigate(Route.CustomWeek.path)
                        },
                    )
                }
                composable(Route.Progress.path) {
                    ProgressScreen(
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onOpenLibrary = { muscle -> navController.navigate(Route.Library.create(muscle)) },
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
                        initialMuscle = Route.Library.parseMuscle(entry.arguments?.getString("muscle")),
                    )
                }
                composable(Route.Settings.path) {
                    SettingsScreen(
                        onOpenGuidedSetup = { navController.navigate(Route.Onboarding.path) },
                        viewModel = settingsViewModel,
                    )
                }
                composable(Route.Onboarding.path) {
                    OnboardingScreen(
                        onFinished = {
                            navController.popBackStack(Route.Home.path, inclusive = false)
                        },
                        onBuildMyOwn = { answers, unit ->
                            container.pendingCustomWeek.value = CustomWeekLaunch(answers, unit)
                            navController.navigate(Route.CustomWeek.path)
                        },
                    )
                }
                composable(Route.CustomWeek.path) {
                    val launch by container.pendingCustomWeek.collectAsStateWithLifecycle()
                    CustomWeekScreen(
                        onFinished = {
                            navController.popBackStack(Route.Home.path, inclusive = false)
                        },
                        onBack = { navController.popBackStack() },
                        preferredDays = launch?.answers?.preferredDays.orEmpty(),
                        answers = launch?.answers,
                        pendingWeightUnit = launch?.unit,
                    )
                }
                composable(
                    route = Route.PlanDay.path,
                    arguments = listOf(
                        navArgument("epochDay") { type = NavType.LongType },
                        navArgument("add") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    PlanDayScreen(
                        epochDay = entry.arguments?.getLong("epochDay") ?: 0L,
                        startInAdd = entry.arguments?.getBoolean("add") == true,
                        onBack = { navController.popBackStack() },
                        onOpenRoutine = { navController.navigate(Route.RoutineEditor.create(it)) },
                    )
                }
                composable(Route.Routines.path) {
                    PlanScreen(
                        onCreateRoutine = { navController.navigate(Route.RoutineEditor.create("new")) },
                        onOpenRoutine = { navController.navigate(Route.RoutineEditor.create(it)) },
                        onOpenLibrary = { navController.navigate(Route.Library.create(null)) },
                        onOpenDay = { epochDay, add ->
                            navController.navigate(Route.PlanDay.create(epochDay, add))
                        },
                    )
                }
                composable(Route.History.path) {
                    HistoryScreen(
                        onOpenSession = { navController.navigate(Route.SessionDetail.create(it)) },
                        onOpenExercise = { navController.navigate(Route.ExerciseDetail.create(it)) },
                        onOpenActiveSession = { sessionId ->
                            navController.navigate(Route.ActiveWorkout.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenActivity = { navController.navigate(Route.ActivityDetail.create(it)) },
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
                        onOpenRest = { sessionId ->
                            navController.navigate(Route.RestTimer.create(sessionId)) {
                                launchSingleTop = true
                            }
                        },
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
                    route = Route.RestTimer.path,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    RestTimerScreen(
                        onClose = { navController.popBackStack() },
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
                    route = Route.ActivityComposer.path,
                    arguments = listOf(navArgument("mode") { type = NavType.StringType }),
                ) {
                    ActivityComposerScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { activityId ->
                            navController.navigate(Route.ActivitySummary.create(activityId)) {
                                popUpTo(Route.Home.path) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = Route.LiveCardio.path,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
                ) {
                    LiveCardioScreen(
                        onExit = { navController.popBackStack() },
                        onFinished = { activityId ->
                            navController.navigate(Route.ActivitySummary.create(activityId)) {
                                popUpTo(Route.Home.path) { inclusive = false }
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(
                    route = Route.ActivityDetail.path,
                    arguments = listOf(navArgument("activityId") { type = NavType.StringType }),
                ) {
                    ActivityDetailScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Route.ActivitySummary.path,
                    arguments = listOf(navArgument("activityId") { type = NavType.StringType }),
                ) {
                    ActivityDetailScreen(
                        celebration = true,
                        onBack = {
                            navController.popBackStack(Route.Home.path, inclusive = false)
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
 * icon, with the icon and its label in the accent and every other tab in [TextSecondary].
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
                .heightIn(min = NAV_BAR_HEIGHT),
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
        targetValue = if (selected) Volt else TextSecondary,
        animationSpec = instrumentTween(Motion.FAST),
        label = "nav-tab-content",
    )
    val tick by animateColorAsState(
        targetValue = if (selected) Volt else Color.Transparent,
        animationSpec = instrumentTween(Motion.FAST),
        label = "nav-tab-tick",
    )
    val background by animateColorAsState(
        targetValue = if (pressed) SurfacePressed else Pit,
        animationSpec = instrumentTween(Motion.TAP),
        label = "nav-tab-press",
    )

    Column(
        modifier = modifier
            .heightIn(min = NAV_BAR_HEIGHT)
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
            tab.icon,
            // The label below is the accessible name; describing the icon too would announce
            // every tab twice.
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(NAV_ICON_SIZE),
        )
        Kicker(
            tab.label,
            color = content,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Metrics.space1),
        )
    }
}

private const val SCREEN_ENTER_SCALE = 0.98f
private val NAV_BAR_HEIGHT = 64.dp
private val NAV_ICON_SIZE = 24.dp
private val NAV_TICK_WIDTH = 16.dp
private val NAV_TICK_HEIGHT = 3.dp
