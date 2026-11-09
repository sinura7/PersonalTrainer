package com.sinura.personaltrainer.domain

data class Gym(
    val id: String,
    val name: String,
    val shortName: String,
    val badge: String,
    val sortOrder: Int,
    val archived: Boolean,
)

/** A filter value for Body and exercise detail. */
sealed interface GymFilter {
    data object All : GymFilter
    data class One(val gymId: String) : GymFilter
    data object Unassigned : GymFilter
}

data class GymVisit(
    val sessions: Int,
    val lastAtMs: Long?,
)
