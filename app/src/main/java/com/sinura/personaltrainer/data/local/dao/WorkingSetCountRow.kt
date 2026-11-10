package com.sinura.personaltrainer.data.local.dao

/** v4 V7: how many working sets one lift got in one finished session. */
data class WorkingSetCountRow(
    val exerciseId: String,
    val finishedAt: Long,
    val workingSets: Int,
    val sessionGymId: String? = null,
    val routineId: String? = null,
    val routineName: String? = null,
)
