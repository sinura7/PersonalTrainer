package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.Routine
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoutineRepository(
    private val routineDao: RoutineDao,
) {
    fun observeAll(): Flow<List<Routine>> = routineDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    fun observeById(id: String): Flow<Routine?> = routineDao.observeById(id).map { it?.toDomain() }

    suspend fun getById(id: String): Routine? = routineDao.getById(id)?.toDomain()

    suspend fun create(name: String, notes: String = ""): Routine {
        val now = System.currentTimeMillis()
        val routine = Routine(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Untitled routine" },
            notes = notes.trim(),
            createdAt = now,
            updatedAt = now,
            exercises = emptyList(),
        )
        routineDao.upsertRoutine(routine.toEntity())
        return routine
    }

    suspend fun updateDetails(id: String, name: String, notes: String) {
        val existing = routineDao.getById(id)?.routine ?: return
        routineDao.updateRoutine(
            existing.copy(
                name = name.trim().ifBlank { existing.name },
                notes = notes.trim(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(id: String) {
        routineDao.deleteRoutine(id)
    }

    suspend fun addExercise(
        routineId: String,
        exercise: Exercise,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ) {
        val nextOrder = routineDao.maxSortOrder(routineId) + 1
        routineDao.upsertRoutineExercise(
            RoutineExerciseEntity(
                id = UUID.randomUUID().toString(),
                routineId = routineId,
                exerciseId = exercise.id,
                sortOrder = nextOrder,
                targetSets = targetSets.coerceAtLeast(1),
                targetReps = targetReps.coerceAtLeast(1),
                targetWeightKg = targetWeightKg?.takeIf { it > 0.0 },
                restSeconds = restSeconds.coerceAtLeast(0),
            ),
        )
        touch(routineId)
    }

    suspend fun updateExercise(
        itemId: String,
        routineId: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
    ) {
        val current = routineDao.getById(routineId)?.items?.firstOrNull { it.item.id == itemId } ?: return
        routineDao.upsertRoutineExercise(
            current.item.copy(
                targetSets = targetSets.coerceAtLeast(1),
                targetReps = targetReps.coerceAtLeast(1),
                targetWeightKg = targetWeightKg?.takeIf { it > 0.0 },
                restSeconds = restSeconds.coerceAtLeast(0),
            ),
        )
        touch(routineId)
    }

    suspend fun removeExercise(itemId: String, routineId: String) {
        routineDao.deleteRoutineExercise(itemId)
        touch(routineId)
    }

    suspend fun moveExercise(routineId: String, itemId: String, direction: Int) {
        val items = routineDao.getById(routineId)
            ?.items
            ?.sortedBy { it.item.sortOrder }
            .orEmpty()
        val index = items.indexOfFirst { it.item.id == itemId }
        val target = index + direction
        if (index < 0 || target !in items.indices) return
        val first = items[index].item
        val second = items[target].item
        routineDao.updateSortOrder(first.id, second.sortOrder)
        routineDao.updateSortOrder(second.id, first.sortOrder)
        touch(routineId)
    }

    private suspend fun touch(routineId: String) {
        val existing = routineDao.getById(routineId)?.routine ?: return
        routineDao.updateRoutine(existing.copy(updatedAt = System.currentTimeMillis()))
    }
}
