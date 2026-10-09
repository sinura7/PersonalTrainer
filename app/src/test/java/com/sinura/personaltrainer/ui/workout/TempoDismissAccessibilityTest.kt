package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.LoggedSetView
import com.sinura.personaltrainer.domain.SetMicroRecCalculator
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.coach.CoachEngine
import com.sinura.personaltrainer.domain.coach.TempoCoachTip
import com.sinura.personaltrainer.domain.setMicroRecInputs
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class TempoDismissAccessibilityTest {
    @get:Rule val compose = createComposeRule()
    private val rec = checkNotNull(SetMicroRecCalculator.suggest(setMicroRecInputs(
        editing = false, loadType = LoadType.EXTERNAL, unit = WeightUnit.LBS,
        targetSets = 3, targetReps = 10, targetWeightKg = 31.75,
        working = listOf(LoggedSetView(31.75, 10, 8, false)), lastAnySetWasWarmup = false,
        hint = null, lighterWeek = false, draftWeightKg = 31.75, draftReps = 10,
        draftRpe = null, nowMs = 0L, todayEpochDay = 0L, rpeIntent = false,
    )))
    private val tip = TempoCoachTip.NextSet(rec, CoachEngine.fromMicroRec(rec))

    @Test fun theSpokenDismissActionDismissesOnlyTheSuggestion() {
        var dismissed = 0
        var applied = 0
        compose.showFloor {
            TempoCoachCard(tip = tip, loadClass = LoadClass.LOADED, unit = WeightUnit.LBS, applied = false,
                enabled = true, onApply = { applied++ }, onDismiss = { dismissed++ })
        }
        compose.onNodeWithContentDescription("Dismiss Tempo suggestion")
            .assertHasClickAction().assertIsEnabled().performClick()
        assertEquals(1, dismissed)
        assertEquals(0, applied)
    }

    @Test fun aPendingWriteRetainsTheNamedDisabledDismissAction() {
        var dismissed = 0
        compose.showFloor {
            TempoCoachCard(tip = tip, loadClass = LoadClass.LOADED, unit = WeightUnit.LBS, applied = false,
                enabled = false, onApply = {}, onDismiss = { dismissed++ })
        }
        compose.onNodeWithContentDescription("Dismiss Tempo suggestion")
            .assertHasClickAction().assertIsNotEnabled()
        assertEquals(0, dismissed)
    }
}
