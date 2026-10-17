package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadCalculator
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.Weekday
import com.sinura.personaltrainer.domain.WorkoutSession

object AddASetMuscleStats {
    fun weeklyHardSetsThisWeek(
        muscle: CanonicalMuscle,
        finishedHistory: List<WorkoutSession>,
        liveSession: WorkoutSession?,
        nowMs: Long,
        time: TimePort,
        zoneId: String,
        weekStart: Weekday,
        exerciseCatalog: Map<String, Exercise>,
    ): Double {
        val snap = MuscleLoadCalculator.snapshot(
            sessions = finishedHistory,
            window = HeatWindow.CURRENT_WEEK,
            nowMs = nowMs,
            time = time,
            zoneId = zoneId,
            exerciseCatalog = exerciseCatalog,
            weekStart = weekStart,
        )
        var total = snap.load(muscle).weeklySets
        liveSession?.sets?.filterNot { it.isWarmup }?.forEach { set ->
            val at = MuscleLoadCalculator.trainedAtMs(liveSession, set)
            val weekStartMs = HeatWindow.CURRENT_WEEK.startMs(nowMs, time, weekStart, zoneId)
            if (at in weekStartMs..nowMs) {
                val credits = muscleCredits(set.exerciseId, liveSession, exerciseCatalog)
                total += credits[muscle] ?: 0.0
            }
        }
        return total
    }

    /** Oldest → newest, last three calendar weeks of weighted hard sets for [muscle]. */
    fun muscleWeeklySetsLastThreeWeeks(
        muscle: CanonicalMuscle,
        finishedHistory: List<WorkoutSession>,
        liveSession: WorkoutSession?,
        nowMs: Long,
        time: TimePort,
        zoneId: String,
        weekStart: Weekday,
        exerciseCatalog: Map<String, Exercise>,
    ): List<Double> {
        val merged = mergeLive(finishedHistory, liveSession)
        val nowWeekStart = time.civilDate(nowMs, zoneId).previousOrSame(weekStart).epochDay
        val buckets = DoubleArray(3)
        merged.filter { it.isFinished || it.id == liveSession?.id }.forEach { session ->
            session.sets.filterNot { it.isWarmup }.forEach { set ->
                val at = MuscleLoadCalculator.trainedAtMs(session, set)
                if (at > nowMs) return@forEach
                val credits = muscleCredits(set.exerciseId, session, exerciseCatalog)
                val weight = credits[muscle] ?: return@forEach
                val setWeekStart = time.civilDate(at, zoneId).previousOrSame(weekStart).epochDay
                val weeksBack = ((nowWeekStart - setWeekStart) / Weekday.DAYS_IN_WEEK).toInt()
                if (weeksBack in 0..2) {
                    buckets[2 - weeksBack] += weight * MuscleLoadCalculator.setStimulus(set)
                }
            }
        }
        return buckets.toList()
    }

    private fun mergeLive(
        finishedHistory: List<WorkoutSession>,
        liveSession: WorkoutSession?,
    ): List<WorkoutSession> {
        if (liveSession == null) return finishedHistory
        return finishedHistory.filter { it.id != liveSession.id } + liveSession
    }

    private fun muscleCredits(
        exerciseId: String,
        session: WorkoutSession,
        catalog: Map<String, Exercise>,
    ): Map<CanonicalMuscle, Double> {
        val embedded = session.exercises.firstOrNull { it.exercise.id == exerciseId }?.exercise
        val exercise = catalog[exerciseId] ?: embedded ?: return emptyMap()
        val muscle = AddASetPolicy.primaryMuscleFor(exercise.muscles, exercise.muscleGroup) ?: return emptyMap()
        return mapOf(muscle to 1.0)
    }
}
