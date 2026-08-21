package com.sinura.personaltrainer.data.local.entity

/**
 * One row of "when did I last do this lift".
 *
 * A projection, not an entity: it maps no table and Room never writes it. It exists because a
 * `GROUP BY` result has a shape no entity has, and giving that shape a name is cheaper than
 * loading every set log to compute it in Kotlin.
 */
data class ExerciseRecencyRow(
    val exerciseId: String,
    val lastLoggedAt: Long,
)
