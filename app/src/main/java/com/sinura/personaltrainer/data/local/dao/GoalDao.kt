package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sinura.personaltrainer.data.local.entity.MeasurableGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM measurable_goals ORDER BY createdAtMs ASC")
    fun observeAll(): Flow<List<MeasurableGoalEntity>>

    @Query("SELECT * FROM measurable_goals ORDER BY createdAtMs ASC")
    suspend fun getAll(): List<MeasurableGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: MeasurableGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(goals: List<MeasurableGoalEntity>)

    @Query("DELETE FROM measurable_goals WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM measurable_goals")
    suspend fun deleteAll()
}
