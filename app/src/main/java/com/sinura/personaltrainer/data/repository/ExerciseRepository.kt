package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.AppRoomDatabase
import com.sinura.personaltrainer.data.local.dao.CatalogDao
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.domain.CatalogMeta
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.ExerciseOrdering
import com.sinura.personaltrainer.domain.ExerciseUsage
import com.sinura.personaltrainer.domain.LikeEscaper
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleGroups
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
 * three screens that create or rename an exercise surface [DuplicateName] and
 * [MissingMuscle] through the error channel they already have.
 */
sealed class SaveExerciseResult {
    data class Saved(val exercise: Exercise) : SaveExerciseResult()
    data class DuplicateName(val existing: Exercise) : SaveExerciseResult()
    data object MissingMuscle : SaveExerciseResult()
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
    private val database: AppRoomDatabase? = null,
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
        }.observeHealth("the exercise catalog").presentValues()

    /**
     * Name, muscle group, or nickname.
     *
     * Two things happen here that the DAO cannot do alone. The query is escaped, because `%`
     * and `_` are LIKE wildcards and a lifter typing `100%` meant it literally. And the SQL
     * hits are unioned with alias hits from [CatalogMeta], because nobody types "Overhead
     * Press" when they mean OHP and no substring of the stored name will ever match it.
     *
     * The alias pass reads the whole catalog, which is fine at 98 rows and would not be at
     * 10,000 — at that point the terms belong in an FTS table. Said here so the decision is
     * visible rather than discovered.
     */
    fun search(query: String): Flow<List<Exercise>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            observeAll()
        } else {
            combine(
                exerciseDao.search(LikeEscaper.escape(trimmed)),
                exerciseDao.observeAll(),
                catalogDao.observeAllCredits(),
            ) { likeHits, all, credits ->
                val byExercise = credits.groupByExercise()
                val aliasHits = all.filter { CatalogMeta.matchesSearchTerms(trimmed, it.id) }
                (likeHits + aliasHits)
                    .distinctBy { it.id }
                    .map { it.toDomain(byExercise[it.id].orEmpty()) }
                    .let(ExerciseOrdering::catalogOrder)
            }.observeHealth("exercise search").presentValues()
        }
    }

    /**
     * Customs sharing a name with a built-in, for the Library's "needs attention" section.
     *
     * Derived on every read rather than stored: a stored flag would have to be cleared when the
     * custom is renamed, and a flag nobody remembers to clear is worse than no flag.
     */
    fun observeNameCollisions(): Flow<List<Exercise>> =
        combine(
            exerciseDao.observeBuiltInCollisions(),
            catalogDao.observeAllCredits(),
        ) { rows, credits ->
            val byExercise = credits.groupByExercise()
            rows.map { it.toDomain(byExercise[it.id].orEmpty()) }
        }.observeHealth("name collisions").presentValues()

    /** When each lift was last logged, for the picker's recency order. */
    fun observeLastLogged(): Flow<Map<String, Long>> =
        workoutDao.observeLastLogged()
            .map { rows -> rows.associate { it.exerciseId to it.lastLoggedAt } }
            .observeHealth("recent lifts").presentValues()

    suspend fun getById(id: String): Exercise? =
        exerciseDao.getById(id)?.toDomain(catalogDao.creditsFor(id).toCredits())

    fun observeById(id: String): Flow<Exercise?> =
        combine(exerciseDao.observeById(id), catalogDao.observeAllCredits()) { row, credits ->
            row?.toDomain(credits.filter { it.exerciseId == id }.toCredits())
        }.observeHealth("this exercise").presentValues()

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
        val group = MuscleGroups.resolved(muscleGroup)
            ?: return SaveExerciseResult.MissingMuscle
        return writeExercise {
            exerciseDao.getByNameKey(nameKey)?.let { clash ->
                return@writeExercise SaveExerciseResult.DuplicateName(clash.toDomain())
            }
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
            SaveExerciseResult.Saved(exercise)
        }
    }

    suspend fun updateCustom(
        id: String,
        name: String,
        muscleGroup: String,
        notes: String,
    ): SaveExerciseResult? {
        val trimmedName = name.trim()
        val nameKey = MuscleNormalizer.nameKeyOf(trimmedName)
        return writeExercise {
            val existing = exerciseDao.getById(id) ?: return@writeExercise null
            if (!existing.isCustom) return@writeExercise null
            exerciseDao.getByNameKey(nameKey)?.takeIf { it.id != id }?.let { clash ->
                return@writeExercise SaveExerciseResult.DuplicateName(clash.toDomain())
            }
            val group = muscleGroup.trim().ifBlank { existing.muscleGroup }
            val updated = existing.copy(
                name = trimmedName,
                muscleGroup = group,
                notes = notes.trim(),
                nameKey = nameKey,
            )
            exerciseDao.update(updated)
            val credits = MuscleNormalizer.deriveCredits(group)
            catalogDao.replaceCreditsFor(id, credits.toRows(id))
            SaveExerciseResult.Saved(updated.toDomain(credits))
        }
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

    private suspend fun <T> writeExercise(block: suspend () -> T): T {
        val db = database
        return if (db != null) db.withTransaction { block() } else block()
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
