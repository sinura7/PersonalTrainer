package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.CompletedTraining
import com.sinura.personaltrainer.domain.ExerciseSetEntry
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
     * Finished working sets of one lift from both stores. Strength sessions
     * and backdated strength activities share [ExerciseSetEntry]; the PR
     * badge at log time still reads the strength store alone.
     */
    fun observeExerciseSets(exerciseId: String): Flow<List<ExerciseSetEntry>> = combine(
        workouts.observeExerciseSets(exerciseId),
        activities.observeExerciseSets(exerciseId),
    ) { fromWorkouts, fromActivities ->
        (fromWorkouts + fromActivities).sortedBy { entry -> entry.record.completedAt }
    }

    /**
     * Moves when either store's finished work changes, so History's horizon key cannot
     * stay put after a backdated activity lands.
     *
     * Never throws. It used to combine two raw reads, so an activity log that could not be
     * read took History down with it instead of reaching the list's own health (audit UI-17).
     * A failed part holds what it last had; the list's reads say whether the page is behind.
     */
    fun observeRevision(): Flow<String> = combine(
        workouts.observeFinishedWorkRevision().observeHealth("the finished-work revision"),
        activities.observeCompletedSummariesHealth(),
        activities.observeRecordSetsHealth(),
    ) { workoutRevision, summaryReads, records ->
        val summaries = summaryReads.presentValue().orEmpty()
        val recordRows = records.presentValue().orEmpty()
        listOf(
            workoutRevision.presentValue().orEmpty(),
            summaries.size.toString(),
            (summaries.maxOfOrNull { it.finishedAt ?: it.date } ?: 0L).toString(),
            recordRows.size.toString(),
        ).joinToString("/")
    }
}