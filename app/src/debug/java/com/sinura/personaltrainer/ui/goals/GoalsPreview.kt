package com.sinura.personaltrainer.ui.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.GoalKind
import com.sinura.personaltrainer.domain.GoalPeriod
import com.sinura.personaltrainer.domain.GoalSnapshot
import com.sinura.personaltrainer.domain.MeasurableGoal
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.components.EmptyState
import com.sinura.personaltrainer.ui.components.GymErrorBanner
import com.sinura.personaltrainer.ui.components.ScreenLoading
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit

/**
 * Goals states for the MP-11 golden fan-out. Real header / empty / card pieces.
 */
@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun GoalsPopulatedPreview() {
    PersonalTrainerTheme {
        GoalsPreviewColumn {
            GoalsHeader(onBack = {}, onToggleAdd = {}, adding = false)
            GoalCard(
                snapshot = GoalsPreviewFixtures.sessions,
                unit = WeightUnit.KG,
                onPause = {},
                onDelete = {},
            )
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun GoalsEmptyPreview() {
    PersonalTrainerTheme {
        GoalsPreviewColumn {
            GoalsHeader(onBack = {}, onToggleAdd = {}, adding = false)
            EmptyState(
                title = "No goals yet",
                body = "Set a session count, lift target, or cardio mark. Nothing here is a streak.",
                actionLabel = "Add a goal",
                onAction = {},
            )
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun GoalsLoadingPreview() {
    PersonalTrainerTheme {
        GoalsPreviewColumn {
            GoalsHeader(onBack = {}, onToggleAdd = {}, adding = false)
            ScreenLoading()
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun GoalsErrorPreview() {
    PersonalTrainerTheme {
        GoalsPreviewColumn {
            GoalsHeader(onBack = {}, onToggleAdd = {}, adding = false)
            GymErrorBanner("Goals could not be read. Retry.")
            GoalCard(
                snapshot = GoalsPreviewFixtures.sessions,
                unit = WeightUnit.KG,
                onPause = {},
                onDelete = {},
            )
        }
    }
}

@Composable
private fun GoalsPreviewColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Pit)
            .padding(Metrics.gutter),
        verticalArrangement = Arrangement.spacedBy(Metrics.sectionGap),
    ) {
        content()
    }
}

internal object GoalsPreviewFixtures {
    val sessions = GoalSnapshot(
        goal = MeasurableGoal(
            id = "g-sessions",
            kind = GoalKind.SESSION_COUNT,
            targetValue = 4.0,
            period = GoalPeriod.WEEK,
            captured = CapturedCivilTime(
                instantMillis = 1L,
                zoneId = "UTC",
                offsetSeconds = 0,
                localEpochDay = 20_000L,
            ),
            createdAtMs = 1L,
            updatedAtMs = 1L,
        ),
        currentValue = 2.0,
        ratio = 0.5,
        met = false,
    )
}
