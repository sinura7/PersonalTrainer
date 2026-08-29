package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import com.sinura.personaltrainer.domain.SessionOrderCopy
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.domain.WeekTwoCopy
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * P9.6 Home / FND-021 / FND-022: Start, Library, Goals, and last-session
 * tiles stay named at 360 dp through font 2.0. TalkBack reads the
 * merged tile, not a silent numeral.
 */
@RunWith(AndroidJUnit4::class)
class HomePassInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lastSessionTilesStayNamedAt360Font1() = assertHomeAboveFold(fontScale = 1f)

    @Test
    fun lastSessionTilesStayNamedAt360Font2() = assertHomeAboveFold(fontScale = 2f)

    @Test
    fun mastheadHasNoSettingsGear() {
        setConstrainedContent(fontScale = 1f) {
            HomeMasthead(epochDay = TODAY, headline = "Push day")
        }
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithText("Push day").assertIsDisplayed()
    }

    @Test
    fun startLibraryAndGoalsStayNamedAt360Font2() {
        setConstrainedContent(fontScale = 2f) {
            ThisWeekCard(
                day = TODAY_DAY,
                nextDay = null,
                loggedToday = false,
                lifts = listOf("Bench press", "Chest-supported row", "Cable fly"),
                reason = "Chest is due.",
                sessionLive = false,
                hasRoutines = true,
                onSuggestWeek = {},
                onReplayAnswers = {},
                onPrimary = {},
                onStartFree = {},
            )
            LinkRow(
                label = "Goals",
                onClick = {},
                modifier = Modifier.testTag(HomeTags.GOALS),
            )
            LinkRow(
                label = "Library",
                onClick = {},
                modifier = Modifier.testTag(HomeTags.LIBRARY),
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start today's planned session").assertIsDisplayed()
        compose.onNodeWithText("Start this session").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.FREE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start a free workout").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.GOALS).assertIsDisplayed()
        compose.onNodeWithContentDescription("Goals").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.LIBRARY).assertIsDisplayed()
        compose.onNodeWithContentDescription("Library").assertIsDisplayed()
    }

    @Test
    fun agendaStartAndFreeStayNamedAt360Font2() {
        setConstrainedContent(fontScale = 2f) {
            DailyAgendaCard(
                items = listOf(STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
            )
            LinkRow(
                label = "Goals",
                onClick = {},
                modifier = Modifier.testTag(HomeTags.GOALS),
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start today's planned session").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.FREE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start a free workout").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.GOALS).assertIsDisplayed()
        compose.onNodeWithText(SessionOrderCopy.AGENDA_SEPARATE).assertDoesNotExist()
    }

    @Test
    fun twoADayKeepsOneVoltAndShowsTheLiftOrder() {
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(CARDIO_ITEM, STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start today's planned session").assertIsDisplayed()
        compose.onNodeWithText("Start Push").assertIsDisplayed()
        compose.onNodeWithText("Start Cardio").assertDoesNotExist()
        compose.onNodeWithText(SessionOrderCopy.AGENDA_SEPARATE).assertIsDisplayed()
        compose.onNodeWithText("1 Squat · 2 Row").assertIsDisplayed()
    }

    @Test
    fun dayStackKeepsOneVoltOnTheFirstStrength() {
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(CARDIO_ITEM, STRENGTH_ITEM, EXTRA_ITEM),
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE, EXTRA_ROUTINE),
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithText("Start Push").assertIsDisplayed()
        compose.onNodeWithText("Start Cardio").assertDoesNotExist()
        compose.onNodeWithText("Start Monday extra").assertDoesNotExist()
        compose.onNodeWithText(SessionOrderCopy.AGENDA_SEPARATE).assertIsDisplayed()
        compose.onNodeWithText("1 Reverse hyper").assertIsDisplayed()
    }

    @Test
    fun emptyWeekReplayStaysNamedAt360Font2() {
        setConstrainedContent(fontScale = 2f) {
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
                onStartFree = {},
            )
        }
        compose.onNodeWithTag(HomeTags.REPLAY).assertIsDisplayed()
        compose.onNodeWithContentDescription(WeekTwoCopy.VOLT).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.FREE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start a free workout").assertIsDisplayed()
    }

    private fun assertHomeAboveFold(fontScale: Float) {
        setConstrainedContent(fontScale) {
            HomeStatRow(
                lastSession = LAST_SESSION,
                todayEpoch = TODAY,
                unit = WeightUnit.KG,
            )
        }
        compose.onNodeWithTag(HomeTags.LAST_SESSION).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.LAST_SESSION)
            .assertTextContains("8000", substring = true)
        compose.onNodeWithTag(HomeTags.LAST_SESSION)
            .assertContentDescriptionEquals("Last session, 8000 kg")
        compose.onNodeWithTag(HomeTags.DAYS_SINCE).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.DAYS_SINCE)
            .assertTextContains("2", substring = true)
        compose.onNodeWithTag(HomeTags.DAYS_SINCE)
            .assertContentDescriptionEquals("Days since, 2")
    }

    private fun setConstrainedContent(fontScale: Float, content: @Composable () -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density.density, fontScale = fontScale),
                    LocalWeightUnit provides WeightUnit.KG,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.size(360.dp, 800.dp)) {
                            Column { content() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        const val TODAY = 20_000L
        val LAST_SESSION = SessionSummary(
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
        val STRENGTH_ITEM = AgendaItem(
            occurrence = ScheduleOccurrence(
                id = "occ-pm",
                ruleId = "rule-lift",
                status = OccurrenceStatus.PLANNED,
                captured = CapturedCivilTime(1L, "UTC", 0, TODAY),
                hour = 18,
                minute = 0,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            rule = ScheduleRule(
                id = "rule-lift",
                weekday = Weekday.MONDAY,
                hour = 18,
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                focusKind = SessionFocusKind.PUSH,
                routineId = "r-push",
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        val CARDIO_ITEM = AgendaItem(
            occurrence = ScheduleOccurrence(
                id = "occ-am",
                ruleId = "rule-cardio",
                status = OccurrenceStatus.PLANNED,
                captured = CapturedCivilTime(1L, "UTC", 0, TODAY),
                hour = 7,
                minute = 0,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            rule = ScheduleRule(
                id = "rule-cardio",
                weekday = Weekday.MONDAY,
                hour = 7,
                minute = 0,
                modality = ScheduleModality.CARDIO,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
        )
        val EXTRA_ITEM = AgendaItem(
            occurrence = ScheduleOccurrence(
                id = "occ-extra",
                ruleId = "rule-extra",
                status = OccurrenceStatus.PLANNED,
                captured = CapturedCivilTime(1L, "UTC", 0, TODAY),
                hour = 20,
                minute = 0,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            rule = ScheduleRule(
                id = "rule-extra",
                weekday = Weekday.MONDAY,
                hour = 20,
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = "r-extra",
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            routineName = "Monday extra",
        )
        val PUSH_ROUTINE = Routine(
            id = "r-push",
            name = "Push",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf("Squat", "Row").mapIndexed { index, name ->
                RoutineExercise(
                    id = "item-$index",
                    routineId = "r-push",
                    exercise = Exercise(
                        id = "ex-$index",
                        name = name,
                        muscleGroup = "Quads",
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
        )
        val EXTRA_ROUTINE = Routine(
            id = "r-extra",
            name = "Monday extra",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf("Reverse hyper").mapIndexed { index, name ->
                RoutineExercise(
                    id = "extra-$index",
                    routineId = "r-extra",
                    exercise = Exercise(
                        id = "ex-extra-$index",
                        name = name,
                        muscleGroup = "Lower back",
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = index,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = null,
                    restSeconds = 90,
                )
            },
        )
        val TODAY_DAY = SuggestedTrainingDay(
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
}
