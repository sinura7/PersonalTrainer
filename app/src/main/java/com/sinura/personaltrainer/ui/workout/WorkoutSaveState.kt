package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.WorkoutSetSave

enum class WorkoutSavePhase { IDLE, CHECKING, SAVING, FAILED, CONFLICT }

/** A failed operation owns its retry. Editable values are never used to rebuild it. */
data class WorkoutSaveState(
    val phase: WorkoutSavePhase = WorkoutSavePhase.IDLE,
    val command: WorkoutSetSave? = null,
    val message: String? = null,
) {
    val pending: Boolean get() = command != null
    val busy: Boolean get() = phase == WorkoutSavePhase.CHECKING || phase == WorkoutSavePhase.SAVING
}
