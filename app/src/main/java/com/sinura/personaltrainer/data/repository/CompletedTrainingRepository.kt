package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.domain.CompletedTraining
import com.sinura.personaltrainer.domain.ExerciseSetEntry
import com.sinura.personaltrainer.domain.RecordSet
import com.sinura.personaltrainer.domain.SessionSummary
import com.sinura.personaltrainer.domain.TimePort
import com.sinura.personaltrainer.domain.toCompletedTraining
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.security.MessageDigest

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
     * A failed part holds what it last had, or counts as nothing if it never loaded; the
     * list's own reads say whether the page is behind.
     */
    fun observeRevision(): Flow<String> = combine(
        workouts.observeFinishedWorkRevisionHealth(),
        activities.observeCompletedSummariesHealth(),
        activities.observeRecordSetsHealth(),
    ) { workoutRevision, summaryReads, records ->
        val summaries = summaryReads.presentValue().orEmpty()
        val recordRows = records.presentValue().orEmpty()
        listOf(
            workoutRevision.presentValue().orEmpty(),
            activityContentRevision(summaries, recordRows),
        ).joinToString("/")
    }

    /**
     * Restore can replace individual sets without changing IDs, timestamps or aggregate
     * volume. Use the existing cheap projections' actual contents, including non-best
     * sets, rather than a count or calculated standing best. This remains an opaque
     * read-model token; no saved row or backup format changes.
     */
    private fun activityContentRevision(
        summaries: List<SessionSummary>,
        records: List<RecordSet>,
    ): String {
        val digest = ReadContentRevision()
        digest.part(summaries.size)
        summaries.forEach { row ->
            with(digest) {
                part(row.kind.name)
                part(row.id)
                part(row.routineId)
                part(row.routineName)
                part(row.date)
                part(row.finishedAt)
                part(row.durationMinutes)
                part(row.workingSets)
                part(row.volumeKg)
                part(row.localEpochDay)
                part(row.cardioSeconds)
                part(row.cardioDistanceMeters)
            }
        }
        digest.part(records.size)
        records.forEach { row ->
            with(digest) {
                part(row.exerciseId)
                part(row.exerciseName)
                part(row.loadClass.name)
                part(row.set.setId)
                part(row.set.sessionId)
                part(row.set.weightKg)
                part(row.set.reps)
                part(row.set.completedAt)
                part(row.set.rpe)
            }
        }
        return digest.token()
    }
}

/** Internal read token only: no durable format, counters or calculation rules. */
internal class ReadContentRevision {
    private val digest = MessageDigest.getInstance("SHA-256")

    fun part(value: Any?) {
        val bytes = value?.toString()?.toByteArray(Charsets.UTF_8)
        // Byte-length framing distinguishes null, empty and delimiter-bearing names.
        digest.update((bytes?.size ?: -1).toString().toByteArray(Charsets.US_ASCII))
        digest.update(0.toByte())
        if (bytes != null) digest.update(bytes)
    }

    fun token(): String = digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}
