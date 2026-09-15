package com.sinura.personaltrainer.domain

/**
 * A lift that has stopped moving.
 *
 * Two RPE-9 sessions already turn INCREASE into HOLD. Nothing used to count how
 * many times that had fired, so a lift could sit on the same load indefinitely.
 * This names that pattern. It does not swap the lift, cut the load, or rewrite
 * the plan — the only offer is the lighter week the lifter can already mark.
 * It looks at [StallSignal.STALL_SESSIONS] finished sessions, not the two-session
 * RPE hold window, so raising that window's default is not how stall is fed.
 */
data class StallFinding(
    val exerciseId: String,
    val exerciseName: String,
    val sessionsHeld: Int,
)

object StallSignal {
    const val STALL_SESSIONS = 3

    fun detect(history: List<WorkoutSession>): StallFinding? {
        val finished = history.filter { it.isFinished }
        if (finished.isEmpty()) return null

        val byExercise = linkedMapOf<String, MutableList<SessionShowing>>()
        finished.sortedBy { it.performedAtMs() }.forEach { session ->
            val classes = session.exercises.associate {
                it.exercise.id to LoadClass.of(it.exercise.loadType)
            }
            val names = session.exercises.associate { it.exercise.id to it.exercise.name }
            session.sets
                .filterNot { it.isWarmup }
                .groupBy { it.exerciseId }
                .forEach { (exerciseId, sets) ->
                    val loadClass = classes[exerciseId] ?: LoadClass.LOADED
                    val top = ProgressionBasis.topWorkingSet(
                        sets.map { WorkingSetCandidate(it.weightKg, it.reps, it.completedAt) },
                        loadClass.weightMeaning,
                    ) ?: return@forEach
                    val topSet = sets.firstOrNull {
                        it.weightKg == top.weightKg &&
                            it.reps == top.reps &&
                            it.completedAt == top.completedAt
                    } ?: return@forEach
                    val strength = measure(topSet, loadClass) ?: return@forEach
                    byExercise.getOrPut(exerciseId) { mutableListOf() }.add(
                        SessionShowing(
                            exerciseId = exerciseId,
                            exerciseName = names[exerciseId] ?: topSet.exerciseName,
                            strength = strength,
                        ),
                    )
                }
        }

        return byExercise.values
            .mapNotNull { showings ->
                val last = showings.takeLast(STALL_SESSIONS)
                if (last.size < STALL_SESSIONS) return@mapNotNull null
                val held = last.zipWithNext().all { (previous, next) ->
                    next.strength <= previous.strength
                }
                if (!held) return@mapNotNull null
                StallFinding(
                    exerciseId = last.last().exerciseId,
                    exerciseName = last.last().exerciseName,
                    sessionsHeld = last.size,
                )
            }
            .sortedWith(compareByDescending<StallFinding> { it.sessionsHeld }.thenBy { it.exerciseName })
            .firstOrNull()
    }

    /**
     * e1RM when it is honest; otherwise the lift's own measure (reps, or less
     * assistance). Past [PersonalRecords.MAX_REPS_FOR_ESTIMATE] e1RM is null, so
     * reps-then-weight is the fallback rather than silently ignoring the lift.
     */
    internal fun measure(set: SetLog, loadClass: LoadClass): Strength? = when (loadClass) {
        LoadClass.LOADED, LoadClass.BODYWEIGHT_ADDED -> {
            PersonalRecords.estimatedOneRepMaxKg(set.weightKg, set.reps)?.let { Strength(it) }
                ?: set.reps.takeIf { it > 0 }?.let { Strength(it.toDouble(), set.weightKg) }
        }
        LoadClass.BODYWEIGHT ->
            set.reps.takeIf { it > 0 }?.let { Strength(it.toDouble()) }
        LoadClass.BODYWEIGHT_ASSISTED ->
            set.reps.takeIf { it > 0 }?.let {
                Strength(primary = -set.weightKg.coerceAtLeast(0.0), secondary = it.toDouble())
            }
    }

    internal data class Strength(
        val primary: Double,
        val secondary: Double = 0.0,
    ) : Comparable<Strength> {
        override fun compareTo(other: Strength): Int =
            compareValuesBy(this, other, { it.primary }, { it.secondary })
    }

    private data class SessionShowing(
        val exerciseId: String,
        val exerciseName: String,
        val strength: Strength,
    )
}
