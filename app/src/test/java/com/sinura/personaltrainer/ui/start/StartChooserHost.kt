package com.sinura.personaltrainer.ui.start

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.ui.home.HomeViewModel

/** Builds the real shared gate the app uses, over [deps]. */
fun startGateFor(deps: AppDependencies): HomeViewModel =
    HomeViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

/**
 * Hosts the shared StartChooser off Home, the way AppNav does from Plan, History or Body:
 * gate navigation is consumed here and handed to [onWorkoutStarted].
 */
@Composable
fun StartChooserHost(
    gate: HomeViewModel,
    deps: AppDependencies,
    onDismiss: () -> Unit,
    onWorkoutStarted: (String) -> Unit,
    onLogMixed: () -> Unit = {},
    onLogPast: () -> Unit = {},
    onLogCardio: () -> Unit = {},
    options: StartOptionsViewModel? = null,
) {
    val model = options ?: remember {
        StartOptionsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
    }
    val session by gate.navigateToSession.collectAsStateWithLifecycle()
    LaunchedEffect(session) {
        val id = session ?: return@LaunchedEffect
        gate.onSessionNavigationHandled()
        onWorkoutStarted(id)
    }
    StartChooser(
        onDismiss = onDismiss,
        onWorkoutStarted = onWorkoutStarted,
        gate = gate,
        onLogMixed = onLogMixed,
        onLogPast = onLogPast,
        onLogCardio = onLogCardio,
        viewModel = model,
    )
}
