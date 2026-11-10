package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sinura.personaltrainer.data.local.entity.GymEntity
import kotlinx.coroutines.flow.Flow

data class GymVisitRow(
    val gymId: String?,
    val sessions: Int,
    val lastAt: Long?,
)

@Dao
interface GymDao {
    @Query(
        """
        SELECT * FROM gyms
        ORDER BY archivedAtMs IS NOT NULL, sortOrder, id
        """,
    )
    fun observeAll(): Flow<List<GymEntity>>

    @Query(
        """
        SELECT * FROM gyms
        ORDER BY archivedAtMs IS NOT NULL, sortOrder, id
        """,
    )
    suspend fun getAll(): List<GymEntity>

    @Query("SELECT * FROM gyms WHERE id = :id")
    suspend fun get(id: String): GymEntity?

    @Upsert
    suspend fun upsert(gym: GymEntity)

    @Upsert
    suspend fun upsertAll(gyms: List<GymEntity>)

    @Query(
        """
        SELECT gymId, COUNT(*) AS sessions,
               MAX(COALESCE(finishedAt, startedAt)) AS lastAt
        FROM workout_sessions
        WHERE finishedAt IS NOT NULL
        GROUP BY gymId
        """,
    )
    fun observeVisits(): Flow<List<GymVisitRow>>

    @Query(
        """
        SELECT DISTINCT gymId FROM workout_sessions
        WHERE gymId IS NOT NULL AND gymId NOT IN (SELECT id FROM gyms)
        UNION
        SELECT DISTINCT gymId FROM activity_sessions
        WHERE gymId IS NOT NULL AND gymId NOT IN (SELECT id FROM gyms)
        """,
    )
    suspend fun danglingGymIds(): List<String>

    @Query("DELETE FROM gyms")
    suspend fun deleteAll()

    @Query(
        """
        SELECT COUNT(*) FROM gyms
        WHERE archivedAtMs IS NULL
        """,
    )
    suspend fun countActive(): Int

    @Query(
        """
        SELECT * FROM gyms
        WHERE archivedAtMs IS NULL AND LOWER(TRIM(name)) = LOWER(TRIM(:name))
        AND (:excludeId IS NULL OR id != :excludeId)
        LIMIT 1
        """,
    )
    suspend fun activeWithNameIgnoringCase(name: String, excludeId: String? = null): GymEntity?

    @Query(
        """
        SELECT * FROM gyms
        WHERE archivedAtMs IS NULL AND UPPER(TRIM(badge)) = UPPER(TRIM(:badge))
        AND (:excludeId IS NULL OR id != :excludeId)
        LIMIT 1
        """,
    )
    suspend fun activeWithBadge(badge: String, excludeId: String? = null): GymEntity?

    @Query(
        """
        SELECT COUNT(*) > 0 FROM workout_sessions
        WHERE finishedAt IS NULL AND gymId IS :id
        """,
    )
    suspend fun liveSessionUsesGym(id: String): Boolean
}
