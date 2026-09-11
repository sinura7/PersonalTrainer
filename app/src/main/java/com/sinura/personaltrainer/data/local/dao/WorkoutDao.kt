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

    /**
     * Writes the notes column and nothing else.
     *
     * [updateSession] rewrites the whole row from an entity the caller read moments earlier,
     * which makes every one of its columns a hostage to whatever else wrote in between. Notes
     * and Finish are the pair that meet: type a note, close the sheet and tap Finish inside the
     * 400 ms debounce, and the notes write — holding a row it read while the session was still
     * running — puts `finishedAt = null` back over the finish that landed in between. The
     * session un-finishes, and the summary is already claiming it is done.
     *
     * A one-column UPDATE cannot lose that race because it never carries the other columns.
     */
    @Query("UPDATE workout_sessions SET notes = :notes WHERE id = :id")
    suspend fun updateSessionNotes(id: String, notes: String)

    /**
     * Finishes the session in one statement, for the same reason [updateSessionNotes] exists.
     *
     * The other half of that race was still open: `finishSession` read the whole row, computed
     * a duration, and wrote every column back from what it had read — so anything that changed
     * in between was reverted by a finish that had never seen it. The note above explains why
     * that shape cannot be made safe by care at the call site.
     *
     * `finishedAt IS NULL` carries the already-finished guard in SQL rather than in a read the
     * caller does first, so two Finishes cannot both decide they are the one that landed.
     * Returns the rows written: 0 means the session was gone or already finished.
     */
    @Query(
        """
        UPDATE workout_sessions
        SET notes = :notes, durationMinutes = :durationMinutes, finishedAt = :finishedAt
        WHERE id = :id AND finishedAt IS NULL
        """,
    )
    suspend fun finishSession(
        id: String,
        notes: String,
        durationMinutes: Int,
        finishedAt: Long,
    ): Int

    /** The moment a session began. Immutable once written, so reading it races with nothing. */
    @Query("SELECT startedAt FROM workout_sessions WHERE id = :id")
    suspend fun sessionStartedAt(id: String): Long?

    @Query("DELETE FROM workout_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM workout_sessions WHERE id = :id AND finishedAt IS NULL")
    suspend fun deleteInProgressSession(id: String): Int

    @Query("DELETE FROM workout_sessions WHERE id = :id AND finishedAt IS NOT NULL")
    suspend fun deleteFinishedSessionRow(id: String): Int

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

    /**
     * Every finished working set of the requested lifts, for a batched
     * progression pass. No window functions — minSdk 26's SQLite cannot.
     * Kotlin groups by lift and session and keeps the last two sessions.
     */
    @Query(
        """
        SELECT sl.id AS setId,
               sl.exerciseId AS exerciseId,
               sl.sessionId AS sessionId,
               ws.routineName AS sessionName,
               ws.date AS sessionDate,
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
          AND (
            sl.completedAt < :completedAt
            OR (sl.completedAt = :completedAt AND sl.setNumber < :setNumber)
          )
          AND (ws.finishedAt IS NOT NULL OR sl.sessionId = :sessionId)
        """,
    )
    /**
     * Everything logged for this lift before this set, for the record check.
     *
     * "Before" is `(completedAt, setNumber)`, not `completedAt` alone. A millisecond stamp is
     * not a total order over sets: two taps inside the same millisecond — which the JVM test
     * lane hits routinely and a fast phone can hit for real — left the second set with no
     * priors at all, so a genuine heaviest-ever was judged the first set of the lift and broke
     * nothing. [setNumber] is assigned inside [WorkoutRepository.logSet]'s transaction and is
     * strictly increasing per lift per session, which is the only place a tie can occur:
     * every other row in range belongs to a finished session with an older stamp.
     */
    suspend fun recordPriorsBefore(
        exerciseId: String,
        sessionId: String,
        weightKg: Double,
        completedAt: Long,
        setNumber: Int,
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

    /**
     * Every finished working set of every lift, with the lift's name and load type, for the
     * lifetime records list. A flat projection on purpose: the alternative was the full
     * session graph of the whole log, which is what the History screen used to avoid by
     * reading only 32 days and calling the result "Records". The name and class come from
     * the library row the session relation reads too, so a deleted lift (which the catalog
     * refuses while sets reference it) reads as unnamed and loaded rather than dropping out.
     */
    @Query(
        """
        SELECT sl.id AS setId,
               sl.sessionId AS sessionId,
               sl.exerciseId AS exerciseId,
               e.name AS exerciseName,
               e.loadType AS loadType,
               sl.weightKg AS weightKg,
               sl.reps AS reps,
               sl.completedAt AS completedAt
        FROM set_logs sl
        JOIN workout_sessions ws ON ws.id = sl.sessionId
        LEFT JOIN exercises e ON e.id = sl.exerciseId
        WHERE sl.isWarmup = 0
          AND ws.finishedAt IS NOT NULL
        """,
    )
    suspend fun finishedWorkingSetRecords(): List<RecordSetRow>

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
     * Ungated on purpose: the picker is opened mid-workout and should rank the
     * lift just logged. Insights uses [finishedLastLogged] instead, gated on
     * finished work so a live set does not re-run the analytics pass.
     *
     * An aggregate rather than reading the sessions and folding them in Kotlin:
     * the picker only needs one number per exercise, while the sessions
     * themselves are the largest thing in the database. `set_logs` is already
     * indexed on `exerciseId`.
     */
    @Query("SELECT exerciseId AS exerciseId, MAX(completedAt) AS lastLoggedAt FROM set_logs GROUP BY exerciseId")
    fun observeLastLogged(): Flow<List<ExerciseRecencyRow>>

    /**
     * Recency over finished sessions only. A suspend query so callers can gate
     * it on [observeFinishedWorkGeneration] the way [sessionSummaries] is
     * gated; a Flow here would still invalidate on every live set because Room
     * watches `set_logs`.
     */
    @Query(
        """
        SELECT sl.exerciseId AS exerciseId, MAX(sl.completedAt) AS lastLoggedAt
        FROM set_logs sl
        INNER JOIN workout_sessions ws ON ws.id = sl.sessionId
        WHERE ws.finishedAt IS NOT NULL
        GROUP BY sl.exerciseId
        """,
    )
    suspend fun finishedLastLogged(): List<ExerciseRecencyRow>

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
