package com.sinura.personaltrainer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.        assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import com.sinura.personaltrainer.domain.ScheduleKind
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
 * P9.6 Home / FND-021 / FND-022: Start and last-session tiles stay named
 * at 360 dp through font 2.0. TalkBack reads the merged tile, not a
 * silent numeral. Home does not repeat tab destinations.
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
    fun startAndFreeStayNamedAt360Font2() {
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
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start today's planned session").assertIsDisplayed()
        compose.onNodeWithText("Start this session").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.FREE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start a free workout").assertIsDisplayed()
    }

    @Test
    fun fallbackVoltOpensStartConfirmAndDoesNotStartUntilConfirm() {
        var started = false
        setConstrainedContent(fontScale = 1f) {
            ThisWeekCard(
                day = TODAY_DAY,
                nextDay = null,
                loggedToday = false,
                lifts = listOf("Squat", "Row"),
                reason = null,
                sessionLive = false,
                hasRoutines = true,
                routines = listOf(PUSH_ROUTINE),
                onSuggestWeek = {},
                onReplayAnswers = {},
                onPrimary = { started = true },
                onStartFree = {},
            )
        }
        compose.onNodeWithText("Start Upper strength?").assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithText("Start Upper strength?").assertIsDisplayed()
        compose.onNodeWithText("1 Squat", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 Row", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 lifts · about 13 min", substring = true).assertIsDisplayed()
        org.junit.Assert.assertFalse(started)
        compose.onNodeWithText("Start").performClick()
        org.junit.Assert.assertTrue(started)
    }

    @Test
    fun fallbackStartConfirmCancelDoesNotStart() {
        var started = false
        setConstrainedContent(fontScale = 1f) {
            ThisWeekCard(
                day = TODAY_DAY,
                nextDay = null,
                loggedToday = false,
                lifts = listOf("Squat", "Row"),
                reason = null,
                sessionLive = false,
                hasRoutines = true,
                routines = listOf(PUSH_ROUTINE),
                onSuggestWeek = {},
                onReplayAnswers = {},
                onPrimary = { started = true },
                onStartFree = {},
            )
        }
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithText("Start Upper strength?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Start Upper strength?").assertDoesNotExist()
        org.junit.Assert.assertFalse(started)
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
                today = TODAY,
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start today's planned session").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.FREE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start a free workout").assertIsDisplayed()
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
                today = TODAY,
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
                today = TODAY,
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
    fun emptyDayBoardKeepsFreeAndHidesStart() {
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = emptyList(),
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                today = TODAY,
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.FREE).assertIsDisplayed()
        compose.onNodeWithContentDescription("Start a free workout").assertIsDisplayed()
    }

    @Test
    fun weekStripCaptionsSaturdayRestWhenFridayHoldsTheWorkout() {
        val friday = 20_000L
        val saturday = friday + 1
        val weekStart = friday - 4
        val rule = ScheduleRule(
            id = "rule-fri",
            weekday = Weekday.FRIDAY,
            hour = 18,
            minute = 0,
            modality = ScheduleModality.STRENGTH,
            routineId = "r-friday",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val occ = ScheduleOccurrence(
            id = "occ-fri",
            ruleId = rule.id,
            status = OccurrenceStatus.PLANNED,
            captured = CapturedCivilTime(1L, "UTC", 0, friday),
            hour = 18,
            minute = 0,
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )
        val cells = com.sinura.personaltrainer.domain.WeekBoard.forWeek(
            weekStart,
            listOf(occ),
            listOf(rule),
            mapOf("r-friday" to "Friday"),
        )
        setConstrainedContent(fontScale = 1f) {
            com.sinura.personaltrainer.ui.components.WeekStrip(
                cells = cells,
                today = saturday,
                selected = saturday,
                onSelectDay = {},
            )
        }
        compose.onNodeWithTag(
            com.sinura.personaltrainer.ui.components.WeekStripTags.cell(saturday),
        ).assertIsDisplayed()
        compose.onNodeWithTag(
            com.sinura.personaltrainer.ui.components.WeekStripTags.cell(saturday),
        ).assertTextContains("Rest", substring = true)
        compose.onNodeWithTag(
            com.sinura.personaltrainer.ui.components.WeekStripTags.cell(friday),
        ).assertTextContains("Workout", substring = true)
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

    @Test
    fun plannedRowOpensStartConfirmAndDoesNotStartUntilConfirm() {
        var started: String? = null
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = { started = it },
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
                today = TODAY,
            )
        }
        compose.onNodeWithText("Start Push?").assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.agendaRow("occ-pm")).performClick()
        compose.onNodeWithText("Start Push?").assertIsDisplayed()
        compose.onNodeWithText("1 Squat", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 Row", substring = true).assertIsDisplayed()
        compose.onNodeWithText("2 lifts · about 13 min", substring = true).assertIsDisplayed()
        org.junit.Assert.assertNull(started)
        compose.onNodeWithText("Start").performClick()
        org.junit.Assert.assertEquals("occ-pm", started)
    }

    @Test
    fun startConfirmCancelDoesNotStart() {
        var started: String? = null
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = { started = it },
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
                today = TODAY,
            )
        }
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithText("Start Push?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Start Push?").assertDoesNotExist()
        org.junit.Assert.assertNull(started)
    }

    @Test
    fun taggedRowOpensTheSameConfirmAsVolt() {
        var started: String? = null
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(CARDIO_ITEM, STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = { started = it },
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
                today = TODAY,
            )
        }
        compose.onNodeWithTag(HomeTags.agendaRow("occ-pm")).performClick()
        compose.onNodeWithText("Start Push?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithText("Start Push?").assertIsDisplayed()
        org.junit.Assert.assertNull(started)
    }

    @Test
    fun dayStackPrefersWorkoutOverStretchOnTheVolt() {
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(CARDIO_ITEM, STRENGTH_ITEM, STRETCH_ITEM),
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE, STRETCH_ROUTINE),
                today = TODAY,
            )
        }
        compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        compose.onNodeWithText("Start Push").assertIsDisplayed()
        compose.onNodeWithText("Start Stretch").assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.agendaRow("occ-stretch")).assertIsDisplayed()
    }

    @Test
    fun stillOpenLeftoverConfirmsAsDoItToday() {
        var started: String? = null
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = emptyList(),
                sessionLive = false,
                onStartOccurrence = { started = it },
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
                stillOpen = listOf(STRENGTH_ITEM),
                today = TODAY + 1,
            )
        }
        compose.onNodeWithTag(HomeTags.STILL_OPEN).assertIsDisplayed()
        compose.onNodeWithText("Start moves it to today.").assertIsDisplayed()
        compose.onNodeWithText("Friday  ·  Push").assertIsDisplayed()
        compose.onNodeWithText("Do Push today").assertIsDisplayed()
        compose.onNodeWithContentDescription("Do this session today").assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.agendaRow("occ-pm")).performClick()
        compose.onNodeWithText("Do Push today?").assertIsDisplayed()
        compose.onNodeWithText("This was Friday. Starting it today moves it here.").assertIsDisplayed()
        org.junit.Assert.assertNull(started)
        compose.onNodeWithText(com.sinura.personaltrainer.domain.MoveToToday.DO_IT_TODAY).performClick()
        org.junit.Assert.assertEquals("occ-pm", started)
    }

    @Test
    fun leftoverOnPastDayBoardConfirmsAsDoItToday() {
        var started: String? = null
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = { started = it },
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE),
                today = TODAY + 1,
            )
        }
        compose.onNodeWithText("Do Push today").assertIsDisplayed()
        compose.onNodeWithText("Start Push").assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.START).performClick()
        compose.onNodeWithText("Do Push today?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        org.junit.Assert.assertNull(started)
        compose.onNodeWithText("Do Push today?").assertDoesNotExist()
    }

    @Test
    fun todayWorkoutKeepsStartWhileStillOpenShowsLeftover() {
        val leftover = EXTRA_ITEM.copy(
            occurrence = EXTRA_ITEM.occurrence.copy(
                id = "occ-leftover",
                captured = EXTRA_ITEM.occurrence.captured.copy(localEpochDay = TODAY - 1),
            ),
        )
        setConstrainedContent(fontScale = 1f) {
            DailyAgendaCard(
                items = listOf(STRENGTH_ITEM),
                sessionLive = false,
                onStartOccurrence = {},
                onStartFree = {},
                routines = listOf(PUSH_ROUTINE, EXTRA_ROUTINE),
                stillOpen = listOf(leftover),
                today = TODAY,
            )
        }
        compose.onNodeWithText("Start Push").assertIsDisplayed()
        compose.onNodeWithText("Do Push today").assertDoesNotExist()
        compose.onNodeWithTag(HomeTags.STILL_OPEN).assertIsDisplayed()
        compose.onNodeWithTag(HomeTags.agendaRow("occ-leftover")).assertIsDisplayed()
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
        val STRETCH_ITEM = AgendaItem(
            occurrence = ScheduleOccurrence(
                id = "occ-stretch",
                ruleId = "rule-stretch",
                status = OccurrenceStatus.PLANNED,
                captured = CapturedCivilTime(1L, "UTC", 0, TODAY),
                hour = 20,
                minute = 0,
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            rule = ScheduleRule(
                id = "rule-stretch",
                weekday = Weekday.MONDAY,
                hour = 20,
                minute = 0,
                modality = ScheduleModality.STRENGTH,
                routineId = "r-stretch",
                templateId = ScheduleKind.aux("stretch"),
                createdAtMs = 1L,
                updatedAtMs = 1L,
            ),
            routineName = "Stretch",
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
        val STRETCH_ROUTINE = Routine(
            id = "r-stretch",
            name = "Stretch",
            notes = "",
            createdAt = 0L,
            updatedAt = 0L,
            exercises = listOf("Calf stretch", "Couch stretch").mapIndexed { index, name ->
                RoutineExercise(
                    id = "stretch-$index",
                    routineId = "r-stretch",
                    exercise = Exercise(
                        id = "ex-stretch-$index",
                        name = name,
                        muscleGroup = "Hips",
                        notes = "",
                        isCustom = false,
                    ),
                    sortOrder = index,
                    targetSets = 1,
                    targetReps = 8,
                    targetWeightKg = null,
                    restSeconds = 20,
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
