package com.sinura.personaltrainer.domain

/** Tables replicated to Supabase when Temper Account is signed in. */
enum class SyncEntityType(val remoteTable: String) {
    ACTIVITY_SESSION("activity_sessions"),
    ACTIVITY_BLOCK("activity_blocks"),
    ACTIVITY_STRENGTH_SET("activity_strength_sets"),
    ACTIVITY_CARDIO_INTERVAL("activity_cardio_intervals"),
    SCHEDULE_RULE("schedule_rules"),
    SCHEDULE_OCCURRENCE("schedule_occurrences"),
}

enum class SyncOutboxOperation {
    UPSERT,
    DELETE,
}

data class SyncEntityVersion(
    val revision: Long,
    val updatedAtMs: Long,
)
