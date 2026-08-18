package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseUsage
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed class DeleteExerciseResult {
    data object Deleted : DeleteExerciseResult()
    data class InUse(val usage: ExerciseUsage) : DeleteExerciseResult()
    data object NotCustom : DeleteExerciseResult()
    data object Missing : DeleteExerciseResult()
}

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val routineDao: RoutineDao,
    private val workoutDao: WorkoutDao,
) {
    fun observeAll(): Flow<List<Exercise>> = exerciseDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    fun search(query: String): Flow<List<Exercise>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            observeAll()
        } else {
            exerciseDao.search(trimmed).map { list -> list.map { it.toDomain() } }
        }
    }

    suspend fun getById(id: String): Exercise? = exerciseDao.getById(id)?.toDomain()

    suspend fun seedDefaultsIfEmpty() {
        if (exerciseDao.count() == 0) {
            exerciseDao.insertAll(DefaultExercises.catalog().map { it.toEntity() })
        }
    }

    suspend fun createCustom(name: String, muscleGroup: String, notes: String = ""): Exercise {
        val exercise = Exercise(
            id = "ex-custom-${UUID.randomUUID()}",
            name = name.trim(),
            muscleGroup = muscleGroup.trim().ifBlank { "Other" },
            notes = notes.trim(),
            isCustom = true,
        )
        exerciseDao.insert(exercise.toEntity())
        return exercise
    }

    suspend fun updateCustom(id: String, name: String, muscleGroup: String, notes: String): Exercise? {
        val existing = exerciseDao.getById(id) ?: return null
        if (!existing.isCustom) return null
        val updated = existing.copy(
            name = name.trim(),
            muscleGroup = muscleGroup.trim().ifBlank { existing.muscleGroup },
            notes = notes.trim(),
        )
        exerciseDao.update(updated)
        return updated.toDomain()
    }

    suspend fun usageFor(exerciseId: String): ExerciseUsage = ExerciseUsage(
        routineCount = routineDao.countForExercise(exerciseId),
        historySetCount = workoutDao.countSetsForExercise(exerciseId),
        sessionCount = workoutDao.countSessionExercisesFor(exerciseId),
    )

    suspend fun deleteCustom(id: String): DeleteExerciseResult {
        val existing = exerciseDao.getById(id) ?: return DeleteExerciseResult.Missing
        if (!existing.isCustom) return DeleteExerciseResult.NotCustom
        val usage = usageFor(id)
        if (usage.isReferenced) return DeleteExerciseResult.InUse(usage)
        exerciseDao.deleteCustom(id)
        return DeleteExerciseResult.Deleted
    }
}
