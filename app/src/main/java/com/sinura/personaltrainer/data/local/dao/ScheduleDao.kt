package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity

/**
 * The pinned cycle, as far as this phase needs it: counted for "is there anything here",
 * read and written wholesale for backup. The Plan tab adds the observe queries it needs when
 * it becomes the first thing that writes a slot.
 */
@Dao
interface ScheduleDao {
    @Query("SELECT COUNT(*) FROM schedule_slots")
    suspend fun count(): Int

    @Query("SELECT * FROM schedule_slots ORDER BY position ASC, id ASC")
    suspend fun getAll(): List<ScheduleSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(rows: List<ScheduleSlotEntity>)

    @Query("DELETE FROM schedule_slots")
    suspend fun deleteAll()
}
