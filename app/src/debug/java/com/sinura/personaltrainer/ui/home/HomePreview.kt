package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.preview.TemperAccessibilityPreviews
import com.sinura.personaltrainer.ui.preview.TemperReducedMotionPreview
import com.sinura.personaltrainer.ui.preview.TemperWidthPreviews
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * P9.6 Home states for FND-043. Real Home pieces, not a second layout.
 */
@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun HomePopulatedPreview() {
    PersonalTrainerTheme {
        HomePreviewColumn {
            HomeStatRow(
                lastSession = HomePreviewFixtures.lastSession,
                todayEpoch = HomePreviewFixtures.TODAY,
                unit = WeightUnit.KG,
            )
            ThisWeekCard(
                day = HomePreviewFixtures.todayDay,
                nextDay = null,
                loggedToday = false,
                lifts = listOf("Bench press", "Chest-supported row", "Cable fly"),
                reason = "Chest is due.",
                sessionLive = false,
                hasRoutines = true,
                onSuggestWeek = {},
                onReplayAnswers = {},
                onPrimary = {},
            )
            LinkRow(label = "Goals", onClick = {})
            LinkRow(label = "Library", onClick = {})
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun HomeEmptyPreview() {
    PersonalTrainerTheme {
        HomePreviewColumn {
            HomeStatRow(
                lastSession = null,
                todayEpoch = HomePreviewFixtures.TODAY,
                unit = WeightUnit.KG,
            )
            ThisWeekCard(
                day = null,
                nextDay = null,
                loggedToday = false,
                lifts = emptyList(),
                reason = null,
                sessionLive = false,
                hasRoutines = true,
                onSuggestWeek = {},
                onReplayAnswers = {},
                onPrimary = {},
            )
            LinkRow(label = "Goals", onClick = {})
            LinkRow(label = "Library", onClick = {})
        }
    }
}

@TemperReducedMotionPreview
@Composable
private fun HomeReducedMotionPreview() {
    PersonalTrainerTheme(reduceMotion = true) {
        HomePreviewColumn {
            HomeStatRow(
                lastSession = HomePreviewFixtures.lastSession,
                todayEpoch = HomePreviewFixtures.TODAY,
                unit = WeightUnit.KG,
            )
            ThisWeekCard(
                day = HomePreviewFixtures.todayDay,
                nextDay = null,
                loggedToday = false,
                lifts = listOf("Bench press"),
                reason = "Chest is due.",
                sessionLive = false,
                hasRoutines = true,
                onSuggestWeek = {},
                onReplayAnswers = {},
                onPrimary = {},
            )
        }
    }
}

@Composable
private fun HomePreviewColumn(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
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
}

internal object HomePreviewFixtures {
    const val TODAY = 20_000L

    val lastSession = SessionSummary(
        id = "s-last",
        routineId = "r-push",
        routineName = "Upper strength",
        date = 1_700_000_000_000L,
        finishedAt = 1_700_000_180_000L,
        durationMinutes = 48,
        workingSets = 16,
        volumeKg = 8_000.0,
        localEpochDay = TODAY - 2,
    )

    val todayDay = SuggestedTrainingDay(
        epochDay = TODAY,
        dayOfWeek = Weekday.MONDAY,
        isRest = false,
        focusKind = SessionFocusKind.PUSH,
        focusTitle = "Push",
        routineId = "r-push",
        routineName = "Upper strength",
        reason = "Chest is due.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )
}
