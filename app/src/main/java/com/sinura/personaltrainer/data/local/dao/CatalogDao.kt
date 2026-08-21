package com.sinura.personaltrainer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.SeedMetaEntity
import kotlinx.coroutines.flow.Flow

/**
 * The muscle junction and the seeder's own bookkeeping.
 *
 * Credits are observed as one flat list rather than per exercise: the body map needs the whole
 * junction on every recomputation, and a query per lift would be one round trip per row in the
 * library on every heat refresh.
 */
@Dao
interface CatalogDao {
    @Query("SELECT * FROM exercise_muscles")
    fun observeAllCredits(): Flow<List<ExerciseMuscleEntity>>

    @Query("SELECT * FROM exercise_muscles")
    suspend fun getAllCredits(): List<ExerciseMuscleEntity>

    @Query("SELECT * FROM exercise_muscles WHERE exerciseId = :exerciseId")
    suspend fun creditsFor(exerciseId: String): List<ExerciseMuscleEntity>

    @Query("SELECT DISTINCT exerciseId FROM exercise_muscles")
    suspend fun exerciseIdsWithCredits(): List<String>

    @Query("DELETE FROM exercise_muscles WHERE exerciseId = :exerciseId")
    suspend fun deleteCreditsFor(exerciseId: String)

    /**
     * Delete-then-insert rather than upsert: a lift whose secondaries were trimmed must lose
     * the rows it no longer has, and an upsert can only ever add or overwrite.
     */
    @Transaction
    suspend fun replaceCreditsFor(exerciseId: String, rows: List<ExerciseMuscleEntity>) {
        deleteCreditsFor(exerciseId)
        if (rows.isNotEmpty()) insertCredits(rows)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredits(rows: List<ExerciseMuscleEntity>)

    @Query("DELETE FROM exercise_muscles")
    suspend fun deleteAllCredits()

    @Query("SELECT * FROM seed_meta WHERE id = 1")
    suspend fun getSeedMeta(): SeedMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSeedMeta(meta: SeedMetaEntity)
}
