package com.sinura.personaltrainer.domain

/** Tables replicated to Supabase when Temper Account is signed in. */
enum class SyncEntityType(val remoteTable: String) {
    ACTIVITY_SESSION("activity_sessions"),
    ACTIVITY_BLOCK("activity_blocks"),
    ACTIVITY_STRENGTH_SET("activity_strength_sets"),
    ACTIVITY_CARDIO_INTERVAL("activity_cardio_intervals"),
    SCHEDULE_RULE("schedule_rules"),
    SCHEDULE_OCCURRENCE("schedule_occurrences"),
    ROUTINE("routines"),
    ROUTINE_EXERCISE("routine_exercises"),
    ACTIVITY_TEMPLATE("activity_templates"),
    CUSTOM_EXERCISE("custom_exercises"),
    EXERCISE_MUSCLE("custom_exercise_muscles"),
    BODYWEIGHT_ENTRY("bodyweight_entries"),
    MEASURABLE_GOAL("measurable_goals"),
    COACH_PREFS("coach_prefs"),
    REMINDER_PREFS("reminder_prefs"),
    DISPLAY_PREFS("display_prefs"),
    ACCOUNT_PROFILE("account_profiles"),
}

enum class SyncOutboxOperation {
    UPSERT,
    DELETE,
}

data class SyncEntityVersion(
    val revision: Long,
    val updatedAtMs: Long,
)
