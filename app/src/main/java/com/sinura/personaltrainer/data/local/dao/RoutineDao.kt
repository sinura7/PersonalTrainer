package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Transaction
    @Query("SELECT * FROM routines ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<RoutineWithExercises>>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    fun observeById(id: String): Flow<RoutineWithExercises?>

    @Transaction
    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): RoutineWithExercises?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutineExercise(item: RoutineExerciseEntity)

    @Query("DELETE FROM routine_exercises WHERE id = :id")
    suspend fun deleteRoutineExercise(id: String)

    @Query("SELECT COALESCE(MAX(sort_order), -1) FROM routine_exercises WHERE routineId = :routineId")
    suspend fun maxSortOrder(routineId: String): Int

    @Query("UPDATE routine_exercises SET sort_order = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Int)

    @Query("SELECT COUNT(*) FROM routine_exercises WHERE exerciseId = :exerciseId")
    suspend fun countForExercise(exerciseId: String): Int

    @Query("SELECT * FROM routines ORDER BY id")
    suspend fun getAllRoutines(): List<RoutineEntity>

    @Query("SELECT * FROM routine_exercises ORDER BY id")
    suspend fun getAllRoutineExercises(): List<RoutineExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceRoutines(items: List<RoutineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceRoutineExercises(items: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises")
    suspend fun deleteAllRoutineExercises()

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines()
}
