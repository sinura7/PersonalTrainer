package com.sinura.personaltrainer.data.repository

import com.sinura.personaltrainer.data.local.dao.CatalogDao
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseUsage
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The result of naming an exercise.
 *
 * A sealed result rather than a thrown exception or a silent overwrite: two lifts with the same
 * name is not an error the app can recover from on the user's behalf — the sets they log next
 * would go to whichever row a query happened to return first — but it is also not a crash. The
 * three screens that create or rename an exercise surface [DuplicateName] through the error
 * channel they already have.
 */
sealed class SaveExerciseResult {
    data class Saved(val exercise: Exercise) : SaveExerciseResult()
    data class DuplicateName(val existing: Exercise) : SaveExerciseResult()
}

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
    private val catalogDao: CatalogDao,
) {
    /**
     * The catalog, with each lift's junction credits attached.
     *
     * Two flows combined rather than a Room `@Relation`: the junction is small and read whole on
     * every heat recomputation, and a relation query would fan out to one lookup per exercise
     * every time either table changed.
     */
    fun observeAll(): Flow<List<Exercise>> =
        combine(exerciseDao.observeAll(), catalogDao.observeAllCredits()) { rows, credits ->
            val byExercise = credits.groupByExercise()
            rows.map { it.toDomain(byExercise[it.id].orEmpty()) }
        }.orLogAndFallback("the exercise catalog", emptyList())

    fun search(query: String): Flow<List<Exercise>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            observeAll()
        } else {
            combine(exerciseDao.search(trimmed), catalogDao.observeAllCredits()) { rows, credits ->
                val byExercise = credits.groupByExercise()
                rows.map { it.toDomain(byExercise[it.id].orEmpty()) }
            }.orLogAndFallback("exercise search", emptyList())
        }
    }

    suspend fun getById(id: String): Exercise? =
        exerciseDao.getById(id)?.toDomain(catalogDao.creditsFor(id).toCredits())

    fun observeById(id: String): Flow<Exercise?> =
        combine(exerciseDao.observeById(id), catalogDao.observeAllCredits()) { row, credits ->
            row?.toDomain(credits.filter { it.exerciseId == id }.toCredits())
        }.orLogAndFallback("this exercise", null)

    /**
     * Refuses a name another exercise already answers to.
     *
     * The check is by `nameKey`, not by name, so "bench press" cannot be created alongside
     * "Bench Press" — they are the same lift as far as anyone reading a list is concerned, and
     * a library with both in it is one where you can never be sure which one your history is on.
     */
    suspend fun createCustom(
        name: String,
        muscleGroup: String,
        notes: String = "",
    ): SaveExerciseResult {
        val trimmedName = name.trim()
        val nameKey = MuscleNormalizer.nameKeyOf(trimmedName)
        exerciseDao.getByNameKey(nameKey)?.let { clash ->
            return SaveExerciseResult.DuplicateName(clash.toDomain())
        }
        val group = muscleGroup.trim().ifBlank { "Other" }
        val exercise = Exercise(
            id = "ex-custom-${UUID.randomUUID()}",
            name = trimmedName,
            muscleGroup = group,
            notes = notes.trim(),
            isCustom = true,
            muscles = MuscleNormalizer.deriveCredits(group),
        )
        exerciseDao.insert(exercise.toEntity())
        catalogDao.replaceCreditsFor(exercise.id, exercise.muscles.toRows(exercise.id))
        return SaveExerciseResult.Saved(exercise)
    }

    suspend fun updateCustom(
        id: String,
        name: String,
        muscleGroup: String,
        notes: String,
    ): SaveExerciseResult? {
        val existing = exerciseDao.getById(id) ?: return null
        if (!existing.isCustom) return null
        val trimmedName = name.trim()
        val nameKey = MuscleNormalizer.nameKeyOf(trimmedName)
        exerciseDao.getByNameKey(nameKey)?.takeIf { it.id != id }?.let { clash ->
            return SaveExerciseResult.DuplicateName(clash.toDomain())
        }
        val group = muscleGroup.trim().ifBlank { existing.muscleGroup }
        val updated = existing.copy(
            name = trimmedName,
            muscleGroup = group,
            notes = notes.trim(),
            nameKey = nameKey,
        )
        exerciseDao.update(updated)
        // Re-derive rather than leave the old credits: the user just told us what this lift
        // trains by changing its group, and a stale junction would keep heating the old muscle.
        val credits = MuscleNormalizer.deriveCredits(group)
        catalogDao.replaceCreditsFor(id, credits.toRows(id))
        return SaveExerciseResult.Saved(updated.toDomain(credits))
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

private fun List<ExerciseMuscleEntity>.groupByExercise(): Map<String, List<MuscleCredit>> =
    groupBy { it.exerciseId }.mapValues { (_, rows) -> rows.toCredits() }

private fun List<ExerciseMuscleEntity>.toCredits(): List<MuscleCredit> =
    // Primary first, then heaviest secondary: every reader that shows a lift's muscles wants
    // the one it is actually for at the top.
    sortedWith(compareByDescending<ExerciseMuscleEntity> { it.weight }.thenBy { it.muscleKey })
        .map { MuscleCredit(muscleKey = it.muscleKey, weight = it.weight) }

private fun List<MuscleCredit>.toRows(exerciseId: String): List<ExerciseMuscleEntity> = map {
    ExerciseMuscleEntity(exerciseId = exerciseId, muscleKey = it.muscleKey, weight = it.weight)
}
