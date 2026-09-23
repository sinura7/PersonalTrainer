package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.FloorStepper
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.NumericEntry
import com.sinura.personaltrainer.domain.PlateMath
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.StepperRepeat
import com.sinura.personaltrainer.domain.UnloadedLoad
import com.sinura.personaltrainer.domain.WarmupSet
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.toWeightLabel
import com.sinura.personaltrainer.ui.components.NumberEntryTags
import com.sinura.personaltrainer.ui.theme.LogLoopScale
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two numbers about to be logged, composed for real.
 *
 * WeightRepsEditor.kt used to be held in place by its own source text: the `val inline`
 * sample rule, the Row and Column slices, `RoundPlate(label = "−", spoken = …)`, the
 * `CustomAccessibilityAction(…)` lines, the plate tokens. W1a's numeric-entry cue edits
 * exactly that code, so every one of those pins would break without saying whether a
 * lifter lost anything. These tests hold what the lifter actually has: the actions
 * TalkBack offers and what they do, plates that say the same words and keep a full thumb,
 * a tap that opens the keypad and a typed value that lands, side-by-side numerals that
 * stack at large text, and plates that stay put as digits come and go.
 *
 * Native graphics, as in WorkoutFloorRenderTest: the numeral size and the plates' place
 * come from measured text, which the legacy renderer measures at almost no width.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class WeightRepsEditorRenderTest {
    @get:Rule val compose = createComposeRule()

    private val weights = mutableListOf<Double>()
    private val repsSeen = mutableListOf<Int>()
    private val seconds = mutableListOf<Int>()

    private fun showEditor(
        weightKg: Double = FLOOR_KG70,
        reps: Int = 10,
        loadType: LoadType = LoadType.EXTERNAL,
        equipment: EquipmentType = EquipmentType.MACHINE,
        plated: Boolean = false,
        hold: Boolean = false,
        holdRunning: Boolean = false,
        enabled: Boolean = true,
        fontScale: Float = 1f,
    ) {
        compose.showFloor(fontScale = fontScale) {
            WeightRepsEditor(
                enabled = enabled,
                weightKg = weightKg,
                reps = reps,
                unit = FLOOR_UNIT,
                loadClass = LoadClass.of(loadType),
                loadType = loadType,
                equipment = equipment,
                movementKey = null,
                plated = plated,
                hold = hold,
                holdSeconds = if (hold) HOLD_SECONDS else null,
                holdRunning = holdRunning,
                holdRemainingSeconds = if (holdRunning) HOLD_REMAINING else 0,
                onWeightKgChange = { weights += it },
                onRepsChange = { repsSeen += it },
                onSecondsChange = { seconds += it },
            )
        }
    }

    /** The step the plates and actions name, by the same rule every weight field obeys. */
    private fun step(loadType: LoadType, equipment: EquipmentType): String =
        IncrementTable.displayStep(loadType, FLOOR_UNIT, equipment)?.let { WeightConverter.formatDisplayNumber(it) }
            ?: FLOOR_UNIT.stepLabel

    @Test
    fun eachNumeralOffersDecreaseIncreaseAndTypeToTalkBack() {
        showEditor()
        val step = step(LoadType.EXTERNAL, EquipmentType.MACHINE)
        val weight = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER)
        val reps = compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER)
        assertEquals(listOf("Decrease weight by $step lb", "Increase weight by $step lb", "Type a weight"), weight.customActionLabels())
        assertEquals(listOf("Decrease reps by 1", "Increase reps by 1", "Type a rep count"), reps.customActionLabels())
        compose.runCustomAction(reps, "Increase reps by 1")
        compose.runCustomAction(reps, "Decrease reps by 1")
        assertEquals(listOf(FloorStepper.nextReps(10, 1), FloorStepper.nextReps(10, -1)), repsSeen)
        compose.runCustomAction(weight, "Increase weight by $step lb")
        compose.runCustomAction(weight, "Decrease weight by $step lb")
        assertEquals(
            listOf(
                FloorStepper.nextWeightKg(FLOOR_KG70, FLOOR_UNIT, 1, LoadType.EXTERNAL, EquipmentType.MACHINE),
                FloorStepper.nextWeightKg(FLOOR_KG70, FLOOR_UNIT, -1, LoadType.EXTERNAL, EquipmentType.MACHINE),
            ),
            weights,
        )
        compose.withKeypad(on = reps, value = "7", byAction = "Type a rep count") {
            compose.onNodeWithText("Reps").assertExists()
        }
        assertEquals(7, repsSeen.last())
    }

    @Test
    fun theRoundPlatesSayTheActionWordsAndKeepAFullThumb() {
        showEditor()
        val step = step(LoadType.EXTERNAL, EquipmentType.MACHINE)
        // A sighted tap and a TalkBack action agree: each plate is announced as the same
        // words its numeral offers in the actions menu.
        val plateWords = listOf("Decrease weight by $step lb", "Increase weight by $step lb", "Decrease reps by 1", "Increase reps by 1")
        val actionWords = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).customActionLabels().take(2) +
            compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).customActionLabels().take(2)
        assertEquals(plateWords, actionWords)
        plateWords.forEach { words ->
            compose.onNodeWithContentDescription(words)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(Metrics.touchMin)
                .assertWidthIsAtLeast(Metrics.touchMin)
        }
        compose.onNodeWithContentDescription("Increase reps by 1").performClick()
        compose.onNodeWithContentDescription("Decrease weight by $step lb").performClick()
        assertEquals(listOf(11), repsSeen)
        assertEquals(listOf(FloorStepper.nextWeightKg(FLOOR_KG70, FLOOR_UNIT, -1, LoadType.EXTERNAL, EquipmentType.MACHINE)), weights)
    }

    @Test
    fun holdingAPlateRepeatsTheStepAndLettingGoAddsNothing() {
        showEditor()
        val plate = compose.onNodeWithContentDescription("Increase reps by 1")
        compose.mainClock.autoAdvance = false
        plate.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(StepperRepeat.HOLD_BEFORE_REPEAT_MS + StepperRepeat.REPEAT_MS * 2 + 50)
        val whileHeld = repsSeen.size
        plate.performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("a held plate keeps stepping, was $whileHeld steps", whileHeld >= 2)
        assertEquals("the release must not add one more step", whileHeld, repsSeen.size)
        assertTrue(repsSeen.all { it == 11 })
    }

    @Test
    fun tappingTheWeightOpensItsKeypadAndATypedValueLands() {
        showEditor()
        val weight = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER)
        assertEquals("Type a weight", weight.clickLabel())
        weight.assertHeightIsAtLeast(Metrics.touchMin)
        val helper = SetCopy.weightKeypadHelper(
            LoadClass.LOADED,
            UnloadedLoad.allowsZeroWorkingWeight(LoadType.EXTERNAL, EquipmentType.MACHINE, null),
        )
        compose.withKeypad(on = weight, value = "82.5") {
            compose.onNodeWithText(WeightMeaning.LIFTED.fieldLabel).assertExists()
            compose.onNodeWithText(helper, substring = true).assertExists()
            compose.onNodeWithTag(NumberEntryTags.FIELD).assert(hasText("70"))
        }
        assertEquals(listOf(checkNotNull(NumericEntry.parseWeightKg("82.5", FLOOR_UNIT))), weights)
        compose.onNodeWithText(WeightMeaning.LIFTED.fieldLabel).assertDoesNotExist()
    }

    @Test
    fun tappingTheRepsOpensItsKeypadAndATypedCountLands() {
        showEditor()
        val reps = compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER)
        assertEquals("Type a rep count", reps.clickLabel())
        reps.assertHeightIsAtLeast(Metrics.touchMin)
        compose.withKeypad(on = reps, value = "12") {
            compose.onNodeWithText("Reps").assertExists()
        }
        assertEquals(listOf(12), repsSeen)
        compose.onNodeWithText("Reps").assertDoesNotExist()
    }

    @Test
    fun weightAndRepsSitSideBySideAtNormalText() {
        // 102.5 lb is the widest weight a narrow phone has to hold without crossing into reps.
        val kg = WeightConverter.lbsToKg(102.5)
        showEditor(weightKg = kg, reps = 888)
        val weight = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).getBoundsInRoot()
        val reps = compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).getBoundsInRoot()
        assertTrue("weight must stay left of reps, was $weight and $reps", weight.right <= reps.left)
        assertTrue("one row: the two numerals overlap vertically", weight.top < reps.bottom && reps.top < weight.bottom)
        // The unit rides the weight's own numeral; reps carries none.
        val unit = compose.onNode(hasText(FLOOR_UNIT.suffix) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
            .assertIsDisplayed()
        // The boxes above keep their place whatever the type size; the words inside them do
        // not. At a size too big for the column, "102.5 lb" keeps the box and draws past it
        // into reps, so the numeral and its unit must each be laid out whole in their room.
        val value = SetCopy.weightEntryHero(WeightMeaning.LIFTED, kg, FLOOR_UNIT).value
        val numeral = compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
        listOf(value to numeral, FLOOR_UNIT.suffix to unit).forEach { (words, node) ->
            assertTrue("\"$words\" must fit its column at normal text, not run past it", node.textLayout().fitsItsWidth())
        }
        assertAnchorHolds(weight, reps)
    }

    @Test
    fun largeTextStacksRepsUnderWeightAndKeepsTheAnchor() {
        showEditor(fontScale = LogLoopScale.STACK_WELLS_FROM)
        val weight = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).getBoundsInRoot()
        val reps = compose.onNodeWithTag(WorkoutTestTags.REPS_STEPPER).getBoundsInRoot()
        assertTrue("reps must stack under weight at large text, was $weight and $reps", reps.top >= weight.bottom)
        compose.onNodeWithContentDescription("Increase reps by 1").assertHeightIsAtLeast(Metrics.touchMin)
        assertAnchorHolds(weight, reps)
    }

    /**
     * The log loop scrolls to SET_ENTRY after a save; it must be the numerals' own root in
     * either layout (LogLoopBringIntoView's regression: anchoring on the set history).
     */
    private fun assertAnchorHolds(weight: DpRect, reps: DpRect) {
        val entry = compose.onNodeWithTag(WorkoutTestTags.SET_ENTRY).assertIsDisplayed().getBoundsInRoot()
        listOf(weight, reps).forEach { numeral ->
            assertTrue(
                "SET_ENTRY must hold both numerals, was $entry around $numeral",
                numeral.left >= entry.left && numeral.right <= entry.right && numeral.top >= entry.top && numeral.bottom <= entry.bottom,
            )
        }
    }

    @Test
    fun thePlatesStayPutAsDigitsComeAndGo() {
        var reps by mutableStateOf(5)
        var weightKg by mutableStateOf(WeightConverter.lbsToKg(5.0))
        var holdSeconds by mutableStateOf<Int?>(null)
        compose.showFloor {
            // Once holdSeconds is set this is a weighted plank: a hold beside its added weight.
            val hold = holdSeconds != null
            WeightRepsEditor(
                enabled = true,
                weightKg = if (hold) WeightConverter.lbsToKg(25.0) else weightKg,
                reps = reps,
                unit = FLOOR_UNIT,
                loadClass = if (hold) LoadClass.BODYWEIGHT_ADDED else LoadClass.LOADED,
                loadType = if (hold) LoadType.BODYWEIGHT_PLUS else LoadType.EXTERNAL,
                equipment = if (hold) EquipmentType.BODYWEIGHT else EquipmentType.MACHINE,
                movementKey = null,
                plated = false,
                hold = hold,
                holdSeconds = holdSeconds,
                holdRunning = false,
                holdRemainingSeconds = 0,
                onWeightKgChange = {},
                onRepsChange = {},
                onSecondsChange = {},
            )
        }
        val step = step(LoadType.EXTERNAL, EquipmentType.MACHINE)
        val plates = listOf("Decrease weight by $step lb", "Increase weight by $step lb", "Decrease reps by 1", "Increase reps by 1")
        val before = plates.map { compose.onNodeWithContentDescription(it).getBoundsInRoot() }
        reps = 100
        weightKg = WeightConverter.lbsToKg(102.5)
        compose.waitForIdle()
        val after = plates.map { compose.onNodeWithContentDescription(it).getBoundsInRoot() }
        assertEquals("the plates are placed from a fixed sample, not the live value", before, after)
        // A hold runs from seconds to minutes. Beside its added weight the clock has half a
        // row, where a short hold would fit its plates beside it and a long one would push
        // them underneath; they are placed from 88:88, so they do neither.
        holdSeconds = 5
        compose.waitForIdle()
        val timePlates = listOf("Decrease time by ${HoldWork.STEP_SECONDS} seconds", "Increase time by ${HoldWork.STEP_SECONDS} seconds")
        val short = timePlates.map { compose.onNodeWithContentDescription(it).getBoundsInRoot() }
        compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assert(hasContentDescription("Time ${HoldWork.clock(5)}"))
        holdSeconds = 600
        compose.waitForIdle()
        compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assert(hasContentDescription("Time ${HoldWork.clock(600)}"))
        val long = timePlates.map { compose.onNodeWithContentDescription(it).getBoundsInRoot() }
        assertEquals("a hold's plates are placed from a fixed sample too, not the live clock", short, long)
    }

    @Test
    fun theHoldNumeralStepsInFiveSecondsAndTakesTypedTime() {
        showEditor(weightKg = 0.0, reps = 0, loadType = LoadType.BODYWEIGHT, equipment = EquipmentType.BODYWEIGHT, hold = true)
        val hold = compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER)
        val step = HoldWork.STEP_SECONDS
        assertEquals(
            listOf("Decrease time by $step seconds", "Increase time by $step seconds", "Type hold seconds"),
            hold.customActionLabels(),
        )
        assertEquals("Type hold seconds", hold.clickLabel())
        compose.runCustomAction(hold, "Increase time by $step seconds")
        assertEquals(listOf(FloorStepper.nextHoldSeconds(HOLD_SECONDS, 1)), seconds)
        compose.withKeypad(on = hold, value = "45") {
            compose.onNodeWithText("Time").assertExists()
        }
        assertEquals(listOf(FloorStepper.nextHoldSeconds(HOLD_SECONDS, 1), 45), seconds)
    }

    @Test
    fun theHoldNumeralIsReadOnlyWhileTheDockClockRuns() {
        showEditor(weightKg = 0.0, reps = 0, loadType = LoadType.BODYWEIGHT, equipment = EquipmentType.BODYWEIGHT, hold = true, holdRunning = true)
        val hold = compose.onNodeWithTag(WorkoutTestTags.HOLD_STEPPER).assertIsNotEnabled()
        assertEquals(listOf("Hold, ${HoldWork.clock(HOLD_REMAINING)} remaining"), hold.spokenDescriptions())
        assertTrue("no TalkBack actions on a running hold", hold.customActionLabels().isEmpty())
        compose.onNodeWithContentDescription("Increase time by ${HoldWork.STEP_SECONDS} seconds").assertIsNotEnabled()
        compose.assertTapOpensNoKeypad { hold.performClick() }
        compose.onNodeWithText("Time").assertDoesNotExist()
        assertTrue(seconds.isEmpty())
    }

    @Test
    fun aLockedEntryOffersNoActionsAndOpensNoKeypad() {
        showEditor(enabled = false)
        val weight = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).assertIsNotEnabled()
        assertTrue(weight.customActionLabels().isEmpty())
        compose.onNodeWithContentDescription("Increase reps by 1").assertIsNotEnabled()
        compose.assertTapOpensNoKeypad { weight.performClick() }
        compose.onNodeWithText(WeightMeaning.LIFTED.fieldLabel).assertDoesNotExist()
    }

    @Test
    fun anAddedWeightLiftNamesItsFieldAndShowsBodyweightAtZero() {
        showEditor(weightKg = 0.0, loadType = LoadType.BODYWEIGHT_PLUS, equipment = EquipmentType.BODYWEIGHT)
        val hero = SetCopy.weightEntryHero(WeightMeaning.ADDED, 0.0, FLOOR_UNIT)
        val step = step(LoadType.BODYWEIGHT_PLUS, EquipmentType.BODYWEIGHT)
        val weight = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER)
        assertEquals(listOf(hero.spoken), weight.spokenDescriptions())
        assertEquals(
            listOf("Decrease added weight by $step lb", "Increase added weight by $step lb", "Type added weight"),
            weight.customActionLabels(),
        )
        // Zero added weight reads as bodyweight, not a lonely "0 lb".
        compose.onNode(hasText(hero.value) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
            .assertIsDisplayed()
        compose.onNode(hasText(FLOOR_UNIT.suffix) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
            .assertDoesNotExist()
        compose.onNodeWithText(checkNotNull(hero.caption)).assertIsDisplayed()
    }

    @Test
    fun anAssistedLiftNamesItsWeightAssistance() {
        showEditor(weightKg = WeightConverter.lbsToKg(30.0), loadType = LoadType.ASSISTED, equipment = EquipmentType.MACHINE)
        val step = step(LoadType.ASSISTED, EquipmentType.MACHINE)
        assertEquals(
            listOf("Decrease assistance by $step lb", "Increase assistance by $step lb", "Type assistance"),
            compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).customActionLabels(),
        )
    }

    @Test
    fun aLoadedLiftAtZeroSaysNoWeightRatherThanZeroPounds() {
        showEditor(weightKg = 0.0)
        assertEquals(
            listOf(SetCopy.weightWellSpoken(WeightMeaning.LIFTED, 0.0, FLOOR_UNIT)),
            compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).spokenDescriptions(),
        )
    }

    @Test
    fun aBarbellShowsItsPlateLoadingUnderTheWeight() {
        val kg = WeightConverter.lbsToKg(135.0)
        showEditor(weightKg = kg, equipment = EquipmentType.BARBELL, plated = true)
        compose.onNodeWithText(checkNotNull(PlateMath.load(kg, FLOOR_UNIT)).caption()).assertIsDisplayed()
    }

    @Test
    fun theWarmupRampOffersPresetsAndMarksTheSuggestedOne() {
        val ramp = listOf(
            WarmupSet(weightKg = WeightConverter.lbsToKg(45.0), percent = 40),
            WarmupSet(weightKg = WeightConverter.lbsToKg(65.0), percent = 60),
            WarmupSet(weightKg = WeightConverter.lbsToKg(85.0), percent = 80),
        )
        var applied: Double? = null
        compose.showFloor(fontScale = 2f) {
            WarmupRampRow(enabled = true, ramp = ramp, emphasisIndex = 1, unit = FLOOR_UNIT, onApplyRamp = { applied = it })
        }
        val root = compose.onNodeWithTag(WorkoutTestTags.WARMUP_RAMP).assertIsDisplayed().getBoundsInRoot()
        // At font 2.0 on 360 dp the presets reflow onto more rows instead of running off the edge.
        val presets = ramp.map { step ->
            compose.onNodeWithText("Use ${step.weightKg.toWeightLabel(FLOOR_UNIT)}").assertIsDisplayed().getBoundsInRoot()
        }
        presets.forEach { preset -> assertTrue("a preset must stay inside the ramp, was $preset in $root", preset.right <= root.right) }
        assertTrue("the three presets wrap onto more than one row, were $presets", presets.map { it.top }.distinct().size > 1)
        compose.onNodeWithText("40%").assertIsDisplayed()
        compose.onNodeWithText("60% · Suggested").assertIsDisplayed()
        compose.onNodeWithText("Use ${ramp[1].weightKg.toWeightLabel(FLOOR_UNIT)}").performClick()
        assertEquals(ramp[1].weightKg, checkNotNull(applied), 0.0001)
    }

    private companion object {
        const val HOLD_SECONDS = 30
        const val HOLD_REMAINING = 12
    }
}
