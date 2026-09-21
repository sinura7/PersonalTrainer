package com.sinura.personaltrainer.data.sync

import com.sinura.personaltrainer.data.local.dao.ActivityDao
import com.sinura.personaltrainer.data.local.dao.PlannerDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
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
}
