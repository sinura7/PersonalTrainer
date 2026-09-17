package com.sinura.personaltrainer.ui.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogReceipt
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * Frozen integration of shipping components for transient-state pixel references.
 * No repository, mutable clock, rest job, receipt expiry, or destructive cleanup.
 * Actual route wiring is covered by WorkoutEntry/CompletionLayout and journeys.
 */
@Composable
internal fun WorkoutFrozenFrame(scenario: String) {
    val hold = scenario == "hold"
    val complete = scenario == "completion"
    fun exerciseById(id: String): Exercise {
        val seed = DefaultExercises.catalog().first { it.id == id }
        return Exercise(id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "", isCustom = false,
            equipment = seed.equipment, loadType = seed.loadType, imageKey = seed.imageKey)
    }
    val exercise = exerciseById(if (hold) "ex-plank" else "ex-barbell-back-squat")
    val next = exerciseById("ex-romanian-deadlift")
    val lift = SessionExercise(id = "lift-a", sessionId = "frozen-session", exercise = exercise, sortOrder = 0,
        targetSets = if (complete) 1 else 3, targetReps = 8, targetWeightKg = 60.0, restSeconds = 90, targetSeconds = if (hold) 30 else null)
    val nextLift = lift.copy(id = "lift-b", exercise = next, sortOrder = 1, targetSets = 3)
    val row = SetLog("saved-a", "frozen-session", exercise.id, exercise.name, 1, 60.0, 8, 8, false, 1_700_000_000_000)
    val sets = if (scenario in setOf("rest", "success", "completion")) listOf(row) else emptyList()
    val draft = ActiveExerciseDraft(weightKg = if (scenario == "warmup") 25.0 else if (hold) 0.0 else 60.0,
        reps = if (hold) 0 else 8, isWarmup = scenario == "warmup", durationSeconds = if (hold) 30 else null)
    val command = WorkoutSetSave("frozen-session", exercise.id, "pending-a", 1_700_000_001_000,
        WorkoutSetValues(draft.weightKg, draft.reps, null, draft.isWarmup, draft.durationSeconds))
    val error = if (scenario == "error") "Could not save this set. Your values are kept. Retry save, or review the operation before editing." else null
    val state = ActiveWorkoutUiState(
        loadState = SessionLoadState.FOUND,
        session = WorkoutSession("frozen-session", null, "Lower A", 1_700_000_000_000, "", 0,
            1_700_000_000_000, null, listOf(lift, nextLift), sets),
        selectedExerciseId = exercise.id, draft = draft, error = error,
        save = if (error != null) WorkoutSaveState(WorkoutSavePhase.FAILED, command, error) else WorkoutSaveState(),
        liftReadiness = LiftEntryReadiness.READY,
    )
    val holdState = if (hold) HoldTimerUiState(running = true, remainingSeconds = 18, totalSeconds = 30, elapsedSeconds = 12) else HoldTimerUiState()
    val action = WorkoutPrimaryActions.derive(state, false, holdState, SetStopwatchUiState(), 0, 0)
    val receipt = if (scenario == "success") LogReceipt(row.id, "Set 1 of 3 logged · 60 kg × 8 · RPE 8", 60.0, 8, 8, false) else null
    CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
        Scaffold(bottomBar = { Column {} }) { systemPadding ->
            Box(Modifier.padding(systemPadding).consumeWindowInsets(systemPadding)) {
                Scaffold(
                    topBar = { WorkoutHeader(routineName = "Lower A", canFinish = sets.isNotEmpty(), showDiscard = sets.isEmpty(), compact = false,
                        onExit = {}, onFinish = {}, onDiscard = {}) },
                    bottomBar = {
                        LogBar(
                            editing = false, logging = false, error = error, draftLabel = "", warmup = draft.isWarmup,
                            showNext = complete, showAnother = complete, onAnotherSet = {}, onLog = {}, onNext = {}, onCancelEdit = {},
                            primaryAction = action, primaryLabel = action.label(WeightUnit.KG, LoadClass.of(exercise.loadType)),
                            onPrimary = { true }, savePending = state.save.pending,
                            showTimer = true, restTotalSeconds = 90, restRemainingSeconds = 46, restRunning = scenario == "rest",
                            hold = hold, holdRunning = hold, holdElapsedSeconds = 12, holdRemainingSeconds = 18, holdTotalSeconds = 30,
                            offerSetClock = !hold,
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).testTag(WorkoutTestTags.CONTENT),
                        contentPadding = PaddingValues(start = Metrics.gutter, end = Metrics.gutter, top = Metrics.space3, bottom = Metrics.space7),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space2),
                    ) {
                        item(key = "current-lift") {
                            CurrentLiftCard(lift = lift, number = 1, total = 2, workingLogged = sets.size,
                                enabled = !state.entryLocked, canEdit = sets.isEmpty(), onOpenSwitcher = {},
                                onSwap = {}, onSkip = {}, onRemove = {}, onNotes = {}, onSummary = {})
                        }
                        item(key = "current-entry") {
                            WorkoutLiftCard(
                                card = WorkoutLiftCardState(
                                    lift = lift, number = 1, selected = true, loggedSets = sets, latestSetId = sets.lastOrNull()?.id,
                                    editingSetId = null, lastPerformance = null, hint = null,
                                    draftWeightKg = draft.weightKg, draftReps = draft.reps, draftWarmup = draft.isWarmup,
                                    draftRpe = null, microRec = null, unit = WeightUnit.KG, canEdit = sets.isEmpty(), showAddSet = complete,
                                    hold = hold, holdSeconds = if (hold) 30 else null, holdRunning = hold, holdRemainingSeconds = 18,
                                    receipt = receipt, entryEnabled = !state.entryLocked, plannedComplete = complete,
                                ),
                                events = WorkoutLiftCardEvents(onWeightKgChange = {}, onRepsAdjust = {}, onRepsChange = {},
                                    onApplyLastTime = { _, _ -> }, onWarmup = {}, onRpe = {}, onApplySuggested = {},
                                    onEditSet = {}, onDeleteSet = {}, onAddSet = {}),
                            )
                        }
                    }
                }
            }
        }
    }
}
