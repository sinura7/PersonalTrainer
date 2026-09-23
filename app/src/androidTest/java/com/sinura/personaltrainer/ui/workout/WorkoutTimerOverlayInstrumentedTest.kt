package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Saved-state recreation of the shipping dock/overlay, without a live clock. */
@RunWith(AndroidJUnit4::class)
class WorkoutTimerOverlayInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun landscapeCustomDraftSurvivesSavedStateRecreationAndAppliesOnce() {
        val restoration = StateRestorationTester(compose)
        var applied = 0
        var commits = 0
        val action = WorkoutPrimaryAction(
            identity = WorkoutPrimaryIdentity(
                kind = WorkoutPrimaryKind.LOG_SET, sessionId = "overlay-session", exerciseId = "ex-barbell-back-squat",
                editingSetId = null, draft = ActiveExerciseDraft(weightKg = 60.0, reps = 8), sets = emptyList(),
                nextExerciseId = null, extraSet = false, timedGeneration = 0, activation = 0L, pendingSave = null,
            ),
            enabled = true,
        )
        val payload = checkNotNull(action.payload(unit = WeightUnit.KG, loadClass = LoadClass.LOADED))
        restoration.setContent {
            PersonalTrainerTheme(reduceMotion = true) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    WorkoutDock(
                        state = WorkoutDockState(
                            primaryAction = action,
                            verb = action.verb(includeNextName = false),
                            payload = payload,
                            editing = false, logging = false, canLog = true, savePending = false, error = null,
                            suggestionUnavailable = false, showAnother = false, undoMessage = null, undoKey = null, undoDwellMs = 0L,
                            // Landscape idle folds the rest card into "Timer controls ›": the sheet is the only way in.
                            timer = WorkoutDockTimer(show = true, restTotalSeconds = 90, hideIdleRest = true),
                        ),
                        events = WorkoutDockEvents(
                            onPrimary = { true }, onEditFailedSave = {}, onCancelEdit = {}, onDismissError = {}, onAnotherSet = {},
                            onUndo = {}, onUndoDismissed = {}, onSkipRest = {}, onStartRest = {}, onSelectRestDuration = {},
                            onNudgeRest = {},
                            onCustomRest = { input ->
                                val seconds = RestTimer.parseCustom(input)
                                if (seconds != null) { applied = seconds; commits++ }
                                seconds != null
                            },
                            onStartSetClock = {}, onStopSetClock = {}, onDismissRestBatteryHint = {}, onOpenRest = {},
                            onOpenNotifications = {},
                        ),
                    )
                }
            }
        }
        // The one filled act: the verb on the first line, what it will write on the second.
        compose.onNodeWithTag(WorkoutTestTags.LOG_SET).assertIsDisplayed().assertTextContains("Log set").assertTextContains(payload)
        compose.onNodeWithTag(WorkoutTestTags.COMPANION_CLOCK).assertIsDisplayed().performClick()
        compose.onNodeWithText("Custom").performScrollTo().performClick()
        // Typed without focus: focus raises the keyboard, whose late hide after restore moves this dialog under the tap on Set.
        compose.onNode(hasSetTextAction()).performSemanticsAction(SemanticsActions.SetText) { it(AnnotatedString("2:15")) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Custom rest").assertIsDisplayed()
        compose.onNodeWithTag(WorkoutTestTags.REST_DURATION_SHEET).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains("2:15")
        assertEquals(0, commits)
        compose.onNodeWithText("Set").performScrollTo().performClick()
        assertEquals(135, applied)
        assertEquals(1, commits)
        compose.onNodeWithText("Custom rest").assertDoesNotExist()
        compose.onNodeWithTag(WorkoutTestTags.REST_DURATION_SHEET).assertDoesNotExist()
    }
}
