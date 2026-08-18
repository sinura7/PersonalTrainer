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

    @Query("SELECT * FROM workout_sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeInProgressSession(): Flow<WorkoutSessionEntity?>

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

    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId
          AND isWarmup = 0
          AND sessionId IN (SELECT id FROM workout_sessions WHERE finishedAt IS NOT NULL AND id != :excludeSessionId)
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    suspend fun lastWorkingSetExcluding(exerciseId: String, excludeSessionId: String): SetLogEntity?

    @Query(
        """
        SELECT * FROM set_logs
        WHERE exerciseId = :exerciseId
          AND isWarmup = 0
          AND sessionId IN (SELECT id FROM workout_sessions WHERE finishedAt IS NOT NULL)
        ORDER BY completedAt DESC
        LIMIT 1
        """,
    )
    suspend fun lastFinishedWorkingSet(exerciseId: String): SetLogEntity?

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
