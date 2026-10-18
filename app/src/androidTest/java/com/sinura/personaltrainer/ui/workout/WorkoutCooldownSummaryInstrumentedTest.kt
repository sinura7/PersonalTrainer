package com.sinura.personaltrainer.ui.workout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.CooldownCopy
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryContent
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryUiState
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutCooldownSummaryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val fixture = WorkoutEntryFixture()

    @After fun cleanup() = fixture.close()

    @Test
    fun skipEndsCooldownAndBackDoesNotReopenWorkout() {
        fixture.seed(targetSets = 1)
        fixture.container.restTimerController.startCooldown()
        var done = false
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG, LocalReducedMotion provides true) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSummaryContent(
                            state = WorkoutSummaryUiState(
                                isLoading = false,
                                sessionId = fixture.sessionId,
                                savedConfirmed = true,
                                summary = WorkoutSummary(
                                    sessionId = fixture.sessionId,
                                    title = "Test",
                                    workingSets = 1,
                                ),
                            ),
                            unit = WeightUnit.KG,
                            onRetry = {},
                            onOpenSession = { error("Summary Back must not reopen the workout") },
                            onDone = { done = true },
                            onSkipCooldown = { id -> fixture.container.restTimerController.skipIfShown(id) },
                            restSnapshot = fixture.container.restTimerController.snapshot,
                            restRemaining = fixture.container.restTimerController.remainingSeconds,
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(CooldownCopy.KICKER).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).performClick()
        compose.waitUntil(5_000) { !fixture.container.restTimerStore.current().running }
        assertFalse(fixture.container.restTimerStore.current().running)
        compose.activity.onBackPressedDispatcher.onBackPressed()
        assertTrue(done)
    }
}
