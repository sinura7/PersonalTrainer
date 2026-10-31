package com.sinura.personaltrainer.ui.start

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.ui.home.HomeScreen
import com.sinura.personaltrainer.ui.home.HomeViewModel

/**
 * Test host that wires Home to the shared StartChooser the way AppNav does: Home's Start
 * opens the chooser and every chooser start goes through the same [HomeViewModel] gate.
 */
@Composable
fun HomeWithStartChooser(
    vm: HomeViewModel,
    deps: AppDependencies,
    onResumeWorkout: (String) -> Unit,
    onOpenPlan: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val options = remember {
        StartOptionsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
    }
    HomeScreen(
        onResumeWorkout = onResumeWorkout,
        onOpenPlan = onOpenPlan,
        onOpenStartSheet = { open = true },
        viewModel = vm,
    )
    if (open) {
        StartChooser(
            onDismiss = { open = false },
            onWorkoutStarted = { open = false; onResumeWorkout(it) },
            gate = vm,
            onOpenPlan = onOpenPlan,
            suggestedKit = vm.uiState.value.suggestedExtraEquipment,
            viewModel = options,
        )
    }
}
