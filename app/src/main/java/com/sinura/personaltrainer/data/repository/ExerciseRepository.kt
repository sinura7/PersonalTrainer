package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.Exercise
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
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
}
