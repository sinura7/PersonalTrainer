package com.sinura.personaltrainer.workout

import com.sinura.personaltrainer.domain.HoldWork

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
 * Packet C restores a map of drafts, one per lift. Both stores are written at the same
 * moment, so in-process they agree; preferring the in-memory copy simply avoids depending on
 * the saved-state write having landed.
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

    fun resolveMap(
        sessionId: String,
        inMemory: Map<String, WorkoutDraft>,
        persisted: Map<String, WorkoutDraft>,
    ): Map<String, WorkoutDraft> {
        if (sessionId.isBlank()) return emptyMap()
        val ids = inMemory.keys + persisted.keys
        return ids.mapNotNull { id ->
            resolve(sessionId, inMemory[id], persisted[id])?.let { id to it }
        }.toMap()
    }

    fun resolveSelectedId(
        sessionId: String,
        inMemorySelected: String?,
        persistedSelected: String?,
        recovered: Map<String, WorkoutDraft>,
    ): String? {
        if (sessionId.isBlank()) return null
        inMemorySelected?.takeIf { it in recovered }?.let { return it }
        persistedSelected?.takeIf { it in recovered }?.let { return it }
        return inMemorySelected ?: persistedSelected ?: recovered.keys.firstOrNull()
    }

    /**
     * Guards against a restored record that is out of range — a hand-edited saved state, or a
     * field whose meaning changed between app versions. Reps must stay loggable and weight
     * must stay a real number. Hold drafts may keep 0 reps.
     */
    fun WorkoutDraft.sanitized(): WorkoutDraft {
        val hold = durationSeconds != null
        return copy(
            weightKg = if (weightKg.isNaN() || weightKg.isInfinite() || weightKg < 0.0) 0.0 else weightKg,
            reps = if (hold) reps.coerceIn(0, MAX_REPS) else reps.coerceIn(1, MAX_REPS),
            rpe = rpe?.takeIf { it in 1..10 },
            durationSeconds = durationSeconds?.coerceIn(HoldWork.MIN_SECONDS, HoldWork.MAX_SECONDS),
        )
    }

    private const val MAX_REPS = 500
}
