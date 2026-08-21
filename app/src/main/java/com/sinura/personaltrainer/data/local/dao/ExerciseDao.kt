package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises ORDER BY id")
    suspend fun getAll(): List<ExerciseEntity>

    @Query(
        """
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%'
           OR muscleGroup LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        """,
    )
    fun search(query: String): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): ExerciseEntity?

    /**
     * The duplicate-name check, and the seeder's collision detection, both run through here.
     * The index behind it is plain, not unique — Room cannot declare the partial unique index
     * this would want (built-ins only), and an index Room does not know about fails schema
     * validation at open. So uniqueness is enforced above the database, and this is the query
     * that enforces it.
     */
    @Query("SELECT * FROM exercises WHERE nameKey = :nameKey LIMIT 1")
    suspend fun getByNameKey(nameKey: String): ExerciseEntity?

    /** For the detail screen, which must notice a rename or a deletion while it is open. */
    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(exercises: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(exercise: ExerciseEntity)

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Query("DELETE FROM exercises WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
