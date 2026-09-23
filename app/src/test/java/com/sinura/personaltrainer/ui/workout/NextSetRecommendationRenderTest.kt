package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRec
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.SetMicroRecCopy
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.coach.CoachEvidenceCopy
import com.sinura.personaltrainer.domain.setMicroRecInputs
import com.sinura.personaltrainer.ui.theme.Metrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The coach's Next-set card, composed on its own: the numbers it proposes, the change said
 * in words, the one-line reason and its evidence, Apply (which fills the entry and then says
 * Applied), and Why, which opens the rule trace with Use suggestion / Keep my numbers.
 *
 * It was held as lines of NextSetRecommendation.kt and domain/SetMicroRec.kt (`private const
 * val NEXT_SET_KICKER = "Next set"`, `contentDescription = "Next set, $numbers"`, `text = if
 * (applied) "Applied" else "Apply"`, `enabled = enabled && !applied`, `title = "Why this
 * set"`, `ProgressionKickerCopy.PLUS_REP to "+1 rep"`). W1b wires the coach goal through to
 * the workout, which reaches exactly the reason line this card reads, so what a lifter sees
 * and can tap is held here instead, and the goal-free reason says so where it stands.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class NextSetRecommendationRenderTest {
    @get:Rule val compose = createComposeRule()

    private var applies = 0
    private var rec by mutableStateOf(recAfter(set(reps = 9, rpe = 8)))
    private var applied by mutableStateOf(false)

    private fun showCard(enabled: Boolean = true, compact: Boolean = false) {
        compose.showFloor {
            NextSetRecommendation(
                rec = rec,
                loadClass = LoadClass.LOADED,
                unit = FLOOR_UNIT,
                applied = applied,
                enabled = enabled,
                onApply = { applies += 1 },
                compact = compact,
            )
        }
    }

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    private fun shown(text: String) = compose.onNode(hasText(text), useUnmergedTree = true).assertIsDisplayed()

    @Test
    fun theCardNamesTheNextSetTheChangeInWordsAndItsReason() {
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET).assertIsDisplayed()
        shown("NEXT SET")
        val numbers = compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertIsDisplayed()
        numbers.assertTextEquals("70 lb × 10")
        assertEquals(listOf("Next set, 70 lb × 10"), numbers.spokenDescriptions())
        // Nine of ten at RPE 8: close, so the call is one more rep at the same weight.
        shown("+1 rep")
        shown("Close — add a rep · Target RPE 8")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsDisplayed().assertIsEnabled()
        // The reason cites its evidence, as a control of its own.
        val line = CoachEvidenceCopy.basedOnLine(CoachEngine.fromMicroRec(rec))
        if (line != null) shown(line)
        compose.onAllNodesWithTag(WorkoutTestTags.COACH_EVIDENCE_CHIP).assertCountEquals(if (line != null) 1 else 0)
    }

    @Test
    fun theChangeIsSaidInWordsForEveryCall() {
        showCard()
        // A clean set at the target holds the weight.
        rec = recAfter(set(reps = 10, rpe = 8))
        shown("Hold the load")
        shown("Clean set — hold the weight · Target RPE 8")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertTextEquals("70 lb × 10")
        // Two hard sets in a row hold the weight too, and say why.
        rec = recAfter(set(reps = 10, rpe = 9), set(reps = 10, rpe = 10))
        assertEquals(SetMicroRecCalculator.RPE_HOLD, rec.reasonCode)
        shown("Hold the load")
        shown("Hard set — hold the weight · Target RPE 10")
        // A missed target backs off.
        rec = recAfter(set(reps = 6, rpe = 9))
        shown("Back off")
        shown("Missed target — go lighter · Target RPE 9")
        // Reps in the tank add the kit's step, said as the step itself.
        rec = recAfter(set(reps = 10, rpe = 7))
        shown("+5")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertTextEquals("75 lb × 10")
        // W1b changes this: once the coach goal is wired through, a Strength goal adds its
        // bias to this reason; today the card reads the goal-free line.
        shown("Had more in you — add weight · Target RPE 7")
        // The first set has nothing to move from, so there is no change line.
        rec = recAfter()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertTextEquals("70 lb × 10")
        listOf("+1 rep", "Hold the load", "Back off", "+5").forEach {
            compose.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Test
    fun applyFillsTheEntryOnceAndThenSaysApplied() {
        showCard()
        val apply = compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY)
            .assert(isButton)
            .assertHeightIsAtLeast(Metrics.touchMin)
        assertEquals(listOf("Apply suggestion, 70 lb × 10"), apply.spokenDescriptions())
        shown("Apply")
        apply.performClick()
        assertEquals(1, applies)
        // The entry now matches: the control stands down and says so, in words.
        applied = true
        apply.assertIsNotEnabled()
        shown("Applied")
        assertEquals(listOf("Suggestion applied, 70 lb × 10"), apply.spokenDescriptions())
        apply.performClick()
        assertEquals("the same suggestion cannot be taken twice", 1, applies)
    }

    @Test
    fun aLockedEntryCannotApplyButCanStillAskWhy() {
        showCard(enabled = false)
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsNotEnabled().performClick()
        assertEquals(0, applies)
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsEnabled().performClick()
        compose.onNodeWithText("Why this set").assertIsDisplayed()
    }

    @Test
    fun whyOpensTheRuleTraceAndCanUseTheSuggestion() {
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onNodeWithText("Why this set").assertIsDisplayed()
        // The engine's own trace, then the evidence it rests on.
        compose.onNodeWithText(SetMicroRecCopy.whyLines(rec).first(), substring = true).assertIsDisplayed()
        compose.onNodeWithText(CoachEvidenceCopy.whySheetAppendix(CoachEngine.fromMicroRec(rec)).last(), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Keep my numbers").assertIsDisplayed()
        compose.onNodeWithText("Use suggestion").performClick()
        assertEquals(1, applies)
        compose.onAllNodesWithText("Why this set").assertCountEquals(0)
        // Keep my numbers closes the sheet and changes nothing.
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onNodeWithText("Keep my numbers").performClick()
        assertEquals(1, applies)
        compose.onAllNodesWithText("Why this set").assertCountEquals(0)
    }

    @Test
    fun onceAppliedWhyOnlyOffersToKeepTheNumbers() {
        applied = true
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onAllNodesWithText("Use suggestion").assertCountEquals(0)
        compose.onNodeWithText("Keep my numbers").performClick()
        assertEquals(0, applies)
    }

    @Test
    fun aPreviewOfTheDraftOffersNoApply() {
        // An effort picked before the set is logged previews what logging it would call.
        rec = recAfter(set(reps = 10, rpe = 8), draftRpe = 9, rpeIntent = false)
        assertTrue(rec.previewOnly)
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET).assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertDoesNotExist()
        shown("If you log this: …")
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).performClick()
        compose.onAllNodesWithText("Use suggestion").assertCountEquals(0)
        compose.onNodeWithText("Keep my numbers").performClick()
        assertEquals(0, applies)
    }

    @Test
    fun aLiftThatIsDoneOrBeingEditedShowsNoCard() {
        rec = recAfter(set(reps = 10, rpe = 8), set(reps = 10, rpe = 8), set(reps = 10, rpe = 8))
        assertEquals(SetMicroRecCalculator.LIFT_DONE, rec.reasonCode)
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET).assertDoesNotExist()
        rec = recAfter(set(reps = 9, rpe = 8)).copy(reasonCode = SetMicroRecCalculator.EDITING)
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET).assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertDoesNotExist()
    }

    @Test
    fun theCompactStripKeepsTheNumbersWhyAndApply() {
        showCard(compact = true)
        compose.onNodeWithTag(WorkoutTestTags.NEXT_SET_COMPACT).assertIsDisplayed()
        compose.onAllNodesWithText("NEXT SET", useUnmergedTree = true).assertCountEquals(0)
        val numbers = compose.onNodeWithTag(WorkoutTestTags.MICRO_REC).assertTextEquals("70 lb × 10")
        assertEquals(listOf("Next set, 70 lb × 10"), numbers.spokenDescriptions())
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_WHY).assertIsDisplayed().assertHeightIsAtLeast(Metrics.touchMin)
        compose.onNodeWithTag(WorkoutTestTags.MICRO_REC_APPLY).assertIsDisplayed().performClick()
        assertEquals(1, applies)
        // The strip is the numbers alone: no change line, reason or evidence.
        compose.onAllNodesWithText("+1 rep", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(WorkoutTestTags.COACH_EVIDENCE_CHIP).assertCountEquals(0)
    }

    @Test
    fun theEvidenceLineOpensTheEvidence() {
        rec = recAfter(set(reps = 10, rpe = 8))
        val suggestion = CoachEngine.fromMicroRec(rec)
        val details = CoachEvidenceCopy.detailLines(suggestion)
        showCard()
        compose.onNodeWithTag(WorkoutTestTags.COACH_EVIDENCE_CHIP).performClick()
        compose.onNodeWithText("Evidence").assertIsDisplayed()
        if (details.isNotEmpty()) compose.onNodeWithText(details.first(), substring = true).assertIsDisplayed()
        compose.onNodeWithText("Close").performClick()
        compose.onAllNodesWithText("Evidence").assertCountEquals(0)
    }

    private fun set(reps: Int, rpe: Int?): LoggedSetView = LoggedSetView(weightKg = FLOOR_KG70, reps = reps, rpe = rpe, isWarmup = false)

    /** The coach's call after [working] sets of a 3 × 10 at 70 lb on a machine. */
    private fun recAfter(vararg working: LoggedSetView, draftRpe: Int? = null, rpeIntent: Boolean = true): SetMicroRec = checkNotNull(
        SetMicroRecCalculator.suggest(
            setMicroRecInputs(
                editing = false,
                loadType = LoadType.EXTERNAL,
                unit = FLOOR_UNIT,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = FLOOR_KG70,
                working = working.toList(),
                lastAnySetWasWarmup = false,
                hint = null,
                lighterWeek = false,
                draftWeightKg = FLOOR_KG70,
                draftReps = 10,
                draftRpe = draftRpe,
                nowMs = 0L,
                todayEpochDay = 0L,
                rpeIntent = rpeIntent,
                equipment = EquipmentType.MACHINE,
            ),
        ),
    )
}
