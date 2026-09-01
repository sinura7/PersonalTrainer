package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sinura.personaltrainer.data.local.entity.ExerciseRecencyRow
import com.sinura.personaltrainer.data.local.entity.ExerciseRecordPriorsRow
import com.sinura.personaltrainer.data.local.entity.FinishedWorkGeneration
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionSummaryRow
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.local.relation.SessionWithDetails
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL ORDER BY date DESC")
    fun observeFinishedSessions(): Flow<List<SessionWithDetails>>

    // Assisted kilograms are machine help REMOVED, credited as 0 by the domain
    // volume rule (SetWork): counting them here showed 720 kg on History and the
    // Home tile for a session whose own summary honestly said 0. The reps still
    // count as working sets. A deleted catalog row reads as loaded — the same
    // stricter fallback LoadClass.of takes.
    @Query(
        """
        SELECT s.id AS id, s.routineId AS routineId, s.routineName AS routineName,
               s.date AS date, s.finishedAt AS finishedAt, s.durationMinutes AS durationMinutes,
               COALESCE(SUM(CASE WHEN l.isWarmup = 0 THEN 1 ELSE 0 END), 0) AS workingSets,
               COALESCE(SUM(CASE WHEN l.isWarmup = 0 AND COALESCE(e.loadType, '') != 'ASSISTED'
                                 THEN l.weightKg * l.reps ELSE 0 END), 0) AS volumeKg
        FROM workout_sessions s
        LEFT JOIN set_logs l ON l.sessionId = s.id
        LEFT JOIN exercises e ON e.id = l.exerciseId
        WHERE s.finishedAt IS NOT NULL
        GROUP BY s.id
        ORDER BY s.date DESC
        """,
    )
    suspend fun sessionSummaries(): List<SessionSummaryRow>

    /**
     * Cheap fingerprint of finished work. Mentions `set_logs`, so Room still
     * re-runs it on every log; the result is equal until a finished session
     * actually changes.
     *
     * The two sums are what makes "actually changes" include an *edit*.
     * `updateSet` deliberately leaves `completedAt` and `setNumber` alone, so
     * correcting a mistyped weight moved nothing in the count-and-timestamp
     * fingerprint this used to be: the screen you edited on updated, and
     * Home's last session, History's totals and the body map kept the old
     * number until an unrelated workout happened to finish. Both are
     * aggregates over a join the query already performs.
     */
    @Query(
        """
        SELECT
          (SELECT COUNT(*) FROM workout_sessions WHERE finishedAt IS NOT NULL) AS finishedSessionCount,
          (SELECT COALESCE(SUM(durationMinutes), 0) FROM workout_sessions WHERE finishedAt IS NOT NULL) AS durationSum,
          (SELECT MAX(finishedAt) FROM workout_sessions WHERE finishedAt IS NOT NULL) AS lastFinishedAt,
          (
            SELECT COUNT(*) FROM set_logs sl
            INNER JOIN workout_sessions ws ON ws.id = sl.sessionId
            WHERE sl.isWarmup = 0 AND ws.finishedAt IS NOT NULL
          ) AS finishedWorkingSetCount,
          (
            SELECT MAX(sl.completedAt) FROM set_logs sl
            INNER JOIN workout_sessions ws ON ws.id = sl.sessionId
            WHERE sl.isWarmup = 0 AND ws.finishedAt IS NOT NULL
          ) AS lastFinishedSetAt,
          (
            SELECT COALESCE(SUM(sl.weightKg * sl.reps), 0) FROM set_logs sl
            INNER JOIN workout_sessions ws ON ws.id = sl.sessionId
            WHERE sl.isWarmup = 0 AND ws.finishedAt IS NOT NULL
          ) AS finishedWorkingVolumeKg,
          (
            SELECT COALESCE(SUM(sl.reps), 0) FROM set_logs sl
            INNER JOIN workout_sessions ws ON ws.id = sl.sessionId
            WHERE sl.isWarmup = 0 AND ws.finishedAt IS NOT NULL
          ) AS finishedWorkingRepCount
        FROM (SELECT 1)
        """,
    )
    fun observeFinishedWorkGeneration(): Flow<FinishedWorkGeneration>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL AND date >= :minDateMs ORDER BY date DESC")
    suspend fun getFinishedSessionsSince(minDateMs: Long): List<SessionWithDetails>

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NOT NULL AND date >= :minDateMs AND date <= :maxDateMs ORDER BY date DESC")
    suspend fun getFinishedSessionsBetween(minDateMs: Long, maxDateMs: Long): List<SessionWithDetails>

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
     * The last [limit] finished sessions containing this exercise, newest first.
     *
     * Same WHERE and ordering as the single-id query above — the RPE rule needs a *run* of
     * sessions rather than one, and two different definitions of "the last session with this
     * lift" would eventually disagree about which set the suggestion was made on.
     */
    @Query(
        """
        SELECT ws.id FROM workout_sessions ws
        INNER JOIN set_logs sl ON sl.sessionId = ws.id
        WHERE sl.exerciseId = :exerciseId
          AND sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
          AND ws.id != :excludeSessionId
        GROUP BY ws.id
        ORDER BY ws.finishedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun lastFinishedSessionIdsWithExercise(
        exerciseId: String,
        excludeSessionId: String,
        limit: Int,
    ): List<String>

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

    /**
     * Every finished working set of the requested lifts, for a batched
     * progression pass. No window functions — minSdk 26's SQLite cannot.
     * Kotlin groups by lift and session and keeps the last two sessions.
     */
    @Query(
        """
        SELECT sl.exerciseId AS exerciseId,
               sl.sessionId AS sessionId,
               sl.weightKg AS weightKg,
               sl.reps AS reps,
               sl.completedAt AS completedAt,
               sl.rpe AS rpe,
               ws.finishedAt AS sessionFinishedAt
        FROM set_logs sl
        JOIN workout_sessions ws ON ws.id = sl.sessionId
        WHERE sl.exerciseId IN (:exerciseIds)
          AND sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
        ORDER BY sl.exerciseId ASC, ws.finishedAt DESC, sl.sessionId ASC
        """,
    )
    suspend fun finishedWorkingSetsForExercises(
        exerciseIds: List<String>,
    ): List<FinishedWorkingSetRow>

    /**
     * Standing bests before [completedAt] for one lift. Includes earlier
     * sets of the in-progress [sessionId] so a work-up cannot beat itself.
     *
     * `maxRepsAtEqualOrMoreAssistance` is read only for assisted lifts, where
     * `weightKg` is machine help: a rep count bought by turning the assistance up
     * is not a record, so a rep record also requires that the standing rep record
     * was itself set at no less help. The `>=` is deliberate and is not a typo for
     * the `=` on the line above, which answers a different question for loaded
     * work.
     */
    @Query(
        """
        SELECT COUNT(*) AS priorSetCount,
               MAX(sl.weightKg) AS maxWeightKg,
               MAX(sl.reps) AS maxReps,
               MAX(CASE WHEN sl.weightKg = :weightKg THEN sl.reps ELSE NULL END) AS maxRepsAtWeight,
               MAX(
                 CASE WHEN sl.weightKg >= :weightKg THEN sl.reps ELSE NULL END
               ) AS maxRepsAtEqualOrMoreAssistance,
               MAX(
                 CASE
                   WHEN sl.weightKg <= 0 THEN NULL
                   WHEN sl.reps = 1 THEN sl.weightKg
                   WHEN sl.reps BETWEEN 2 AND 12 THEN sl.weightKg * (1.0 + sl.reps / 30.0)
                   ELSE NULL
                 END
               ) AS maxEstimatedOneRepMaxKg
        FROM set_logs sl
        JOIN workout_sessions ws ON ws.id = sl.sessionId
        WHERE sl.exerciseId = :exerciseId
          AND sl.isWarmup = 0
          AND sl.reps > 0
          AND sl.completedAt < :completedAt
          AND (ws.finishedAt IS NOT NULL OR sl.sessionId = :sessionId)
        """,
    )
    suspend fun recordPriorsBefore(
        exerciseId: String,
        sessionId: String,
        weightKg: Double,
        completedAt: Long,
    ): ExerciseRecordPriorsRow

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

    /**
     * Best finished working weight per lift. One aggregate, no set graph
     * (P8.2 lift-target goals). ASSISTED lifts are excluded outright:
     * their weightKg is machine help REMOVED, so MAX picked the
     * most-assisted — easiest — set as the "best". A weight goal on an
     * assisted lift has no honest kilogram answer here.
     */
    @Query(
        """
        SELECT sl.exerciseId AS exerciseId, MAX(sl.weightKg) AS bestKg
        FROM set_logs sl
        JOIN workout_sessions ws ON ws.id = sl.sessionId
        LEFT JOIN exercises e ON e.id = sl.exerciseId
        WHERE sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
          AND COALESCE(e.loadType, '') != 'ASSISTED'
        GROUP BY sl.exerciseId
        """,
    )
    fun observeBestWorkingWeights(): Flow<List<ExerciseBestWeightRow>>

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

    /**
     * When each lift was last logged, for the picker's recency order.
     *
     * An aggregate rather than reading the sessions and folding them in Kotlin: the picker is
     * opened mid-workout and only needs one number per exercise, while the sessions themselves
     * are the largest thing in the database. `set_logs` is already indexed on `exerciseId`.
     */
    @Query("SELECT exerciseId AS exerciseId, MAX(completedAt) AS lastLoggedAt FROM set_logs GROUP BY exerciseId")
    fun observeLastLogged(): Flow<List<ExerciseRecencyRow>>

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
