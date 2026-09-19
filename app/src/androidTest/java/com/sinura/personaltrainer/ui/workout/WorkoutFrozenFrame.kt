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
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseFloorStatsCalculator
import com.sinura.personaltrainer.domain.FloorWeightPresets
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.LiftEntryReadiness
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LogReceipt
import com.sinura.personaltrainer.domain.SessionExercise
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetOrdinalCopy
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WarmupRamp
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutProgressCalculator
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.domain.WorkoutSetSave
import com.sinura.personaltrainer.domain.WorkoutSetValues
import com.sinura.personaltrainer.ui.components.HairlineDivider
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.units.LocalWeightUnit

/**
 * Frozen integration of shipping components for transient-state pixel references.
 * No repository, mutable clock, rest job, receipt expiry, or destructive cleanup.
 * Actual route wiring is covered by WorkoutEntry/CompletionLayout and journeys.
 *
 * The frame composes the same pieces the route does, in the route's order: the
 * header with its progress line, the exercise identity, the stats row, the hero
 * numerals (plus the warm-up ramp on a warm-up), the RPE track, the set history,
 * and the dock with its companion slot and the one filled commit.
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
    val session = WorkoutSession("frozen-session", null, "Lower A", 1_700_000_000_000, "", 0,
        1_700_000_000_000, null, listOf(lift, nextLift), sets)
    val state = ActiveWorkoutUiState(
        loadState = SessionLoadState.FOUND,
        session = session,
        selectedExerciseId = exercise.id, draft = draft, error = error,
        save = if (error != null) WorkoutSaveState(WorkoutSavePhase.FAILED, command, error) else WorkoutSaveState(),
        liftReadiness = LiftEntryReadiness.READY,
    )
    val holdState = if (hold) HoldTimerUiState(running = true, remainingSeconds = 18, totalSeconds = 30, elapsedSeconds = 12) else HoldTimerUiState()
    val action = WorkoutPrimaryActions.derive(state, false, holdState, SetStopwatchUiState(), 0, 0)
    val receipt = if (scenario == "success") LogReceipt(row.id, "Set 1 of 3 logged · 60 kg × 8 · RPE 8", 60.0, 8, 8, false) else null
    val unit = WeightUnit.KG
    val loadClass = LoadClass.of(exercise.loadType)
    val entryEnabled = !state.entryLocked
    val workingLogged = sets.count { !it.isWarmup }
    val plannedComplete = action.kind == WorkoutPrimaryKind.NEXT_EXERCISE || action.kind == WorkoutPrimaryKind.FINISH
    // Same wording rule as the route: the identity says which set is next, or that the plan is met.
    val setContext = if (plannedComplete) {
        "Planned sets complete"
    } else {
        SetOrdinalCopy.draftLine(
            isWarmup = draft.isWarmup,
            warmupLogged = sets.count { it.isWarmup },
            workingLogged = workingLogged,
            targetSets = lift.targetSets,
        )
    }
    val progress = WorkoutProgressCalculator.of(session = session, selectedExerciseId = exercise.id)
    val stats = ExerciseFloorStatsCalculator.of(
        session = session,
        exerciseId = exercise.id,
        lastPerformance = null,
        priorHistory = emptyList(),
        unit = unit,
    )
    val sourceLabel = FloorWeightPresets.source(
        currentKg = draft.weightKg,
        plannedKg = lift.targetWeightKg,
        lastKg = null,
        suggestedKg = null,
    )?.label
    val ramp = if (draft.isWarmup && workingLogged == 0) {
        WarmupRamp.sets(
            workingWeightKg = WarmupRamp.workingWeightKg(
                draftKg = draft.weightKg,
                draftIsWarmup = true,
                workingLogged = workingLogged,
                targetKg = lift.targetWeightKg,
                suggestedKg = null,
                lastKg = null,
                loadType = exercise.loadType,
                equipment = exercise.equipment,
                movementKey = exercise.movementKey,
            ),
            loadType = exercise.loadType,
            unit = unit,
            equipment = exercise.equipment,
        )
    } else {
        emptyList()
    }
    val current = if (plannedComplete) {
        null
    } else {
        CurrentSetMark(
            mark = SetOrdinalCopy.draftMark(isWarmup = draft.isWarmup, workingLogged = workingLogged),
            label = setContext,
        )
    }
    CompositionLocalProvider(LocalWeightUnit provides unit) {
        Scaffold(bottomBar = { Column {} }) { systemPadding ->
            Box(Modifier.padding(systemPadding).consumeWindowInsets(systemPadding)) {
                Scaffold(
                    topBar = {
                        WorkoutHeader(
                            routineName = "Lower A",
                            progress = progress,
                            canFinish = state.canFinish,
                            showDiscard = state.showDiscard,
                            compact = false,
                            onExit = {},
                            onFinish = {},
                            onDiscard = {},
                            overflow = {
                                LiftOverflowMenu(
                                    liftId = lift.id, canEdit = sets.isEmpty(), onSwap = {}, onRemove = {}, onNotes = {},
                                    onSummary = {}, onSkip = {}, onSwitch = {}, enabled = entryEnabled,
                                )
                            },
                        )
                    },
                    bottomBar = {
                        WorkoutDock(
                            state = WorkoutDockState(
                                primaryAction = action,
                                verb = action.verb(includeNextName = true),
                                payload = action.payload(unit = unit, loadClass = loadClass),
                                editing = false, logging = false, canLog = state.canLog, savePending = state.save.pending,
                                error = error, suggestionUnavailable = false, showAnother = complete,
                                undoMessage = null, undoKey = null, undoDwellMs = 0L,
                                timer = WorkoutDockTimer(
                                    show = true, restRemainingSeconds = 46, restTotalSeconds = 90, restRunning = scenario == "rest",
                                    holdRunning = hold, holdElapsedSeconds = if (hold) 12 else 0, holdRemainingSeconds = if (hold) 18 else 0,
                                    holdTotalSeconds = if (hold) 30 else 0, offerSetClock = !hold,
                                ),
                            ),
                            events = WorkoutDockEvents(
                                onPrimary = { true }, onEditFailedSave = {}, onCancelEdit = {}, onDismissError = {}, onAnotherSet = {},
                                onUndo = {}, onUndoDismissed = {}, onSkipRest = {}, onStartRest = {}, onSelectRestDuration = {},
                                onNudgeRest = {}, onCustomRest = { true }, onStartSetClock = {}, onStopSetClock = {},
                                onDismissRestBatteryHint = {}, onOpenRest = {}, onOpenNotifications = {},
                            ),
                        )
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).testTag(WorkoutTestTags.CONTENT),
                        contentPadding = PaddingValues(start = Metrics.gutter, end = Metrics.gutter, top = Metrics.space3, bottom = Metrics.space7),
                        verticalArrangement = Arrangement.spacedBy(Metrics.space4),
                    ) {
                        item(key = "exercise-header") {
                            ExerciseHeader(
                                lift = lift, number = 1, total = 2, workingLogged = workingLogged, setContext = setContext,
                                draftWarmup = draft.isWarmup, onWarmup = {}, onOpenSwitcher = {}, onDetails = {}, enabled = entryEnabled,
                            )
                        }
                        item(key = "stats") {
                            Column {
                                HairlineDivider(startIndent = 0.dp)
                                ExerciseStatsRow(stats = stats, unit = unit)
                                HairlineDivider(startIndent = 0.dp)
                            }
                        }
                        item(key = "entry") {
                            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space3)) {
                                WeightRepsEditor(
                                    enabled = entryEnabled, weightKg = draft.weightKg, reps = draft.reps, unit = unit,
                                    loadClass = loadClass, loadType = exercise.loadType, equipment = exercise.equipment,
                                    movementKey = exercise.movementKey, plated = exercise.equipment == EquipmentType.BARBELL,
                                    hold = hold, holdSeconds = draft.durationSeconds ?: lift.targetSeconds, holdRunning = hold,
                                    holdRemainingSeconds = if (hold) 18 else 0, sourceLabel = sourceLabel,
                                    plannedKg = lift.targetWeightKg, lastKg = null,
                                    onWeightKgChange = {}, onRepsChange = {}, onSecondsChange = {},
                                )
                                if (draft.isWarmup) {
                                    WarmupRampRow(
                                        enabled = entryEnabled, ramp = ramp,
                                        emphasisIndex = WarmupRamp.nextUnusedIndex(ramp = ramp, loggedWarmupKg = emptyList()),
                                        unit = unit, onApplyRamp = {},
                                    )
                                }
                            }
                        }
                        item(key = "rpe") {
                            RpeSelector(enabled = entryEnabled, warmup = draft.isWarmup, rpe = null, recommendedRpe = null, onRpe = {})
                        }
                        item(key = "set-history") {
                            Column(verticalArrangement = Arrangement.spacedBy(Metrics.space4)) {
                                HairlineDivider(startIndent = 0.dp)
                                SetHistoryStrip(
                                    sets = sets, targetSets = lift.targetSets, loadClass = loadClass, unit = unit,
                                    editingSetId = null, receiptSetId = receipt?.setId, current = current, showAddSet = complete,
                                    enabled = entryEnabled, onEdit = {}, onDelete = {}, onOpenAll = {}, onAddSet = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
