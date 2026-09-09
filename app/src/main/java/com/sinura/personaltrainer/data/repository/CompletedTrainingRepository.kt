package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.CompletedTraining
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.toCompletedTraining
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Read model over both completed-training stores (completed-training-convergence.md).
 *
 * IDs stay the existing row ids. No third table, no rewrite.
 */
class CompletedTrainingRepository(
    private val workouts: WorkoutRepository,
    private val activities: ActivityRepository,
    private val time: TimePort,
) {
    suspend fun all(): List<CompletedTraining> {
        val zone = time.defaultZoneId()
        val fromWorkouts = workouts.sessionsBetween(minDateMs = 0L, maxDateMs = Long.MAX_VALUE)
            .mapNotNull { session -> session.toCompletedTraining(time, zone) }
        val fromActivities = activities.all().mapNotNull { session -> session.toCompletedTraining() }
        return fromWorkouts + fromActivities
    }

    /**
     * Moves when either store's finished work changes, so History's horizon key cannot
     * stay put after a backdated activity lands.
     */
    fun observeRevision(): Flow<String> = combine(
        workouts.observeFinishedWorkRevision(),
        activities.observeCompletedSummaries(),
        activities.observeRecordSetsHealth(),
    ) { workoutRevision, summaries, records ->
        val recordRows = records.presentValue().orEmpty()
        listOf(
            workoutRevision,
            summaries.size.toString(),
            (summaries.maxOfOrNull { it.finishedAt ?: it.date } ?: 0L).toString(),
            recordRows.size.toString(),
        ).joinToString("/")
    }
}