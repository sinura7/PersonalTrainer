package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ActivityCardioIntervalEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.ActivityStrengthSetEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySummaryRow
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.entity.SessionStillRow
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import com.sinura.personaltrainer.data.local.relation.ActivityTemplateGraph
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_sessions WHERE liveToken = 'LIVE' LIMIT 1")
    suspend fun getLive(): ActivitySessionEntity?

    @Query("SELECT * FROM activity_sessions WHERE liveToken = 'LIVE' LIMIT 1")
    fun observeLive(): Flow<ActivitySessionEntity?>

    @Query("SELECT * FROM activity_sessions WHERE id = :id")
    suspend fun getSessionRow(id: String): ActivitySessionEntity?

    @Transaction
    @Query("SELECT * FROM activity_sessions WHERE id = :id")
    suspend fun getSessionGraph(id: String): ActivitySessionGraph?

    @Transaction
    @Query("SELECT * FROM activity_sessions ORDER BY performedStartInstantMs DESC")
    suspend fun getAllGraphs(): List<ActivitySessionGraph>

    @Transaction
    @Query("SELECT * FROM activity_sessions WHERE status = 'COMPLETED' AND performedStartInstantMs >= :minMs ORDER BY performedStartInstantMs DESC")
    fun observeCompletedGraphsSince(minMs: Long): Flow<List<ActivitySessionGraph>>

    @Query(
        """
        SELECT s.id AS id,
               s.title AS title,
               s.performedStartInstantMs AS date,
               s.performedEndInstantMs AS finishedAt,
               s.performedStartLocalEpochDay AS localEpochDay,
               COALESCE((
                   SELECT COUNT(*) FROM activity_blocks b
                   INNER JOIN activity_strength_sets st ON st.blockId = b.id
                   WHERE b.sessionId = s.id AND st.isWarmup = 0
               ), 0) AS workingSets,
               COALESCE((
                   SELECT SUM(st.weightKg * st.reps) FROM activity_blocks b
                   INNER JOIN activity_strength_sets st ON st.blockId = b.id
                   WHERE b.sessionId = s.id AND st.isWarmup = 0
               ), 0) AS volumeKg,
               COALESCE((
                   SELECT SUM(b.elapsedSeconds) FROM activity_blocks b
                   WHERE b.sessionId = s.id AND b.kind = 'CARDIO'
               ), 0) AS cardioSeconds,
               (
                   SELECT SUM(b.distanceMeters) FROM activity_blocks b
                   WHERE b.sessionId = s.id AND b.kind = 'CARDIO'
               ) AS cardioDistanceMeters
        FROM activity_sessions s
        WHERE s.status = 'COMPLETED'
        ORDER BY s.performedStartInstantMs DESC
        """,
    )
    fun observeCompletedSummaries(): Flow<List<ActivitySummaryRow>>

    /**
     * Strength lifts on completed activities, in block order. History
     * cards picture the first few. Catalog columns win when the row
     * still exists so the keyed still can show; snapshot columns stand
     * in when it does not (soft FK).
     */
    @Query(
        """
        SELECT b.sessionId AS sessionId,
               b.sortOrder AS sortOrder,
               COALESCE(e.id, b.exerciseId) AS id,
               COALESCE(e.name, b.exerciseName, '') AS name,
               COALESCE(e.muscleGroup, '') AS muscleGroup,
               COALESCE(e.notes, '') AS notes,
               COALESCE(e.isCustom, 0) AS isCustom,
               COALESCE(e.equipment, b.equipment, 'OTHER') AS equipment,
               COALESCE(e.loadType, b.loadType, 'EXTERNAL') AS loadType,
               e.movementKey AS movementKey,
               e.imageKey AS imageKey
        FROM activity_blocks b
        INNER JOIN activity_sessions s ON s.id = b.sessionId
        LEFT JOIN exercises e ON e.id = b.exerciseId
        WHERE s.status = 'COMPLETED'
          AND b.kind = 'STRENGTH'
          AND b.exerciseId IS NOT NULL
        ORDER BY b.sessionId, b.sortOrder
        """,
    )
    suspend fun completedSessionStills(): List<SessionStillRow>

    /**
     * Every working set of every completed activity's strength blocks, with the block's own
     * snapshot of the lift's name and load type, for the lifetime records list. Observed
     * directly: activity tables change on a confirm, not on every live set, so Room's
     * invalidation is the right gate here where it is not for `set_logs`.
     */
    @Query(
        """
        SELECT st.id AS setId,
               s.id AS sessionId,
               b.exerciseId AS exerciseId,
               b.exerciseName AS exerciseName,
               b.loadType AS loadType,
               st.weightKg AS weightKg,
               st.reps AS reps,
               st.completedAtMs AS completedAt
        FROM activity_strength_sets st
        JOIN activity_blocks b ON b.id = st.blockId
        JOIN activity_sessions s ON s.id = b.sessionId
        WHERE st.isWarmup = 0
          AND b.kind = 'STRENGTH'
          AND b.exerciseId IS NOT NULL
          AND s.status = 'COMPLETED'
        """,
    )
    fun observeCompletedStrengthSetRecords(): Flow<List<RecordSetRow>>

    /**
     * Finished working sets of one lift, same projection as
     * [WorkoutDao.observeFinishedWorkingSets]. Exercise detail reads both
     * through [com.sinura.personaltrainer.data.repository.CompletedTrainingRepository].
     */
    @Query(
        """
        SELECT st.id AS setId,
               s.id AS sessionId,
               s.title AS sessionName,
               s.performedStartInstantMs AS sessionDate,
               st.weightKg AS weightKg,
               st.reps AS reps,
               st.completedAtMs AS completedAt
        FROM activity_strength_sets st
        JOIN activity_blocks b ON b.id = st.blockId
        JOIN activity_sessions s ON s.id = b.sessionId
        WHERE b.exerciseId = :exerciseId
          AND st.isWarmup = 0
          AND b.kind = 'STRENGTH'
          AND s.status = 'COMPLETED'
        ORDER BY st.completedAtMs ASC
        """,
    )
    fun observeFinishedWorkingSets(exerciseId: String): Flow<List<ExerciseSetRow>>

    @Transaction
    @Query("SELECT * FROM activity_sessions WHERE performedStartLocalEpochDay = :localEpochDay ORDER BY performedStartInstantMs")
    suspend fun graphsOnLocalDate(localEpochDay: Long): List<ActivitySessionGraph>

    @Query("SELECT COUNT(*) FROM activity_sessions")
    suspend fun sessionCount(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: ActivitySessionEntity)

    @Update
    suspend fun updateSession(session: ActivitySessionEntity)

    @Query("DELETE FROM activity_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM activity_sessions")
    suspend fun deleteAllSessions()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBlock(block: ActivityBlockEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertBlocks(blocks: List<ActivityBlockEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStrengthSets(sets: List<ActivityStrengthSetEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCardioIntervals(intervals: List<ActivityCardioIntervalEntity>)

    @Transaction
    @Query("SELECT * FROM activity_templates ORDER BY title")
    suspend fun getAllTemplateGraphs(): List<ActivityTemplateGraph>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: ActivityTemplateEntity)

    @Query("DELETE FROM activity_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM activity_templates")
    suspend fun deleteAllTemplates()
}
