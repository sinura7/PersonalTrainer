package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.FloorTimerSurface
import com.sinura.personaltrainer.domain.HoldTimerUiState
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.SetStopwatchUiState
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutAdvance
import com.sinura.personaltrainer.domain.WorkoutSetSave

enum class WorkoutPrimaryKind {
    UNAVAILABLE, ADD_EXERCISE, LOG_SET, LOG_WARMUP, SAVE_CHANGES,
    START_HOLD, LOG_HOLD, NEXT_EXERCISE, FINISH, CHECKING, SAVING, UPDATING, RETRY_SAVE, REVIEW_SAVE,
}

/** Identity of the action actually rendered. Clock ticks do not invalidate a press. */
data class WorkoutPrimaryIdentity(
    val kind: WorkoutPrimaryKind,
    val sessionId: String?,
    val exerciseId: String?,
    val editingSetId: String?,
    val draft: ActiveExerciseDraft,
    val sets: List<SetLog>,
    val nextExerciseId: String?,
    val extraSet: Boolean,
    val timedGeneration: Int,
    val activation: Long,
    val pendingSave: WorkoutSetSave?,
)

data class WorkoutPrimaryAction(
    val identity: WorkoutPrimaryIdentity,
    val enabled: Boolean,
    val nextName: String? = null,
    val durationSeconds: Int? = null,
) {
    val kind: WorkoutPrimaryKind get() = identity.kind

    fun label(unit: WeightUnit, loadClass: LoadClass, includeNextName: Boolean = true): String {
        val draft = identity.pendingSave?.values
        val payload = SetCopy.setLine(
            weightKg = draft?.weightKg ?: identity.draft.weightKg,
            reps = draft?.reps ?: identity.draft.reps,
            loadClass = loadClass, unit = unit,
            durationSeconds = draft?.durationSeconds ?: durationSeconds,
            entryPrecision = true,
        )
        return when (kind) {
            WorkoutPrimaryKind.UNAVAILABLE -> "Workout unavailable"
            WorkoutPrimaryKind.ADD_EXERCISE -> "Add exercise"
            WorkoutPrimaryKind.LOG_SET -> "Log set · $payload"
            WorkoutPrimaryKind.LOG_WARMUP -> "Log warm-up · $payload"
            WorkoutPrimaryKind.SAVE_CHANGES -> "Save changes"
            WorkoutPrimaryKind.START_HOLD -> "Start hold"
            WorkoutPrimaryKind.LOG_HOLD -> "Log hold · $payload"
            WorkoutPrimaryKind.NEXT_EXERCISE -> if (includeNextName) "Next exercise · ${nextName.orEmpty()}" else "Next exercise"
            WorkoutPrimaryKind.FINISH -> "Finish workout"
            WorkoutPrimaryKind.CHECKING -> "Checking save…"
            WorkoutPrimaryKind.SAVING -> "Saving…"
            WorkoutPrimaryKind.UPDATING -> "Updating workout…"
            WorkoutPrimaryKind.RETRY_SAVE -> "Retry save"
            WorkoutPrimaryKind.REVIEW_SAVE -> "Review save"
        }
    }
}

/** Presentation derives from the existing session/draft/timer state, including after reopen. */
object WorkoutPrimaryActions {
    fun derive(
        state: ActiveWorkoutUiState,
        extraSet: Boolean,
        hold: HoldTimerUiState,
        stopwatch: SetStopwatchUiState,
        timedGeneration: Int,
        activation: Long,
    ): WorkoutPrimaryAction {
        val session = state.session
        val selected = session?.exercises?.firstOrNull { it.exercise.id == state.selectedExerciseId }
        val isHold = selected?.exercise?.let(HoldWork::isHold) == true
        val holdArmed = hold.running || hold.totalSeconds > 0
        val advance = WorkoutAdvance.forSelection(
            session = session, selectedExerciseId = state.selectedExerciseId,
            wantAnother = extraSet || state.draft.isWarmup,
            editing = state.editingSetId != null,
        )
        val available = state.loadState == SessionLoadState.FOUND && session?.isFinished == false
        val kind = when {
            !available -> WorkoutPrimaryKind.UNAVAILABLE
            state.mutating -> WorkoutPrimaryKind.UPDATING
            state.save.phase == WorkoutSavePhase.CHECKING -> WorkoutPrimaryKind.CHECKING
            state.save.phase == WorkoutSavePhase.SAVING || state.logging -> WorkoutPrimaryKind.SAVING
            state.save.phase == WorkoutSavePhase.FAILED -> WorkoutPrimaryKind.RETRY_SAVE
            state.save.phase == WorkoutSavePhase.CONFLICT -> WorkoutPrimaryKind.REVIEW_SAVE
            session?.exercises?.isEmpty() == true -> WorkoutPrimaryKind.ADD_EXERCISE
            state.editingSetId != null -> WorkoutPrimaryKind.SAVE_CHANGES
            state.draft.isWarmup -> if (isHold && !holdArmed) WorkoutPrimaryKind.START_HOLD else WorkoutPrimaryKind.LOG_WARMUP
            holdArmed && isHold -> WorkoutPrimaryKind.LOG_HOLD
            stopwatch.used -> WorkoutPrimaryKind.LOG_SET
            advance.showNext -> WorkoutPrimaryKind.NEXT_EXERCISE
            advance.showFinish -> WorkoutPrimaryKind.FINISH
            isHold -> WorkoutPrimaryKind.START_HOLD
            else -> WorkoutPrimaryKind.LOG_SET
        }
        val enabled = available && when (kind) {
            WorkoutPrimaryKind.UNAVAILABLE, WorkoutPrimaryKind.CHECKING, WorkoutPrimaryKind.SAVING,
            WorkoutPrimaryKind.UPDATING -> false
            WorkoutPrimaryKind.LOG_SET, WorkoutPrimaryKind.LOG_WARMUP, WorkoutPrimaryKind.SAVE_CHANGES,
            WorkoutPrimaryKind.START_HOLD, WorkoutPrimaryKind.LOG_HOLD -> state.canLog
            else -> true
        }
        return WorkoutPrimaryAction(
            identity = WorkoutPrimaryIdentity(
                kind = kind, sessionId = session?.id, exerciseId = state.selectedExerciseId,
                editingSetId = state.editingSetId, draft = state.draft,
                sets = session?.sets.orEmpty(), nextExerciseId = advance.nextExerciseId,
                extraSet = extraSet, timedGeneration = timedGeneration, activation = activation,
                pendingSave = state.save.command,
            ),
            enabled = enabled, nextName = advance.nextName,
            durationSeconds = FloorTimerSurface.durationToLog(
                hold = isHold, holdElapsedSeconds = hold.elapsedSeconds,
                holdTotalSeconds = hold.totalSeconds, holdRemainingSeconds = hold.remainingSeconds,
                holdDraftSeconds = state.draft.durationSeconds ?: selected?.targetSeconds,
                stopwatch = stopwatch, existingDurationSeconds = state.draft.durationSeconds.takeUnless { isHold },
            ),
        )
    }
}
