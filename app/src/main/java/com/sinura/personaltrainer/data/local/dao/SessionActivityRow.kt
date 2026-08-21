package com.sinura.personaltrainer.data.local.dao

/**
 * Set counters for one session, aggregated in SQL.
 *
 * The live-session bar needs a set count on every screen; loading the session's whole set
 * graph to count rows would be a far heavier read for two integers and a timestamp.
 */
data class SessionActivityRow(
    val totalSets: Int,
    val workingSets: Int,
    val lastCompletedAt: Long?,
)
