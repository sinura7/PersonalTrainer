package com.sinura.personaltrainer.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.LiveSessionRules
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.Kicker
import com.sinura.personaltrainer.ui.components.PrimaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.TextPrimary
import com.sinura.personaltrainer.ui.theme.TextSecondary

@Composable
fun LiveCardioScreen(
    onExit: () -> Unit,
    onFinished: (String) -> Unit,
    viewModel: LiveCardioViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finishedId by viewModel.finishedId.collectAsStateWithLifecycle()
    LaunchedEffect(finishedId) {
        val id = finishedId ?: return@LaunchedEffect
        viewModel.onFinishedHandled()
        onFinished(id)
    }

    Scaffold { padding ->
        if (state.missing) {
            EmptyState(
                title = "No live cardio",
                body = "That session is gone. Start a new one from Home.",
                actionLabel = "Back",
                onAction = onExit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(Metrics.gutter),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Metrics.gutter),
            verticalArrangement = Arrangement.spacedBy(Metrics.space4),
        ) {
            Text(state.session?.title ?: "Cardio", style = InstrumentType.title, color = TextPrimary)
            Text(
                LiveSessionRules.formatElapsed(state.elapsedSeconds),
                style = InstrumentType.display,
                color = TextPrimary,
            )
            Text("Process death keeps this clock. Reboot keeps the last honest elapsed.", style = InstrumentType.caption, color = TextSecondary)
            state.error?.let { GymErrorBanner(it) }
            Kicker("Type")
            CardioType.entries.forEach { option ->
                TextButton(onClick = { viewModel.setType(option) }) {
                    Text(if (option == state.type) "• ${option.name}" else option.name)
                }
            }
            TextButton(onClick = { viewModel.setIndoor(!state.indoor) }) {
                Text(if (state.indoor) "Indoor" else "Outdoor")
            }
            OutlinedTextField(
                value = state.distanceKm,
                onValueChange = viewModel::setDistanceKm,
                label = { Text("Distance km (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryGymButton(
                text = if (state.finishing) "Finishing…" else "Finish",
                onClick = viewModel::finish,
                enabled = !state.finishing,
            )
            TextButton(onClick = viewModel::discard) { Text("Discard") }
            TextButton(onClick = onExit) { Text("Leave running") }
        }
    }
}
