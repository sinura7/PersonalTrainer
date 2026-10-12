package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.mapper.toDomain
import com.sinura.personaltrainer.data.mapper.toEntity
import com.sinura.personaltrainer.data.sync.SyncAuthoring
import com.sinura.personaltrainer.domain.AuxiliaryPack
import com.sinura.personaltrainer.domain.DataHealth
import com.sinura.personaltrainer.domain.Exercise
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.Routine
import com.sinura.personaltrainer.domain.TargetDefaults
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoutineRepository(
    private val routineDao: RoutineDao,
    private val database: TemperDatabase? = null,
    private val planner: PlannerRepository? = null,
    private val syncAuthoring: SyncAuthoring? = null,
) {
    private suspend fun notifyRoutinesSync() {
        syncAuthoring?.onRoutinesChanged()
    }
    fun observeAllHealth(): Flow<DataHealth<List<Routine>>> = routineDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }.observeHealth("routines")

    fun observeAll(): Flow<List<Routine>> = observeAllHealth().presentValues()

    fun observeById(id: String): Flow<Routine?> = routineDao.observeById(id).map { it?.toDomain() }
        .observeHealth("a routine").presentValues()

    suspend fun getById(id: String): Routine? = routineDao.getById(id)?.toDomain()

    /** How many routines exist. Cheaper than loading them all to ask whether any do. */
    suspend fun count(): Int = routineDao.getAllRoutines().size

    /** Prepare the available, ordered pack once; a failed child write leaves no partial routine. */
    internal suspend fun ensureAuxiliaryRoutine(
        pack: AuxiliaryPack,
        exercises: ExerciseRepository,
    ): String {
        val db = checkNotNull(database) { "Preparing an Extra requires a database transaction." }
        val prepared = db.withTransaction {
            val available = pack.lifts.mapNotNull { lift ->
                exercises.getById(id = lift.exerciseId)?.let { exercise ->
                    lift to exercise
                }
            }.distinctBy { (_, exercise) -> exercise.id }
            check(available.isNotEmpty()) { "No lifts from this Extra are available." }
            val wanted = available.map { (_, exercise) -> exercise.id }
            val name = pack.title.trim().ifBlank { "Untitled routine" }
            val existing = routineDao.getAllRoutines()
                .sortedByDescending { it.updatedAt }
                .firstOrNull { routine ->
                    routine.name.equals(other = name, ignoreCase = true) &&
                        routineDao.getById(id = routine.id)?.items
                            ?.sortedBy { it.item.sortOrder }
                            ?.map { it.exercise.id } == wanted
                }
            if (existing != null) {
                return@withTransaction AuxiliaryRoutinePreparation(id = existing.id, created = false)
            }
            val now = System.currentTimeMillis()
            val routine = RoutineEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                notes = pack.caption.trim(),
                createdAt = now,
                updatedAt = now,
            )
            routineDao.upsertRoutine(routine = routine)
            available.forEachIndexed { index, (lift, exercise) ->
                val hold = HoldWork.isHold(exercise)
                routineDao.upsertRoutineExercise(
                    item = RoutineExerciseEntity(
                        id = UUID.randomUUID().toString(),
                        routineId = routine.id,
                        exerciseId = exercise.id,
                        sortOrder = index,
                        targetSets = lift.sets.coerceAtLeast(1),
                        targetReps = if (hold) HoldWork.HOLD_REPS_PLACEHOLDER else lift.reps.coerceAtLeast(1),
                        targetWeightKg = null,
                        restSeconds = lift.restSeconds.coerceAtLeast(0),
                        targetSeconds = if (hold) HoldWork.countdownSeconds(targetSeconds = lift.reps) else null,
                        targetSecondsMax = null,
                    ),
                )
            }
            AuxiliaryRoutinePreparation(id = routine.id, created = true)
        }
        if (prepared.created) notifyRoutinesSync()
        return prepared.id
    }

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
        notifyRoutinesSync()
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
        notifyRoutinesSync()
    }

    suspend fun delete(id: String) {
        val beforeDelete = routineDao.getById(id)
        syncAuthoring?.onRoutineDeleted(beforeDelete)
        val db = database
        val plannerRepo = planner
        if (db != null && plannerRepo != null) {
            db.withTransaction {
                plannerRepo.onRoutineDeleted(id)
                routineDao.deleteRoutine(id)
            }
        } else {
            routineDao.deleteRoutine(id)
        }
    }

    suspend fun addExercise(
        routineId: String,
        exercise: Exercise,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
        targetSeconds: Int? = null,
        targetSecondsMax: Int? = null,
    ) {
        addExerciseAdmitted(
            routineId = routineId, exercise = exercise,
            targets = AddTargets(
                sets = targetSets, reps = targetReps, weightKg = targetWeightKg, restSeconds = restSeconds,
                seconds = targetSeconds, secondsMax = targetSecondsMax,
            ),
            admit = { true },
        )
    }

    /** Library read ownership is checked after transaction acquisition and destination reads. */
    internal suspend fun addLibraryExercise(
        routineId: String,
        exercise: Exercise,
        defaults: TargetDefaults,
        admit: () -> Boolean,
    ): Boolean = addExerciseAdmitted(
        routineId = routineId, exercise = exercise,
        targets = AddTargets(sets = defaults.sets, reps = defaults.reps, weightKg = null, restSeconds = defaults.restSeconds),
        admit = admit,
    )

    private suspend fun addExerciseAdmitted(
        routineId: String,
        exercise: Exercise,
        targets: AddTargets,
        admit: () -> Boolean,
    ): Boolean = writeRoutine {
        val existing = routineDao.getById(routineId)
        if (existing?.items?.any { it.exercise.id == exercise.id } == true) return@writeRoutine admit()
        val nextOrder = routineDao.maxSortOrder(routineId) + 1
        if (!admit()) return@writeRoutine false
        val hold = HoldWork.isHold(exercise)
        routineDao.upsertRoutineExercise(
            RoutineExerciseEntity(
                id = UUID.randomUUID().toString(), routineId = routineId, exerciseId = exercise.id, sortOrder = nextOrder,
                targetSets = targets.sets.coerceAtLeast(1),
                targetReps = if (hold) HoldWork.HOLD_REPS_PLACEHOLDER else targets.reps.coerceAtLeast(1),
                targetWeightKg = targets.weightKg?.takeIf { it > 0.0 }, restSeconds = targets.restSeconds.coerceAtLeast(0),
                targetSeconds = if (hold) HoldWork.countdownSeconds(targets.seconds) else null,
                targetSecondsMax = if (hold) targets.secondsMax else null,
            ),
        )
        touch(routineId)
        notifyRoutinesSync()
        true
    }

    private data class AddTargets(
        val sets: Int,
        val reps: Int,
        val weightKg: Double?,
        val restSeconds: Int,
        val seconds: Int? = null,
        val secondsMax: Int? = null,
    )

    /**
     * Replaces one lift in a routine with another, keeping everything about its place.
     *
     * Its id, its position and its targets all stay, because the plan is "third lift, three
     * sets of eight, ninety seconds' rest" and only the implement changed. Remove-then-add
     * would have lost the position and reset the targets, which is why nobody used it.
     *
     * `targetWeightKg` is the one thing deliberately dropped: a weight chosen for a barbell is
     * not a starting point on a machine, and the progression prefill will suggest a real number
     * from that lift's own history.
     *
     * Returns a message when it refuses, so the editor can say why rather than doing nothing.
     */
    suspend fun swapExercise(routineId: String, itemId: String, replacement: Exercise): String? {
        val routine = routineDao.getById(routineId) ?: return "That routine is no longer available."
        val current = routine.items.firstOrNull { it.item.id == itemId }
            ?: return "That lift is no longer in this routine."
        if (routine.items.any { it.exercise.id == replacement.id }) {
            return "${replacement.name} is already in this routine."
        }
        routineDao.upsertRoutineExercise(current.item.copy(exerciseId = replacement.id, targetWeightKg = null))
        touch(routineId)
        notifyRoutinesSync()
        return null
    }

    suspend fun updateExercise(
        itemId: String,
        routineId: String,
        targetSets: Int,
        targetReps: Int,
        targetWeightKg: Double?,
        restSeconds: Int,
        targetSeconds: Int? = null,
        targetSecondsMax: Int? = null,
    ) {
        val current = routineDao.getById(routineId)?.items?.firstOrNull { it.item.id == itemId } ?: return
        val hold = current.item.targetSeconds != null || targetSeconds != null
        routineDao.upsertRoutineExercise(
            current.item.copy(
                targetSets = targetSets.coerceAtLeast(1),
                targetReps = if (hold) HoldWork.HOLD_REPS_PLACEHOLDER else targetReps.coerceAtLeast(1),
                targetWeightKg = targetWeightKg?.takeIf { it > 0.0 },
                restSeconds = restSeconds.coerceAtLeast(0),
                targetSeconds = if (hold) {
                    HoldWork.countdownSeconds(targetSeconds ?: current.item.targetSeconds)
                } else {
                    null
                },
                targetSecondsMax = if (hold) targetSecondsMax else null,
            ),
        )
        touch(routineId)
        notifyRoutinesSync()
    }

    suspend fun removeExercise(itemId: String, routineId: String) {
        val current = routineDao.getById(routineId)
        val removed = current?.items?.firstOrNull { it.item.id == itemId }?.item
        routineDao.deleteRoutineExercise(itemId)
        touch(routineId)
        val routineUpdatedAt = routineDao.getById(routineId)?.routine?.updatedAt ?: System.currentTimeMillis()
        if (removed != null) {
            syncAuthoring?.onRoutineExerciseRemoved(removed, routineUpdatedAt)
        }
        notifyRoutinesSync()
    }

    suspend fun moveExercise(routineId: String, itemId: String, direction: Int) {
        writeRoutine {
            val items = routineDao.getById(routineId)
                ?.items
                ?.sortedBy { it.item.sortOrder }
                .orEmpty()
            val index = items.indexOfFirst { it.item.id == itemId }
            val target = index + direction
            if (index < 0 || target !in items.indices) return@writeRoutine
            val first = items[index].item
            val second = items[target].item
            routineDao.updateSortOrder(first.id, second.sortOrder)
            routineDao.updateSortOrder(second.id, first.sortOrder)
            touch(routineId)
            notifyRoutinesSync()
        }
    }

    private suspend fun <T> writeRoutine(block: suspend () -> T): T {
        val db = database
        return if (db != null) db.withTransaction { block() } else block()
    }

    private suspend fun touch(routineId: String) {
        val existing = routineDao.getById(routineId)?.routine ?: return
        routineDao.updateRoutine(existing.copy(updatedAt = System.currentTimeMillis()))
    }

    private data class AuxiliaryRoutinePreparation(val id: String, val created: Boolean)
}
