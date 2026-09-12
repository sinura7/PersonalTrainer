package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.sinura.personaltrainer.domain.AgendaItem
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.OccurrenceStatus
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.RoutineExercise
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.ScheduleModality
import com.sinura.personaltrainer.domain.ScheduleOccurrence
import com.sinura.personaltrainer.domain.ScheduleRule
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.plan.MissedWorkCard
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
            MissedWorkCard(
                overdueCount = 1,
                onMoveRemaining = {},
                onAdaptWeek = {},
                onKeepDates = {},
                onSkipMissed = {},
            )
            DailyAgendaCard(
                items = HomePreviewFixtures.agenda,
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = HomePreviewFixtures.routines,
            )
        }
    }
}

@TemperWidthPreviews
@TemperAccessibilityPreviews
@Composable
private fun HomeEmptyPreview() {
    PersonalTrainerTheme {
        HomePreviewColumn {
            ThisWeekCard(
                day = null,
                nextDay = null,
                loggedToday = false,
                lifts = emptyList(),
                reason = null,
                sessionLive = false,
                hasRoutines = true,
                onPrimary = {},
                onStartFree = {},
            )
        }
    }
}

@TemperReducedMotionPreview
@Composable
private fun HomeReducedMotionPreview() {
    PersonalTrainerTheme(reduceMotion = true) {
        HomePreviewColumn {
            DailyAgendaCard(
                items = HomePreviewFixtures.agenda,
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = HomePreviewFixtures.routines,
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

    val agenda: List<AgendaItem> = listOf(
        AgendaItem(
            occurrence = occurrence("occ-am", "rule-cardio", hour = 7),
            rule = rule("rule-cardio", ScheduleModality.CARDIO),
        ),
        AgendaItem(
            occurrence = occurrence("occ-pm", "rule-lift", hour = 18),
            rule = rule(
                "rule-lift",
                ScheduleModality.STRENGTH,
                SessionFocusKind.PUSH,
                routineId = "r-push",
            ),
            routineName = "Upper strength",
        ),
    )

    val routines: List<Routine> = listOf(
        Routine(
            id = "r-push",
            name = "Upper strength",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf("Squat", "Row", "Bench").mapIndexed { index, name ->
                RoutineExercise(
                    id = "item-$index",
                    routineId = "r-push",
                    exercise = Exercise(
                        id = "ex-$index",
                        name = name,
                        muscleGroup = "Chest",
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = index,
                    targetSets = 3,
                    targetReps = 5,
                    targetWeightKg = null,
                    restSeconds = 90,
                )
            },
        ),
    )

    private fun occurrence(id: String, ruleId: String, hour: Int) = ScheduleOccurrence(
        id = id,
        ruleId = ruleId,
        status = OccurrenceStatus.PLANNED,
        captured = CapturedCivilTime(
            instantMillis = 1L,
            zoneId = "UTC",
            offsetSeconds = 0,
            localEpochDay = TODAY,
        ),
        hour = hour,
        minute = 0,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )

    private fun rule(
        id: String,
        modality: ScheduleModality,
        focusKind: SessionFocusKind? = null,
        routineId: String? = null,
    ) = ScheduleRule(
        id = id,
        weekday = Weekday.MONDAY,
        hour = 7,
        minute = 0,
        modality = modality,
        focusKind = focusKind,
        routineId = routineId,
        createdAtMs = 1L,
        updatedAtMs = 1L,
    )
}
