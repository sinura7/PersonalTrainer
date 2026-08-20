package com.sinura.personaltrainer.workout

/**
 * Decides which staged-but-unlogged entry to restore when an Active Workout screen is rebuilt.
 *
 * There are two places a draft can come from and they have different lifetimes:
 *  - [WorkoutDraftCache] is process-scoped. It is the freshest source while the app is alive,
 *    and it is empty after the process dies.
 *  - SavedStateHandle is written through on every change and DOES survive process death, which
 *    is exactly the gym case: the phone sits in a pocket through a three-minute rest and the
 *    OS reclaims the app. Before this existed, the lifter came back to a reset weight.
 *
 * Both are written at the same moment, so in-process they agree; preferring the in-memory copy
 * simply avoids depending on the saved-state write having landed.
 *
 * Pure so the recovery rules are unit-testable without Android.
 */
object WorkoutDraftRecovery {
    fun resolve(
        sessionId: String,
        inMemory: WorkoutDraft?,
        persisted: WorkoutDraft?,
    ): WorkoutDraft? {
        if (sessionId.isBlank()) return null
        // A draft belonging to another session must never leak into this one.
        val live = inMemory?.takeIf { it.sessionId == sessionId }
        val saved = persisted?.takeIf { it.sessionId == sessionId }
        return (live ?: saved)?.sanitized()
    }

    /**
     * Guards against a restored record that is out of range — a hand-edited saved state, or a
     * field whose meaning changed between app versions. Reps must stay loggable and weight
     * must stay a real number.
     */
    fun WorkoutDraft.sanitized(): WorkoutDraft = copy(
        weightKg = if (weightKg.isNaN() || weightKg.isInfinite() || weightKg < 0.0) 0.0 else weightKg,
        reps = reps.coerceIn(1, MAX_REPS),
        rpe = rpe?.takeIf { it in 1..10 },
    )

    private const val MAX_REPS = 500
}
