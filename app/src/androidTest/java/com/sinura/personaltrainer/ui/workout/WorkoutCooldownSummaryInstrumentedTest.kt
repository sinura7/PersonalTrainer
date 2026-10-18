package com.sinura.personaltrainer.ui.workout

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.CooldownCopy
import com.sinura.personaltrainer.domain.RestTimerMarkers
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryScreen
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryViewModel
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutCooldownSummaryInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<androidx.activity.ComponentActivity>()
    private val fixture = WorkoutEntryFixture()
    private val app = ApplicationProvider.getApplicationContext<PersonalTrainerApp>()

    @After fun cleanup() = fixture.close()

    @Test
    fun summaryScreenSkipAndBackUseDoneNotHistory() {
        fixture.seed(targetSets = 1)
        runBlockingFinishSession()
        fixture.container.restTimerController.startCooldown()
        var done = false
        val summaryVm = WorkoutSummaryViewModel(
            application = app,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to fixture.sessionId)),
            container = fixture.container,
        )
        compose.setContent {
            WorkoutSummaryScreen(
                onDone = { done = true },
                onOpenSession = { error("Summary Back must not reopen the workout") },
                viewModel = summaryVm,
            )
        }
        compose.waitUntil(15_000) {
            fixture.container.restTimerStore.current().running &&
                RestTimerMarkers.isCooldown(fixture.container.restTimerStore.current())
        }
        compose.onNodeWithText(CooldownCopy.KICKER).assertIsDisplayed()
        compose.onNodeWithTag(SummaryTags.COOLDOWN_SKIP).performClick()
        compose.waitUntil(5_000) { !fixture.container.restTimerStore.current().running }
        assertFalse(fixture.container.restTimerStore.current().running)
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { done }
        assertTrue(done)
    }

    private fun runBlockingFinishSession() {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            val session = checkNotNull(fixture.container.workoutRepository.getSession(fixture.sessionId))
            if (!session.isFinished) {
                fixture.container.workoutRepository.finishSession(fixture.sessionId, notes = "")
            }
        }
    }
}
