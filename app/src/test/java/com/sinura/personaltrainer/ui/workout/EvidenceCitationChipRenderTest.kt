package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.WeightUnit
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
 * The evidence line under a coach suggestion ("Based on Helms et al. 2016"), composed for
 * real. It opens the evidence sheet, so it is a button a thumb can hit and TalkBack can
 * name: a full 48 dp target (it was a two-line caption, about 32 dp), the Button role, one
 * spoken sentence, and "double-tap to open the evidence".
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class EvidenceCitationChipRenderTest {
    @get:Rule val compose = createComposeRule()

    private val suggestion = checkNotNull(
        CoachEngine.suggest(
            setMicroRecInputs(
                editing = false,
                loadType = LoadType.EXTERNAL,
                unit = WeightUnit.KG,
                targetSets = 3,
                targetReps = 10,
                targetWeightKg = 60.0,
                working = listOf(LoggedSetView(60.0, 12, 7, false)),
                lastAnySetWasWarmup = false,
                hint = null,
                lighterWeek = false,
                draftWeightKg = 0.0,
                draftReps = 0,
                draftRpe = null,
                nowMs = 0L,
                todayEpochDay = 0L,
                rpeIntent = true,
            ),
        ),
    )

    @Test
    fun theEvidenceLineIsA48DpButtonReadOnceThatOpensTheEvidence() {
        var opened = 0
        compose.showFloor { EvidenceCitationChip(suggestion = suggestion, onShowDetail = { opened += 1 }) }
        val primary = checkNotNull(suggestion.primaryEvidence())
        val line = checkNotNull(CoachEvidenceCopy.basedOnLine(suggestion))
        val chip = compose.onNodeWithTag(WorkoutTestTags.COACH_EVIDENCE_CHIP)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(Metrics.touchMin)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        assertEquals(CoachEvidenceCopy.OPEN_EVIDENCE, chip.clickLabel())
        // One sentence, with the claim a sighted lifter only gets by opening the sheet; the
        // visible line is not read after it.
        assertEquals(
            listOf("Evidence, ${CoachEvidenceCopy.chipLabel(primary)}, ${primary.claim}"),
            chip.spokenDescriptions(),
        )
        assertTrue("was ${chip.mergedTexts()}", chip.mergedTexts().isEmpty())
        compose.onNodeWithText(line, useUnmergedTree = true).assertIsDisplayed()
        chip.performClick()
        assertEquals(1, opened)
    }
}
