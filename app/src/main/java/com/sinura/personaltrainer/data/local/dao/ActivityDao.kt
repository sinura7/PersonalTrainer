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
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.relation.ActivitySessionGraph
import com.sinura.personaltrainer.data.local.relation.ActivityTemplateGraph
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activity_sessions WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getLive(): ActivitySessionEntity?

    @Query("SELECT * FROM activity_sessions WHERE status = 'ACTIVE' LIMIT 1")
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

    @Query("DELETE FROM activity_blocks WHERE sessionId = :sessionId")
    suspend fun deleteBlocksForSession(sessionId: String)

    @Transaction
    @Query("SELECT * FROM activity_templates ORDER BY title")
    suspend fun getAllTemplateGraphs(): List<ActivityTemplateGraph>

    @Transaction
    @Query("SELECT * FROM activity_templates WHERE id = :id")
    suspend fun getTemplateGraph(id: String): ActivityTemplateGraph?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: ActivityTemplateEntity)

    @Query("DELETE FROM activity_templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @Query("DELETE FROM activity_templates")
    suspend fun deleteAllTemplates()
}
