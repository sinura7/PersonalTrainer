package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.draft.wellFormedDraftText
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LoadType

/** Frozen private handoff identity. A retry must inspect this row, not mint another replacement. */
internal data class WorkoutPickerSwap(
    val sessionStartedAt: Long,
    val before: SessionExerciseEntity,
    val after: SessionExerciseEntity,
    val replacementLoadType: LoadType,
    val replacementIsHold: Boolean,
) {
    fun validate() {
        require(sessionStartedAt >= 0L)
        listOf(before.id, before.sessionId, before.exerciseId, after.id, after.exerciseId).forEach {
            require(it.isNotBlank() && it.length <= 256 && wellFormedDraftText(it))
        }
        require(before.id != after.id && before.exerciseId != after.exerciseId)
        require(before.targetWeightKg?.isFinite() != false)
        require(after == pickerSwapReplacement(before, after.id, after.exerciseId, replacementIsHold))
    }
    override fun toString(): String = "WorkoutPickerSwap(redacted)"
}

internal fun pickerSwapReplacement(before: SessionExerciseEntity, resultId: String,
    exerciseId: String, isHold: Boolean): SessionExerciseEntity = before.copy(
    id = resultId, exerciseId = exerciseId, targetWeightKg = null,
    targetReps = if (isHold) HoldWork.HOLD_REPS_PLACEHOLDER else before.targetReps,
    targetSeconds = if (isHold) before.targetSeconds ?: HoldWork.DEFAULT_SECONDS else null,
    targetSecondsMax = if (isHold) before.targetSecondsMax else null,
)
