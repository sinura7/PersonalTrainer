package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sinura.personaltrainer.data.local.entity.BodyweightEntryEntity
import com.sinura.personaltrainer.data.local.entity.TrainingBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyweightDao {
    @Query("SELECT * FROM bodyweight_entries ORDER BY epochDay ASC")
    fun observeAll(): Flow<List<BodyweightEntryEntity>>

    @Query("SELECT * FROM bodyweight_entries ORDER BY epochDay ASC")
    suspend fun getAll(): List<BodyweightEntryEntity>

    @Query("SELECT COUNT(*) FROM bodyweight_entries")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyweightEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<BodyweightEntryEntity>)

    @Query("DELETE FROM bodyweight_entries WHERE epochDay = :epochDay")
    suspend fun deleteDay(epochDay: Long)

    @Query("DELETE FROM bodyweight_entries")
    suspend fun deleteAll()
}

@Dao
interface TrainingBlockDao {
    @Query("SELECT * FROM training_blocks WHERE isCurrent = 1 LIMIT 1")
    fun observeCurrent(): Flow<TrainingBlockEntity?>

    @Query("SELECT * FROM training_blocks WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrent(): TrainingBlockEntity?

    @Query("SELECT * FROM training_blocks WHERE isCurrent = 0 ORDER BY startEpochDay ASC")
    fun observePast(): Flow<List<TrainingBlockEntity>>

    @Query("SELECT * FROM training_blocks WHERE isCurrent = 0 ORDER BY startEpochDay ASC")
    suspend fun getPast(): List<TrainingBlockEntity>

    @Query("SELECT COUNT(*) FROM training_blocks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(block: TrainingBlockEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(blocks: List<TrainingBlockEntity>)

    @Query("DELETE FROM training_blocks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM training_blocks")
    suspend fun deleteAll()
}
