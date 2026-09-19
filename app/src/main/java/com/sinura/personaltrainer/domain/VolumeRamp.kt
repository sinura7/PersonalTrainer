package com.sinura.personaltrainer.domain

/**
 * Last week's easy work, as a card, never a rewrite of the plan.
 *
 * [LighterWeek] is not a set-count scaler and [TrainingBlock] holds no load
 * opinion. A ramp that mutated the routine would be the second voice both
 * were written to prevent. This only names a muscle that ran productive
 * easy sets last week and could take two more.
 */
data class VolumeRampFinding(
    val muscle: CanonicalMuscle,
    val lastWeekSets: Int,
    val suggestedSets: Int,
)

object VolumeRamp {
    const val LOOKBACK_DAYS = 7L
    const val RPE_CEILING = 8
    const val ADD_SETS = 2
    const val MIN_SETS = HeatBand.LOW_MIN_SETS.toInt()

    fun detect(
        history: List<WorkoutSession>,
        nowMs: Long,
        time: TimePort,
        zoneId: String,
        exerciseCatalog: Map<String, Exercise> = emptyMap(),
    ): VolumeRampFinding? {
        val cutoff = time.minusCivilDays(nowMs, zoneId, LOOKBACK_DAYS)
        val tallies = linkedMapOf<CanonicalMuscle, MuscleWeek>()
        history.filter { it.isFinished }.forEach { session ->
            session.sets.filterNot { it.isWarmup }.forEach { set ->
                val trainedAt = MuscleLoadCalculator.trainedAtMs(session, set)
                if (trainedAt < cutoff || trainedAt > nowMs) return@forEach
                val muscle = primaryMuscle(set, session, exerciseCatalog) ?: return@forEach
                if (muscle == CanonicalMuscle.OTHER) return@forEach
                val row = tallies.getOrPut(muscle) { MuscleWeek() }
                row.count += 1
                val rpe = set.rpe
                if (rpe == null || rpe > RPE_CEILING) row.allEasy = false
            }
        }
        val cap = HeatBand.HIGH_MIN_SETS.toInt()
        return tallies.entries
            .mapNotNull { (muscle, week) ->
                if (!week.allEasy) return@mapNotNull null
                if (week.count < MIN_SETS) return@mapNotNull null
                val suggested = week.count + ADD_SETS
                if (suggested > cap) return@mapNotNull null
                VolumeRampFinding(
                    muscle = muscle,
                    lastWeekSets = week.count,
                    suggestedSets = suggested,
                )
            }
            .sortedWith(
                compareByDescending<VolumeRampFinding> { it.lastWeekSets }
                    .thenBy { it.muscle.displayName },
            )
            .firstOrNull()
    }

    private fun primaryMuscle(
        set: SetLog,
        session: WorkoutSession,
        catalog: Map<String, Exercise>,
    ): CanonicalMuscle? {
        val exercise = session.exercises.firstOrNull { it.exercise.id == set.exerciseId }?.exercise
            ?: catalog[set.exerciseId]
            ?: return null
        return MuscleRecency.musclesOf(exercise).firstOrNull()
    }

    private class MuscleWeek {
        var count: Int = 0
        var allEasy: Boolean = true
    }
}
