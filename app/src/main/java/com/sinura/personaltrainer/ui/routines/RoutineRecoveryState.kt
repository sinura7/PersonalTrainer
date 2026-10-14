package com.sinura.personaltrainer.ui.routines

import com.sinura.personaltrainer.data.draft.RoutineDraftFields

internal data class RoutineRecoveryState(
    val ready: Boolean = false,
    val resumed: Boolean = false,
    val problem: String? = null,
    val fields: RoutineDraftFields? = null,
)
