package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.ExerciseFloorStatsPresentation
import com.sinura.personaltrainer.domain.ExerciseSessionSummary
import com.sinura.personaltrainer.domain.ExerciseSetRecord
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.domain.setMicroRecInputs
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
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WorkoutFloorComponentsTest {
    @get:Rule val compose = createComposeRule()

    private val unit = FLOOR_UNIT
    private val kg70 = FLOOR_KG70

    private fun show(content: @Composable () -> Unit) = compose.showFloor(content = content)

    @Test
    fun headerSaysWhereTheSessionStandsAndFinishWaitsForASet() {
        val session = floorSession(listOf(floorSet(1, kg70, 10, rpe = 8), floorSet(2, kg70, 10, rpe = 9)), targetSets = 3, secondLift = true)
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
        // The plan line wears the instrument-label voice, which is always uppercase.
        compose.onNodeWithText("1 OF 2 EXERCISES · 2 OF 6 SETS").assertIsDisplayed()
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
                lift = floorLift(targetSets = 3),
                number = 1,
                total = 2,
                setContext = "Working set 3 of 3",
                draftWarmup = false,
                onWarmup = { warmup = it },
                onOpenSwitcher = { switched += 1 },
                onDetails = { details += 1 },
            )
        }
        compose.onNodeWithText("Leg Extension").assertIsDisplayed()
        compose.onNodeWithText("MACHINE").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.SET_CONTEXT, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("2/3 working sets", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.WORKING_CHIP).assertIsSelected()
        compose.onNodeWithTag(WorkoutTestTags.WARMUP_CHIP).assertIsNotSelected().performClick()
        assertEquals(true, warmup)
        compose.onNodeWithTag(WorkoutTestTags.DETAILS).performClick()
        assertEquals(1, details)
        // The switcher opens from its own visible control, not from the whole identity.
        compose.onNodeWithTag(WorkoutTestTags.liftCard("leg-ext")).performClick()
        assertEquals(0, switched)
        compose.onNodeWithTag(WorkoutTestTags.LIFT_SWITCH).assertTextContains("Lift 1 of 2").performClick()
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
            session = floorSession(emptyList(), targetSets = 3),
            exerciseId = "leg-ext",
            lastPerformance = previous,
            priorHistory = previous.sets,
            unit = unit,
        )
        var applied: Pair<Double, Int>? = null
        show { ExerciseStatsRow(stats = stats, unit = unit, onApplyLastSet = { kg, reps -> applied = kg to reps }) }
        // Last time's 70 × 9 is also the standing best, so the value shows twice.
        compose.onAllNodesWithText("70 × 9").assertCountEquals(2)
        compose.onNodeWithText("Last time · RPE 8").assertIsDisplayed()
        compose.onNodeWithText("—").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.STAT_LAST).performClick()
        assertEquals(kg70 to 9, applied)
    }

    @Test
    fun preparePhaseStatsRowShowsLastTimeOnly() {
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
            session = floorSession(emptyList(), targetSets = 3),
            exerciseId = "leg-ext",
            lastPerformance = previous,
            priorHistory = previous.sets,
            unit = unit,
        )
        show {
            ExerciseStatsRow(
                stats = stats,
                unit = unit,
                visibility = ExerciseFloorStatsPresentation.rowVisibility(workingSetsLoggedToday = 0),
            )
        }
        compose.onNodeWithText("70 × 9").assertIsDisplayed()
        compose.onNodeWithText("Last time · RPE 8").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.STAT_BEST).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.STAT_VOLUME).assertDoesNotExist()
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
                onWeightKgChange = { weight = it },
                onRepsChange = { reps = it },
                onSecondsChange = {},
            )
        }
        val step = WeightConverter.formatDisplayNumber(
            IncrementTable.displayStep(LoadType.EXTERNAL, unit, EquipmentType.MACHINE) ?: unit.step,
        )
        // No headings over the wells and no source label under them: the unit beside the
        // number says which is the weight, and each well still names its field aloud.
        compose.onAllNodesWithText("WEIGHT").assertCountEquals(0)
        compose.onAllNodesWithText("REPS").assertCountEquals(0)
        compose.onAllNodesWithText("Plan").assertCountEquals(0)
        // Each numeral is spoken as its field and value, so its digits are read unmerged.
        compose.onNodeWithText("lb", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("70", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("10", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assert(hasContentDescription("Weight 70 lb"))
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assert(hasContentDescription("Reps 10"))
        compose.onNodeWithContentDescription("Increase reps by 1").performClick()
        assertEquals(11, reps)
        compose.onNodeWithContentDescription("Increase weight by $step lb").performClick()
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
                onWeightKgChange = {},
                onRepsChange = {},
                onSecondsChange = {},
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assertExists()
        compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).assertDoesNotExist()
        compose.onAllNodesWithText("TIME").assertCountEquals(0)
        compose.onNodeWithText("0:30", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assert(hasContentDescription("Time 0:30"))
    }

    @Test
    fun rpeTrackSelectsClearsAndHidesForWarmups() {
        var chosen: Int? = 99
        show { RpeSelector(enabled = true, warmup = false, rpe = null, recommendedRpe = 8, onRpe = { chosen = it }) }
        compose.onNodeWithTag(WorkoutTestTags.RPE_TRACK).assertExists()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Max effort").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.RPE_CLEAR).assertDoesNotExist()
        // One sentence per chip: its digit is not read after it, and "selected" is left to
        // the radio's own state.
        val eight = compose.onNodeWithTag(WorkoutTestTags.rpeChoice(8))
        assertEquals(listOf("RPE 8, about two reps left, recommended"), eight.spokenDescriptions())
        assertTrue("was ${eight.mergedTexts()}", eight.mergedTexts().isEmpty())
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
    fun compactNextSetStripKeepsWhyAndApplyWithoutTheKicker() {
        val rec = checkNotNull(
            SetMicroRecCalculator.suggest(
                setMicroRecInputs(
                    editing = false,
                    loadType = LoadType.EXTERNAL,
                    unit = unit,
                    targetSets = 3,
                    targetReps = 10,
                    targetWeightKg = kg70,
                    working = emptyList(),
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
        show {
            NextSetRecommendation(
                rec = rec,
                loadClass = LoadClass.LOADED,
                unit = unit,
                applied = false,
                enabled = true,
                onApply = {},
                compact = true,
            )
        }
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET_COMPACT).assertIsDisplayed()
        compose.onNodeWithText("NEXT SET").assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsDisplayed()
        compose.onNodeWithText(SetMicroRecCopy.numbers(rec, LoadClass.LOADED, unit)).assertIsDisplayed()
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
        compose.onNodeWithText("Applied", useUnmergedTree = true).assertIsDisplayed()
        // Said once: "Suggestion applied, …" and not the visible word after it.
        assertTrue(compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).mergedTexts().isEmpty())
    }

    @Test
    fun setHistoryChipsOpenTheirMenu() {
        var edited: String? = null
        show {
            SetHistoryStrip(
                sets = listOf(floorSet(1, kg70, 10, rpe = 8), floorSet(2, kg70, 10, rpe = 9)),
                targetSets = 3,
                loadClass = LoadClass.LOADED,
                unit = unit,
                editingSetId = null,
                receiptSetId = "set-2",
                current = CurrentSetMark(mark = "3", label = "Working set 3 of 3"),
                enabled = true,
                onEdit = { edited = it },
                onDelete = {},
                onOpenAll = {},
            )
        }
        compose.onNodeWithText("SET HISTORY").assertIsDisplayed()
        // Each chip is spoken as one sentence, so its visible words are read unmerged.
        compose.onNodeWithText("70 × 10 @ 8", useUnmergedTree = true).assertIsDisplayed()
        // A resting chip no longer repeats the number its marker ring already shows; the
        // ordinal stays in the row's spoken form and in the menu that opens from it.
        compose.onAllNodesWithText("Set 1 of 3", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag(WorkoutTestTags.setChip("set-1"))
            .assert(hasContentDescription("Set 1 of 3", substring = true))
        compose.onNodeWithText("Saved · Set 2 of 3", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.CURRENT_SET).assertIsDisplayed()
        compose.onNodeWithText("Current", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.setChip("set-1")).performClick()
        compose.onNodeWithText("Revise Set 1 of 3").performClick()
        assertEquals("set-1", edited)
        // One "Add set" on the floor, and it is the dock's (W1a): none in the history.
        compose.onNodeWithText("Add set", substring = true, useUnmergedTree = true).assertDoesNotExist()
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
}
