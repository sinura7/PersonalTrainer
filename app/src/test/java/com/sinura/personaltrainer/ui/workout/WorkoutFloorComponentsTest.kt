package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.setMicroRecInputs
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The redesigned floor's components, composed for real on the JVM.
 *
 * Each block proves the contract the migration brief states: what a control is called,
 * what it announces, what it calls back with, and what it refuses to do (Apply never logs,
 * a warm-up shows no RPE track, a bodyweight lift has no weight column).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class WorkoutFloorComponentsTest {
    @get:Rule val compose = createComposeRule()

    private val unit = WeightUnit.LBS
    private val kg70 = WeightConverter.lbsToKg(70.0)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides unit) {
                PersonalTrainerTheme { content() }
            }
        }
    }

    @Test
    fun headerSaysWhereTheSessionStandsAndFinishWaitsForASet() {
        val session = session(listOf(set(1, kg70, 10, rpe = 8), set(2, kg70, 10, rpe = 9)), targetSets = 3, secondLift = true)
        val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = "leg-ext")
        show {
            WorkoutHeader(
                routineName = "Lower B",
                progress = progress,
                canFinish = false,
                compact = false,
                onExit = {},
                onFinish = {},
            )
        }
        compose.onNodeWithText("Lower B").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_LINE).assertIsDisplayed()
        compose.onNodeWithText("Exercise 1 of 2 · 2 of 6 sets").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.PROGRESS_BAR).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.FINISH).assertIsNotEnabled()
        compose.onNodeWithContentDescription("Exit workout").assertExists()
    }

    @Test
    fun exerciseHeaderNamesTheLiftAndSwitchesSetType() {
        var warmup: Boolean? = null
        var switched = 0
        var details = 0
        show {
            ExerciseHeader(
                lift = lift(targetSets = 3),
                number = 1,
                total = 2,
                workingLogged = 2,
                setContext = "Set 3 of 3",
                draftWarmup = false,
                onWarmup = { warmup = it },
                onOpenSwitcher = { switched += 1 },
                onDetails = { details += 1 },
            )
        }
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
        compose.onNodeWithText("MACHINE").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT).assertIsDisplayed()
        compose.onNodeWithText("2/3 working sets").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP).assertIsSelected()
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsNotSelected().performClick()
        assertEquals(true, warmup)
        compose.onNodeWithTag(WorkoutTestTags.DETAILS).performClick()
        assertEquals(1, details)
        compose.onNodeWithTag(WorkoutTestTags.liftCard("leg-ext")).performClick()
        assertEquals(1, switched)
    }

    @Test
    fun statsRowReadsTheSavedRowsAndLastTimeCanBeApplied() {
        val previous = ExerciseSessionSummary(
            sessionId = "old",
            sessionName = "Lower B",
            performedAtMs = 1L,
            topSet = null,
            workingSets = 1,
            volumeKg = 0.0,
            estimatedOneRepMaxKg = null,
            sets = listOf(ExerciseSetRecord(setId = "a", sessionId = "old", weightKg = kg70, reps = 9, completedAt = 1L, rpe = 8)),
        )
        val stats = ExerciseFloorStatsCalculator.of(
            session = session(emptyList(), targetSets = 3),
            exerciseId = "leg-ext",
            lastPerformance = previous,
            priorHistory = previous.sets,
            unit = unit,
        )
        var applied: Pair<Double, Int>? = null
        show { ExerciseStatsRow(stats = stats, unit = unit, onApplyLastSet = { kg, reps -> applied = kg to reps }) }
        compose.onNodeWithText("70 × 9 @ 8").assertIsDisplayed()
        compose.onNodeWithText("Last time").assertIsDisplayed()
        compose.onNodeWithText("70 × 9").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.STAT_LAST).performClick()
        assertEquals(kg70 to 9, applied)
    }

    @Test
    fun editorStepsWithTheEquipmentRuleAndNamesEveryControl() {
        var weight: Double? = null
        var reps: Int? = null
        show {
            WeightRepsEditor(
                enabled = true,
                weightKg = kg70,
                reps = 10,
                unit = unit,
                loadClass = LoadClass.LOADED,
                loadType = LoadType.EXTERNAL,
                equipment = EquipmentType.MACHINE,
                movementKey = null,
                plated = false,
                hold = false,
                holdSeconds = null,
                holdRunning = false,
                holdRemainingSeconds = 0,
                sourceLabel = "Plan",
                onWeightKgChange = { weight = it },
                onRepsChange = { reps = it },
                onSecondsChange = {},
            )
        }
        val step = WeightConverter.formatDisplayNumber(
            IncrementTable.displayStep(LoadType.EXTERNAL, unit, EquipmentType.MACHINE) ?: unit.step,
        )
        compose.onNodeWithText("WEIGHT (LBS)").assertIsDisplayed()
        compose.onNodeWithText("REPS").assertIsDisplayed()
        compose.onNodeWithText("70").assertIsDisplayed()
        compose.onNodeWithText("10").assertIsDisplayed()
        compose.onNodeWithText("Plan").assertIsDisplayed()
        compose.onNodeWithContentDescription("Increase reps by 1").performClick()
        assertEquals(11, reps)
        compose.onNodeWithContentDescription("Increase weight by $step lbs").performClick()
        assertEquals(
            FloorStepper.nextWeightKg(kg70, unit, 1, LoadType.EXTERNAL, EquipmentType.MACHINE),
            checkNotNull(weight),
            0.0001,
        )
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assertExists()
    }

    @Test
    fun bodyweightLiftsHaveNoWeightColumnAndHoldsShowTime() {
        show {
            WeightRepsEditor(
                enabled = true,
                weightKg = 0.0,
                reps = 12,
                unit = unit,
                loadClass = LoadClass.BODYWEIGHT,
                loadType = LoadType.BODYWEIGHT,
                equipment = EquipmentType.BODYWEIGHT,
                movementKey = null,
                plated = false,
                hold = false,
                holdSeconds = null,
                holdRunning = false,
                holdRemainingSeconds = 0,
                sourceLabel = null,
                onWeightKgChange = {},
                onRepsChange = {},
                onSecondsChange = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assertExists()
    }

    @Test
    fun holdEditorShowsSecondsNotReps() {
        show {
            WeightRepsEditor(
                enabled = true,
                weightKg = 0.0,
                reps = 0,
                unit = unit,
                loadClass = LoadClass.BODYWEIGHT,
                loadType = LoadType.BODYWEIGHT,
                equipment = EquipmentType.BODYWEIGHT,
                movementKey = null,
                plated = false,
                hold = true,
                holdSeconds = 30,
                holdRunning = false,
                holdRemainingSeconds = 0,
                sourceLabel = null,
                onWeightKgChange = {},
                onRepsChange = {},
                onSecondsChange = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assertDoesNotExist()
        compose.onNodeWithText("TIME").assertIsDisplayed()
        compose.onNodeWithText("0:30").assertIsDisplayed()
    }

    @Test
    fun rpeTrackSelectsClearsAndHidesForWarmups() {
        var chosen: Int? = 99
        show { RpeSelector(enabled = true, warmup = false, rpe = null, recommendedRpe = 8, onRpe = { chosen = it }) }
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertExists()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Max effort").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8)).assert(hasContentDescription("RPE 8, about two reps left, not selected, recommended"))
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(9)).performClick()
        assertEquals(9, chosen)
    }

    @Test
    fun rpeSelectedChipClearsOnSecondTapAndHelpOpens() {
        var chosen: Int? = 99
        show { RpeSelector(enabled = true, warmup = false, rpe = 9, recommendedRpe = null, onRpe = { chosen = it }) }
        compose.onNodeWithTag(WorkoutTestTags.rpeChoice(9)).assertIsSelected().performClick()
        assertNull(chosen)
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.RPE_HELPER).performClick()
        compose.onNodeWithText("Effort (RPE)").assertIsDisplayed()
    }

    @Test
    fun warmupDraftShowsTheReasonInsteadOfTheTrack() {
        show { RpeSelector(enabled = true, warmup = true, rpe = null, recommendedRpe = null, onRpe = {}) }
        compose.onNodeWithTag(WorkoutTestTags.RPE_WARMUP_REASON).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertDoesNotExist()
    }

    @Test
    fun nextSetCardAppliesOnceAndExplainsItself() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                setMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = unit,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = kg70,
                    working = listOf(LoggedSetView(weightKg = kg70, reps = 10, rpe = 9, isWarmup = false)),
                    lastAnySetWasWarmup = false,
                    hint = null,
                    lighterWeek = false,
                    draftWeightKg = kg70,
                    draftReps = 10,
                    draftRpe = null,
                    nowMs = 0L,
                    todayEpochDay = 0L,
                    rpeIntent = true,
                    equipment = EquipmentType.MACHINE,
                ),
            ),
        )
        assertTrue(SetMicroRecCopy.visibleOnEntry(rec))
        var applied = 0
        show { NextSetRecommendation(rec = rec, loadClass = LoadClass.LOADED, unit = unit, applied = false, enabled = true, onApply = { applied += 1 }) }
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET).assertExists()
        compose.onNodeWithText("NEXT SET").assertIsDisplayed()
        compose.onNodeWithText(SetMicroRecCopy.numbers(rec, LoadClass.LOADED, unit)).assertIsDisplayed()
        assertNotNull(SetMicroRecCopy.deltaLine(rec, LoadClass.LOADED, unit))
        if (rec.showApply && !rec.previewOnly) {
            compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).performClick()
            assertEquals(1, applied)
        }
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onNodeWithText("Why this set").assertIsDisplayed()
    }

    @Test
    fun appliedRecommendationStandsDown() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                setMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = unit,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = kg70,
                    working = listOf(LoggedSetView(weightKg = kg70, reps = 10, rpe = 9, isWarmup = false)),
                    lastAnySetWasWarmup = false,
                    hint = null,
                    lighterWeek = false,
                    draftWeightKg = kg70,
                    draftReps = 10,
                    draftRpe = null,
                    nowMs = 0L,
                    todayEpochDay = 0L,
                    rpeIntent = true,
                    equipment = EquipmentType.MACHINE,
                ),
            ),
        )
        if (!(rec.showApply && !rec.previewOnly)) return
        show { NextSetRecommendation(rec = rec, loadClass = LoadClass.LOADED, unit = unit, applied = true, enabled = true, onApply = {}) }
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsNotEnabled()
        compose.onNodeWithText("Applied").assertIsDisplayed()
    }

    @Test
    fun setHistoryChipsOpenTheirMenuAndOfferAddSet() {
        var edited: String? = null
        var added = 0
        show {
            SetHistoryStrip(
                sets = listOf(set(1, kg70, 10, rpe = 8), set(2, kg70, 10, rpe = 9)),
                targetSets = 3,
                loadClass = LoadClass.LOADED,
                unit = unit,
                editingSetId = null,
                receiptSetId = "set-2",
                current = CurrentSetMark(mark = "3", label = "Set 3 of 3"),
                showAddSet = true,
                enabled = true,
                onEdit = { edited = it },
                onDelete = {},
                onOpenAll = {},
                onAddSet = { added += 1 },
            )
        }
        compose.onNodeWithText("SET HISTORY").assertIsDisplayed()
        compose.onNodeWithText("70 × 10 @ 8").assertIsDisplayed()
        compose.onNodeWithText("Set 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Saved · Set 2 of 3").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_SET).assertIsDisplayed()
        compose.onNodeWithText("Current").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.setOptions("set-1")).performClick()
        compose.onNodeWithText("Revise set 1").performClick()
        assertEquals("set-1", edited)
        compose.onNodeWithTag(WorkoutTestTags.ADD_SET).performClick()
        assertEquals(1, added)
        compose.onNodeWithTag(WorkoutTestTags.VIEW_SETS).assertExists()
    }

    @Test
    fun restCardRunsRestsAndFlashesDone() {
        var nudged: Int? = null
        var skipped = 0
        show {
            RestTimerCard(
                remainingSeconds = 92,
                totalSeconds = 120,
                running = true,
                completedTimerId = null,
                afterWarmup = false,
                offerSetClock = false,
                onSkip = { skipped += 1 },
                onStart = {},
                onNudge = { nudged = it },
                onEditDuration = {},
                onStartSetClock = {},
                onOpenRest = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.REST_BAR).assertExists()
        compose.onNodeWithText("REST").assertIsDisplayed()
        compose.onNodeWithText("1:32").assertIsDisplayed()
        compose.onNodeWithText("Target 2:00").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_MINUS).performClick()
        assertEquals(-15, nudged)
        compose.onNodeWithTag(WorkoutTestTags.REST_PLUS).performClick()
        assertEquals(15, nudged)
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        assertEquals(1, skipped)
    }

    @Test
    fun idleRestCardIsTheInstrumentAtRest() {
        var started = 0
        var edits = 0
        show {
            RestTimerCard(
                remainingSeconds = 0,
                totalSeconds = 120,
                running = false,
                completedTimerId = null,
                afterWarmup = false,
                offerSetClock = true,
                onSkip = {},
                onStart = { started += 1 },
                onNudge = {},
                onEditDuration = { edits += 1 },
                onStartSetClock = {},
                onOpenRest = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.REST_IDLE).assertExists()
        compose.onNodeWithText("2:00").assertIsDisplayed()
        compose.onNodeWithText("Planned").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_SKIP).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.START_SET_CLOCK).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.START_REST).performClick()
        assertEquals(1, started)
        compose.onNodeWithContentDescription("Rest is not running. Rest 2:00. Tap to change duration.").performClick()
        assertEquals(1, edits)
    }

    private fun lift(targetSets: Int, id: String = "leg-ext", name: String = "Leg Extension") = SessionExercise(
        id = "se-$id",
        sessionId = "s1",
        exercise = Exercise(
            id = id, name = name, muscleGroup = "Legs", notes = "", isCustom = false,
            equipment = EquipmentType.MACHINE, loadType = LoadType.EXTERNAL,
        ),
        sortOrder = 0,
        targetSets = targetSets,
        targetReps = 10,
        targetWeightKg = kg70,
        restSeconds = 120,
    )

    private fun session(sets: List<SetLog>, targetSets: Int, secondLift: Boolean = false) = WorkoutSession(
        id = "s1",
        routineId = null,
        routineName = "Lower B",
        date = 1L,
        notes = "",
        durationMinutes = 0,
        startedAt = 1L,
        finishedAt = null,
        exercises = if (secondLift) listOf(lift(targetSets), lift(targetSets = 3, id = "leg-curl", name = "Leg Curl")) else listOf(lift(targetSets)),
        sets = sets,
    )

    private fun set(number: Int, weightKg: Double, reps: Int, rpe: Int? = null) = SetLog(
        id = "set-$number",
        sessionId = "s1",
        exerciseId = "leg-ext",
        exerciseName = "Leg Extension",
        setNumber = number,
        weightKg = weightKg,
        reps = reps,
        rpe = rpe,
        isWarmup = false,
        completedAt = number.toLong(),
    )
}
