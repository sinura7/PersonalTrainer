package com.sinura.personaltrainer.ui.summary

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.CooldownCopy
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSummary
import com.sinura.personaltrainer.ui.theme.LocalReducedMotion
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w400dp-h800dp")
class WorkoutSummaryCooldownRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())

    @Test
    fun cooldownStripShowsClockAndSkipWithDonePrimary() {
        deps.restTimerController.startCooldown()
        val snapshotFlow = deps.restTimerController.snapshot
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalWeightUnit provides WeightUnit.KG,
                    LocalReducedMotion provides true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSummaryContent(
                            state = WorkoutSummaryUiState(
                                isLoading = false,
                                savedConfirmed = true,
                                summary = WorkoutSummary(
                                    sessionId = "s1",
                                    title = "Push",
                                    workingSets = 3,
                                ),
                            ),
                            unit = WeightUnit.KG,
                            onRetry = {},
                            onOpenSession = {},
                            onDone = {},
                            onSkipCooldown = { deps.restTimerController.skipIfShown(snapshotFlow.value.timerId) },
                            restSnapshot = snapshotFlow,
                            restRemaining = deps.restTimerController.remainingSeconds,
                        )
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            deps.restTimerStore.current().running &&
                com.sinura.personaltrainer.domain.RestTimerMarkers.isCooldown(deps.restTimerStore.current())
        }
        compose.onNodeWithTag(SummaryTags.COOLDOWN).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_CLOCK).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).assertIsDisplayed()
        compose.onNode(hasTestTag(SummaryTags.DONE)).assertIsDisplayed()
    }

    @Test
    fun cooldownStripShowsOnWarmupsOnlySummary() {
        deps.restTimerController.startCooldown()
        val snapshotFlow = deps.restTimerController.snapshot
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalWeightUnit provides WeightUnit.KG,
                    LocalReducedMotion provides true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSummaryContent(
                            state = WorkoutSummaryUiState(
                                isLoading = false,
                                savedConfirmed = true,
                                summary = WorkoutSummary(
                                    sessionId = "s1",
                                    title = "Push",
                                    workingSets = 0,
                                ),
                            ),
                            unit = WeightUnit.KG,
                            onRetry = {},
                            onOpenSession = {},
                            onDone = {},
                            onSkipCooldown = { deps.restTimerController.skipIfShown(snapshotFlow.value.timerId) },
                            restSnapshot = snapshotFlow,
                            restRemaining = deps.restTimerController.remainingSeconds,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag(SummaryTags.COOLDOWN).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).assertIsDisplayed()
    }

    @Test
    fun cooldownStripShowsWhenSummaryRowIsMissing() {
        deps.restTimerController.startCooldown()
        val snapshotFlow = deps.restTimerController.snapshot
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalWeightUnit provides WeightUnit.KG,
                    LocalReducedMotion provides true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSummaryContent(
                            state = WorkoutSummaryUiState(
                                isLoading = false,
                                missing = true,
                                sessionId = "s1",
                            ),
                            unit = WeightUnit.KG,
                            onRetry = {},
                            onOpenSession = {},
                            onDone = {},
                            onSkipCooldown = { deps.restTimerController.skipIfShown(snapshotFlow.value.timerId) },
                            restSnapshot = snapshotFlow,
                            restRemaining = deps.restTimerController.remainingSeconds,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag(SummaryTags.COOLDOWN).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).assertIsDisplayed()
    }

    @Test
    fun cooldownStripShowsWhenSummaryReadFailedButWorkoutSaved() {
        deps.restTimerController.startCooldown()
        val snapshotFlow = deps.restTimerController.snapshot
        compose.setContent {
            PersonalTrainerTheme {
                CompositionLocalProvider(
                    LocalWeightUnit provides WeightUnit.KG,
                    LocalReducedMotion provides true,
                ) {
                    Box(Modifier.fillMaxSize()) {
                        WorkoutSummaryContent(
                            state = WorkoutSummaryUiState(
                                isLoading = false,
                                failed = true,
                                savedConfirmed = true,
                                sessionId = "s1",
                            ),
                            unit = WeightUnit.KG,
                            onRetry = {},
                            onOpenSession = {},
                            onDone = {},
                            onSkipCooldown = { deps.restTimerController.skipIfShown(snapshotFlow.value.timerId) },
                            restSnapshot = snapshotFlow,
                            restRemaining = deps.restTimerController.remainingSeconds,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag(SummaryTags.COOLDOWN).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).assertIsDisplayed()
    }
}
