package com.sinura.personaltrainer.testutil

import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred

/** Holds the real catalog transaction; release before asking Room for another read. */
class GatedPickerCreation(deps: FakeAppDependencies) {
    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    val inserts = AtomicInteger()
    private val dao = object : ExerciseDao by deps.database.exerciseDao() {
        override suspend fun insert(exercise: ExerciseEntity) {
            inserts.incrementAndGet()
            entered.complete(Unit)
            release.await()
            deps.database.exerciseDao().insert(exercise)
        }
    }
    val container: AppDependencies = object : AppDependencies by deps {
        override val exerciseRepository = ExerciseRepository(
            exerciseDao = dao,
            routineDao = deps.database.routineDao(),
            workoutDao = deps.database.workoutDao(),
            catalogDao = deps.database.catalogDao(),
            database = deps.database,
        )
    }
}
