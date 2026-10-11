package com.sinura.personaltrainer.data.repository

import android.app.Application
import android.database.sqlite.SQLiteFullException
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAccountAuth
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.sync.SyncAuthoring
import com.sinura.personaltrainer.data.sync.SyncOutboxWriter
import com.sinura.personaltrainer.domain.AccountSession
import com.sinura.personaltrainer.domain.AuxiliaryPack
import com.sinura.personaltrainer.domain.AuxiliaryPacks
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.domain.SyncEntityType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Extra preparation owns one complete routine, including after a failed write or a retry. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AuxiliaryRoutineAtomicTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun secondChildFailureRollsBackInventoryAndRetryCommitsOneCompleteRoutineBeforeSync() = runBlocking {
        val pack = AuxiliaryPacks.GolfNone
        seedCatalogIds(ids = pack.lifts.map { it.exerciseId })
        val unrelated = deps.routineRepository.create(name = "Already saved", notes = "Keep this routine")
        attachPack(routineId = unrelated.id, pack = pack.copy(lifts = pack.lifts.take(1)))
        val dao = deps.database.routineDao()
        val parentsBefore = dao.getAllRoutines()
        val childrenBefore = dao.getAllRoutineExercises()
        val childWrites = AtomicInteger()
        val syncRequests = AtomicInteger()
        val faultDao = object : RoutineDao by dao {
            override suspend fun upsertRoutineExercise(item: RoutineExerciseEntity) {
                if (childWrites.incrementAndGet() == 2) {
                    // The parent and first child have really reached Room, inside the transaction.
                    assertEquals(1, dao.getById(id = item.routineId)!!.items.size)
                    throw SQLiteFullException("Injected second Extra child write failure")
                }
                dao.upsertRoutineExercise(item = item)
            }
        }
        val repository = RoutineRepository(
            routineDao = faultDao,
            database = deps.database,
            syncAuthoring = authoring(requestSync = {
                assertFalse(deps.database.inTransaction())
                syncRequests.incrementAndGet()
            }),
        )

        val failed = runCatching { ensure(pack = pack, routines = repository) }

        assertTrue(failed.exceptionOrNull() is SQLiteFullException)
        assertEquals(2, childWrites.get())
        assertEquals(parentsBefore, dao.getAllRoutines())
        assertEquals(childrenBefore, dao.getAllRoutineExercises())
        assertEquals(0, deps.database.syncDao().pendingCount())
        assertEquals(0, syncRequests.get())

        val savedId = ensure(pack = pack, routines = repository)
        val saved = repository.getById(id = savedId)!!
        assertEquals(pack.lifts.map { it.exerciseId }, saved.exercises.map { it.exercise.id })
        assertEquals(pack.lifts.indices.toList(), saved.exercises.map { it.sortOrder })
        assertEquals(parentsBefore.size + 1, dao.getAllRoutines().size)
        assertEquals(childrenBefore.size + pack.lifts.size, dao.getAllRoutineExercises().size)
        assertEquals(1, syncRequests.get())
        val outbox = deps.database.syncDao().peekOutbox(limit = 100)
        val savedItemIds = saved.exercises.map { it.id }.toSet()
        assertEquals(
            savedItemIds,
            outbox.filter {
                it.entityType == SyncEntityType.ROUTINE_EXERCISE.name && it.entityId in savedItemIds
            }.map { it.entityId }.toSet(),
        )
        val parentsCommitted = dao.getAllRoutines()
        val childrenCommitted = dao.getAllRoutineExercises()
        assertEquals(savedId, ensure(pack = pack, routines = repository))
        assertEquals(parentsCommitted, dao.getAllRoutines())
        assertEquals(childrenCommitted, dao.getAllRoutineExercises())
        assertEquals(1, syncRequests.get())
    }

    @Test
    fun concurrentEnsuresReturnOneCompleteCanonicalRoutine() = runBlocking {
        val pack = AuxiliaryPacks.GolfNone
        seedCatalogIds(ids = pack.lifts.map { it.exerciseId })
        val begin = CompletableDeferred<Unit>()
        val allReady = CompletableDeferred<Unit>()
        val readyCallers = AtomicInteger()
        val ids = withTimeout(timeMillis = 10_000L) {
            val callers = List(size = 8) {
                async(context = Dispatchers.IO) {
                    if (readyCallers.incrementAndGet() == 8) allReady.complete(value = Unit)
                    begin.await()
                    ensure(pack = pack)
                }
            }
            allReady.await()
            begin.complete(value = Unit)
            callers.awaitAll()
        }

        assertEquals(1, ids.toSet().size)
        assertEquals(listOf(ids.first()), deps.database.routineDao().getAllRoutines().map { it.id })
        val saved = deps.routineRepository.getById(id = ids.first())!!
        assertEquals(pack.lifts.map { it.exerciseId }, saved.exercises.map { it.exercise.id })
        assertEquals(pack.lifts.indices.toList(), saved.exercises.map { it.sortOrder })
        assertEquals(pack.lifts.size, deps.database.routineDao().getAllRoutineExercises().size)
    }

    @Test
    fun shortenedCatalogReusesEffectiveOrderAndKeepsTheFirstDuplicatePrescription() = runBlocking {
        val original = AuxiliaryPacks.GolfNone
        val availableLifts = listOf(original.lifts[1], original.lifts[3])
        val pack = original.copy(lifts = original.lifts + original.lifts[1].copy(sets = 9, reps = 99))
        seedCatalogIds(ids = availableLifts.map { it.exerciseId })

        val savedId = ensure(pack = pack)
        val saved = deps.routineRepository.getById(id = savedId)!!
        assertEquals(availableLifts.map { it.exerciseId }, saved.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 1), saved.exercises.map { it.sortOrder })
        assertEquals(availableLifts.map { it.sets }, saved.exercises.map { it.targetSets })
        assertEquals(availableLifts.map { it.reps }, saved.exercises.map { it.targetReps })
        val parentsBeforeRetry = deps.database.routineDao().getAllRoutines()
        val childrenBeforeRetry = deps.database.routineDao().getAllRoutineExercises()

        assertEquals(savedId, ensure(pack = pack))
        assertEquals(parentsBeforeRetry, deps.database.routineDao().getAllRoutines())
        assertEquals(childrenBeforeRetry, deps.database.routineDao().getAllRoutineExercises())
        assertEquals(1, parentsBeforeRetry.size)
        assertEquals(availableLifts.size, childrenBeforeRetry.size)
    }

    @Test
    fun allMissingLiftsRejectBeforeWritingOrReusingAnEmptyRoutine() = runBlocking {
        val pack = AuxiliaryPacks.GolfNone
        val empty = deps.routineRepository.create(name = pack.title, notes = "Existing empty draft")
        val parentsBefore = deps.database.routineDao().getAllRoutines()

        val failed = runCatching { ensure(pack = pack) }

        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertEquals(parentsBefore, deps.database.routineDao().getAllRoutines())
        assertEquals(emptyList<RoutineExerciseEntity>(), deps.database.routineDao().getAllRoutineExercises())
        assertEquals(emptyList<String>(), deps.routineRepository.getById(id = empty.id)!!.exercises.map { it.id })
    }

    @Test
    fun existingFullOrderedRoutineIsReusedWithoutChangingItsIdentityOrTargets() = runBlocking {
        val pack = AuxiliaryPacks.GolfNone
        seedCatalogIds(ids = pack.lifts.map { it.exerciseId })
        val partial = deps.routineRepository.create(name = pack.title)
        attachPack(routineId = partial.id, pack = pack.copy(lifts = pack.lifts.take(1)))
        val reversed = deps.routineRepository.create(name = pack.title)
        attachPack(routineId = reversed.id, pack = pack.copy(lifts = pack.lifts.reversed()))
        val complete = deps.routineRepository.create(name = pack.title.lowercase(), notes = "Already authored")
        attachPack(routineId = complete.id, pack = pack)
        val firstItem = deps.routineRepository.getById(id = complete.id)!!.exercises.first()
        deps.routineRepository.updateExercise(
            itemId = firstItem.id,
            routineId = complete.id,
            targetSets = 7,
            targetReps = 6,
            targetWeightKg = 12.5,
            restSeconds = 90,
        )
        val dao = deps.database.routineDao()
        val parentsBefore = dao.getAllRoutines()
        val childrenBefore = dao.getAllRoutineExercises()

        assertEquals(complete.id, ensure(pack = pack))
        assertEquals(parentsBefore, dao.getAllRoutines())
        assertEquals(childrenBefore, dao.getAllRoutineExercises())
    }

    @Test
    fun aNewPackPreservesRepAndHoldTargetsAndContiguousOrder() = runBlocking {
        val pack = AuxiliaryPacks.GolfNone
        seedCatalogIds(ids = pack.lifts.map { it.exerciseId })

        val savedId = ensure(pack = pack)
        val saved = deps.routineRepository.getById(id = savedId)!!

        assertEquals(pack.title, saved.name)
        assertEquals(pack.caption, saved.notes)
        assertEquals(saved.createdAt, saved.updatedAt)
        assertEquals(pack.lifts.map { it.exerciseId }, saved.exercises.map { it.exercise.id })
        assertEquals(listOf(0, 1, 2, 3), saved.exercises.map { it.sortOrder })
        assertEquals(listOf(2, 2, 2, 2), saved.exercises.map { it.targetSets })
        assertEquals(listOf(8, 8, 1, 10), saved.exercises.map { it.targetReps })
        assertEquals(listOf(null, null, 20, null), saved.exercises.map { it.targetSeconds })
        assertEquals(listOf(20, 20, 20, 20), saved.exercises.map { it.restSeconds })
        assertEquals(4, saved.exercises.map { it.id }.toSet().size)
        saved.exercises.forEach { row ->
            assertEquals(savedId, row.routineId)
            assertNull(row.targetWeightKg)
            assertNull(row.targetSecondsMax)
        }
    }

    @Test
    fun aRepositoryWithoutADatabaseFailsClosedWithoutMintingARoutine() = runBlocking {
        val pack = AuxiliaryPacks.GolfNone
        seedCatalogIds(ids = pack.lifts.map { it.exerciseId })
        val repository = RoutineRepository(routineDao = deps.database.routineDao())

        val failed = runCatching { ensure(pack = pack, routines = repository) }

        assertTrue(failed.exceptionOrNull() is IllegalStateException)
        assertTrue(deps.database.routineDao().getAllRoutines().isEmpty())
        assertTrue(deps.database.routineDao().getAllRoutineExercises().isEmpty())
    }

    private suspend fun ensure(
        pack: AuxiliaryPack,
        routines: RoutineRepository = deps.routineRepository,
    ): String = withTimeout(timeMillis = 10_000L) {
        AuxiliaryBlocks.ensureRoutine(pack = pack, routines = routines, exercises = deps.exerciseRepository)
    }

    private suspend fun seedCatalogIds(ids: List<String>) {
        val catalog = DefaultExercises.catalog().filter { it.id in ids }
        assertEquals(ids.toSet(), catalog.map { it.id }.toSet())
        deps.database.exerciseDao().insertAll(
            exercises = catalog.map { exercise ->
                ExerciseEntity(
                    id = exercise.id,
                    name = exercise.name,
                    muscleGroup = exercise.muscleGroup,
                    notes = "",
                    isCustom = false,
                    equipment = exercise.equipment.name,
                    loadType = exercise.loadType.name,
                    movementKey = exercise.movementKey,
                    imageKey = exercise.imageKey,
                    nameKey = MuscleNormalizer.nameKeyOf(exercise.name),
                )
            },
        )
    }

    private suspend fun attachPack(routineId: String, pack: AuxiliaryPack) {
        pack.lifts.forEach { lift ->
            val exercise = deps.exerciseRepository.getById(id = lift.exerciseId)!!
            deps.routineRepository.addExercise(
                routineId = routineId,
                exercise = exercise,
                targetSets = lift.sets,
                targetReps = lift.reps,
                targetWeightKg = null,
                restSeconds = lift.restSeconds,
                targetSeconds = if (HoldWork.isHold(exercise)) lift.reps else null,
            )
        }
    }

    private fun authoring(requestSync: () -> Unit): SyncAuthoring = SyncAuthoring(
        auth = FakeAccountAuth(initialSession = AccountSession(email = "fixture@example.com", userId = "fixture-user")),
        outbox = SyncOutboxWriter(syncDao = deps.database.syncDao()),
        activityDao = deps.database.activityDao(),
        plannerDao = deps.database.plannerDao(),
        routineDao = deps.database.routineDao(),
        exerciseDao = deps.database.exerciseDao(),
        catalogDao = deps.database.catalogDao(),
        bodyweightDao = deps.database.bodyweightDao(),
        goalDao = deps.database.goalDao(),
        settingsStore = deps.preferencesRepository.accountSyncSettingsStore,
        requestSync = requestSync,
    )
}
