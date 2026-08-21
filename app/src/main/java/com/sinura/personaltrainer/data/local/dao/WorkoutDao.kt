package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL ORDER BY date DESC")
    fun observeFinishedSessions(): Flow<List<SessionWithDetails>>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<SessionWithDetails?>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSession(id: String): SessionWithDetails?

    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getInProgressSession(): WorkoutSessionEntity?

    /** The session row alone. [getSession] pulls its whole exercise and set graph with it. */
    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getSessionRow(id: String): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgressSession(): Flow<WorkoutSessionEntity?>

    /**
     * Set counters for one session, aggregated in SQL for the live-session bar.
     *
     * `lastCompletedAt` counts warm-ups on purpose: a warm-up set is activity, and the
     * staleness rule is about whether the user is still training, not about working volume.
     */
    @Query(
        """
        SELECT COUNT(*) AS totalSets,
               COUNT(CASE WHEN isWarmup = 0 THEN 1 END) AS workingSets,
               MAX(completedAt) AS lastCompletedAt
        FROM set_logs WHERE sessionId = :sessionId
        """,
    )
    fun observeSessionActivity(sessionId: String): Flow<SessionActivityRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: WorkoutSessionEntity)

    @Update
    suspend fun updateSession(session: WorkoutSessionEntity)

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSessionExercise(item: SessionExerciseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessionExercises(items: List<SessionExerciseEntity>)

    @Query("DELETE FROM session_exercises WHERE id = :id")
    suspend fun deleteSessionExercise(id: String)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM session_exercises WHERE sessionId = :sessionId")
    suspend fun maxSessionExerciseOrder(sessionId: String): Int

    @Insert
    suspend fun insertSet(set: SetLogEntity)

    @Query("SELECT * FROM set_logs WHERE id = :id")
    suspend fun getSet(id: String): SetLogEntity?

    @Update
    suspend fun updateSet(set: SetLogEntity)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: String)

    @Query(
        """
        SELECT * FROM set_logs
        WHERE sessionId = :sessionId AND exerciseId = :exerciseId
        ORDER BY setNumber ASC, completedAt ASC
        """,
    )
    suspend fun setsForExercise(sessionId: String, exerciseId: String): List<SetLogEntity>

    /**
     * The id of the most recently finished session that contains a working set of this
     * exercise, or null if there is none.
     *
     * Deliberately returns the SESSION, not a set: which set within it counts is a coaching
     * decision that lives in [com.sinura.personaltrainer.domain.ProgressionBasis], where it is
     * unit-testable. Pass an empty string for [excludeSessionId] to exclude nothing.
     *
     * Replaces the old lastWorkingSetExcluding / lastFinishedWorkingSet pair, which ordered by
     * completedAt and so returned whatever set happened to be logged last — a back-off set
     * after a top set. Those queries are gone rather than deprecated so the bug cannot be
     * reintroduced by calling them.
     */
    @Query(
        """
        SELECT ws.id FROM workout_sessions ws
        INNER JOIN set_logs sl ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId
          AND sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
          AND ws.id != :excludeSessionId
        ORDER BY ws.finishedAt DESC
        LIMIT 1
        """,
    )
    suspend fun lastFinishedSessionIdWithExercise(
        exerciseId: String,
        excludeSessionId: String,
    ): String?

    /**
     * Every finished working set of one exercise, oldest first, with just enough of its
     * session attached to summarise it.
     *
     * A narrow projection on purpose. The exercise detail screen is about one lift, and
     * subscribing to the full-history deep graph to find it would map every set of every
     * session the user has ever logged to answer a question about one of them.
     */
    @Query(
        """
        SELECT sl.id AS setId,
               sl.sessionId AS sessionId,
               ws.routineName AS sessionName,
               ws.date AS sessionDate,
               sl.weightKg AS weightKg,
               sl.reps AS reps,
               sl.completedAt AS completedAt
        FROM set_logs sl
        JOIN workout_sessions ws ON ws.id = sl.sessionId
        WHERE sl.exerciseId = :exerciseId
          AND sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
        ORDER BY sl.completedAt ASC
        """,
    )
    fun observeFinishedWorkingSets(exerciseId: String): Flow<List<ExerciseSetRow>>

    /** The same rows, read once — for the personal-record check at the moment a set is logged. */
    @Query(
        """
        SELECT sl.id AS setId,
               sl.sessionId AS sessionId,
               ws.routineName AS sessionName,
               ws.date AS sessionDate,
               sl.weightKg AS weightKg,
               sl.reps AS reps,
               sl.completedAt AS completedAt
        FROM set_logs sl
        JOIN workout_sessions ws ON ws.id = sl.sessionId
        WHERE sl.exerciseId = :exerciseId
          AND sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
        ORDER BY sl.completedAt ASC
        """,
    )
    suspend fun finishedWorkingSets(exerciseId: String): List<ExerciseSetRow>

    /** Every non-warmup set of one exercise in one session, for top-set selection. */
    @Query(
        """
        SELECT * FROM set_logs
        WHERE sessionId = :sessionId
          AND exerciseId = :exerciseId
          AND isWarmup = 0
        """,
    )
    suspend fun workingSetsForExerciseInSession(
        sessionId: String,
        exerciseId: String,
    ): List<SetLogEntity>

    @Query(
        """
        SELECT targetReps FROM session_exercises
        WHERE exerciseId = :exerciseId
          AND sessionId IN (SELECT id FROM workout_sessions WHERE finishedAt IS NOT NULL)
        ORDER BY (SELECT date FROM workout_sessions WHERE id = sessionId) DESC
        LIMIT 1
        """,
    )
    suspend fun lastTargetReps(exerciseId: String): Int?

    @Query("SELECT COUNT(*) FROM set_logs WHERE exerciseId = :exerciseId")
    suspend fun countSetsForExercise(exerciseId: String): Int

    @Query("SELECT COUNT(*) FROM session_exercises WHERE exerciseId = :exerciseId")
    suspend fun countSessionExercisesFor(exerciseId: String): Int

    @Query("SELECT * FROM workout_sessions ORDER BY id")
    suspend fun getAllSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercises ORDER BY id")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_logs ORDER BY id")
    suspend fun getAllSets(): List<SetLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceSessions(items: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceSets(items: List<SetLogEntity>)

    @Query("DELETE FROM set_logs")
    suspend fun deleteAllSets()

    @Query("DELETE FROM session_exercises")
    suspend fun deleteAllSessionExercises()

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAllSessions()
}
