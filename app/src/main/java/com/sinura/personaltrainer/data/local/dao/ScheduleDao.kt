package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import kotlinx.coroutines.flow.Flow

/**
 * The pinned cycle.
 *
 * Ordered by `position` everywhere it is read: position IS the cycle, and a list returned in
 * insertion order would silently mean a different week.
 */
@Dao
interface ScheduleDao {
    @Query("SELECT COUNT(*) FROM schedule_slots")
    suspend fun count(): Int

    @Query("SELECT * FROM schedule_slots ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<ScheduleSlotEntity>>

    @Query("SELECT MAX(position) FROM schedule_slots")
    suspend fun maxPosition(): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(slot: ScheduleSlotEntity)

    @Update
    suspend fun update(slot: ScheduleSlotEntity)

    @Query("SELECT * FROM schedule_slots WHERE id = :id")
    suspend fun getById(id: String): ScheduleSlotEntity?

    @Query("DELETE FROM schedule_slots WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Accepting a suggested week is one act, so it is one transaction. */
    @Transaction
    suspend fun insertAll(slots: List<ScheduleSlotEntity>) {
        if (slots.isNotEmpty()) replaceAll(slots)
    }

    @Query("SELECT * FROM schedule_slots ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<ScheduleSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(rows: List<ScheduleSlotEntity>)

    @Query("DELETE FROM schedule_slots")
    suspend fun deleteAll()
}
