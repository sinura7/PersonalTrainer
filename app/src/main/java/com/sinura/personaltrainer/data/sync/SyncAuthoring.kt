package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.BodyweightDao
import com.sinura.personaltrainer.data.local.dao.CatalogDao
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.PlannerDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.domain.AccountAuthPort
import com.sinura.personaltrainer.domain.ActivityStatus
import kotlinx.coroutines.flow.first

/** Enqueues outbox rows after local commits when Temper Account is signed in. */
class SyncAuthoring(
    private val auth: AccountAuthPort,
    private val outbox: SyncOutboxWriter,
    private val activityDao: ActivityDao,
    private val plannerDao: PlannerDao,
    private val routineDao: RoutineDao,
    private val exerciseDao: ExerciseDao,
    private val catalogDao: CatalogDao,
    private val bodyweightDao: BodyweightDao,
    private val requestSync: () -> Unit,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun onActivitySessionCommitted(sessionId: String, status: ActivityStatus) {
        if (status == ActivityStatus.ACTIVE) return
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueCompletedSession(activityDao, sessionId, userId)
        requestSync()
    }

    suspend fun onActivityDeleted(sessionId: String, revision: Long, updatedAtMs: Long) {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueSessionDelete(
            userId = userId,
            sessionId = sessionId,
            revision = revision,
            updatedAtMs = updatedAtMs,
            deletedAtMs = nowMillis(),
        )
        requestSync()
    }

    suspend fun onScheduleChanged() {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueScheduleRules(userId, plannerDao.getRules())
        outbox.enqueueScheduleOccurrences(userId, plannerDao.getAllOccurrences())
        requestSync()
    }

    suspend fun onRoutinesChanged() {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueAllRoutines(
            userId = userId,
            routines = routineDao.getAllRoutines(),
            exercises = routineDao.getAllRoutineExercises(),
        )
        requestSync()
    }

    suspend fun onRoutineDeleted(beforeDelete: RoutineWithExercises?) {
        val userId = auth.session.first()?.userId ?: return
        val deletedAtMs = nowMillis()
        val routine = beforeDelete?.routine ?: return
        beforeDelete.items.forEach { row ->
            outbox.enqueueRoutineExerciseDelete(
                userId = userId,
                item = row.item,
                updatedAtMs = routine.updatedAt,
                deletedAtMs = deletedAtMs,
            )
        }
        outbox.enqueueRoutineDelete(userId, routine, deletedAtMs)
        requestSync()
    }

    suspend fun onRoutineExerciseRemoved(item: RoutineExerciseEntity, routineUpdatedAtMs: Long) {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueRoutineExerciseDelete(
            userId = userId,
            item = item,
            updatedAtMs = routineUpdatedAtMs,
            deletedAtMs = nowMillis(),
        )
        requestSync()
    }

    suspend fun onTemplatesChanged() {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueAllTemplates(userId, activityDao.getAllTemplateGraphs())
        requestSync()
    }

    suspend fun onCustomExerciseCommitted(exercise: ExerciseEntity, muscles: List<ExerciseMuscleEntity>) {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueCustomExerciseUpsert(
            userId = userId,
            exercise = exercise,
            muscles = muscles,
            createdAtMs = exercise.updatedAtMs,
        )
        requestSync()
    }

    suspend fun onCustomExerciseDeleted(exercise: ExerciseEntity, muscles: List<ExerciseMuscleEntity>) {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueCustomExerciseDelete(
            userId = userId,
            exercise = exercise,
            muscles = muscles,
            deletedAtMs = nowMillis(),
        )
        requestSync()
    }

    suspend fun onCustomExercisesChanged() {
        val userId = auth.session.first()?.userId ?: return
        val customs = exerciseDao.getAllCustom()
        val customIds = customs.map { it.id }.toSet()
        val muscles = catalogDao.getAllCredits().filter { it.exerciseId in customIds }
        outbox.enqueueAllCustomExercises(userId, customs, muscles)
        requestSync()
    }

    suspend fun onBodyweightChanged() {
        val userId = auth.session.first()?.userId ?: return
        outbox.enqueueAllBodyweightEntries(userId, bodyweightDao.getAll())
        requestSync()
    }

    /** After sign-in, queue custom lifts and weigh-ins that may predate the account. */
    suspend fun bootstrapLocalSnapshot() {
        val userId = auth.session.first()?.userId ?: return
        val customs = exerciseDao.getAllCustom()
        val customIds = customs.map { it.id }.toSet()
        val muscles = catalogDao.getAllCredits().filter { it.exerciseId in customIds }
        outbox.enqueueAllCustomExercises(userId, customs, muscles)
        outbox.enqueueAllBodyweightEntries(userId, bodyweightDao.getAll())
        requestSync()
    }
}
