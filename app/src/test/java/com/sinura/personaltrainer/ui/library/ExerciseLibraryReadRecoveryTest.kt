package com.sinura.personaltrainer.ui.library

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.AppDependencies
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.dao.ExerciseDao
import com.sinura.personaltrainer.data.local.dao.RoutineDao
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.relation.RoutineWithExercises
import com.sinura.personaltrainer.data.repository.ExerciseRepository
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.insertTestExercise
import com.sinura.personaltrainer.testutil.seedTestWorkout
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/** Real Room/private DataStore faults; Main-owned reads and Retry share one pumped owner. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExerciseLibraryReadRecoveryTest {
    private enum class Source { CATALOG, ROUTINES, COLLISIONS, PREFERENCES }
    private val dispatcher = UnconfinedTestDispatcher()
    private val mainDispatcher = StandardTestDispatcher(scheduler = dispatcher.scheduler)
    private lateinit var deps: FakeAppDependencies
    private lateinit var vm: ExerciseLibraryViewModel
    private var collector: Job? = null
    private val fail = MutableStateFlow(false)
    private var faultSource = Source.CATALOG
    private var readGate: CompletableDeferred<Unit>? = null
    private val faultObserved = MutableStateFlow(false)
    private var routineLookupGate: CompletableDeferred<Unit>? = null
    private val routineLookupEntered = MutableStateFlow(false)
    private var heldLookup = 1
    private var lookupCount = 0
    private var failRoutineLookup = false
    private var failDelete = false
    private var failPreferenceWrite = false

    @Before fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            prefsStoreDecorator = { real ->
                object : DataStore<Preferences> by real {
                    override val data = guarded(Source.PREFERENCES, real.data)
                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                        if (failPreferenceWrite) throw IOException("Synthetic preference write failure")
                        return real.updateData(transform)
                    }
                }
            },
        )
    }

    @After fun tearDown() {
        readGate?.complete(Unit)
        routineLookupGate?.complete(Unit)
        collector?.cancel()
        runBlocking {
            if (::vm.isInitialized) {
                val job = checkNotNull(vm.viewModelScope.coroutineContext[Job])
                job.cancel()
                pumpUntil(what = "Library owner retired", done = { job.isCompleted })
            }
        }
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test fun firstCatalogFailureHasARealRetryInsteadOfEndlessLoading() = firstFailure(Source.CATALOG)
    @Test fun firstRoutineFailureDoesNotMasqueradeAsAnEmptyLibrary() = firstFailure(Source.ROUTINES)
    @Test fun firstCollisionFailureDoesNotHideTheRequiredRead() = firstFailure(Source.COLLISIONS)
    @Test fun firstPreferencesFailureDoesNotUseSafeDefaultsAsAHealthyRead() = firstFailure(Source.PREFERENCES)

    @Test fun aRealCatalogReadReturnsToTheMainOwnerThread() = runBlocking {
        val ownerThread = Thread.currentThread().id
        val exercise = insertTestExercise(deps, "ex-thread-proof", "Thread proof", muscleGroup = "Back", isCustom = true)
        mountOwner()
        val returned = CompletableDeferred<Pair<Long, String?>>()
        vm.viewModelScope.launch {
            val row = deps.exerciseRepository.getById(exercise.id)
            returned.complete(Thread.currentThread().id to row?.id)
        }
        pumpUntil(what = "real catalog read returned", timeoutMs = 10_000, done = { returned.isCompleted })
        val (returnedThread, id) = returned.await()
        assertEquals("the actual catalog identity is retained", exercise.id, id)
        assertEquals("Main-owned Library state cannot resume on a Room worker", ownerThread, returnedThread)
    }

    private fun firstFailure(source: Source) = runBlocking {
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        val original = deps.exerciseRepository.observeAll().first()
        faultSource = source
        fail.value = true
        mountOwner()
        vm.onQueryChange("  ROW  ")
        vm.onMuscleSelected(CanonicalMuscle.BACK)
        vm.onEquipmentSelected(EquipmentType.OTHER)
        val refused = vm.uiState.awaitConfined { it.readState == LibraryReadState.UNAVAILABLE }
        assertFalse(refused.isLoading)
        assertTrue(refused.exercises.isEmpty())
        assertEquals("  ROW  ", refused.query)
        assertEquals(CanonicalMuscle.BACK, refused.selectedMuscle)
        assertEquals(EquipmentType.OTHER, refused.selectedEquipment)
        drainDeniedActions(what = "unavailable Library Save", actions = {
            vm.openCreate()
            vm.saveEditor()
        })
        fail.value = false
        assertTrue(vm.retryRead())
        val recovered = vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT && !it.retryPending }
        assertEquals(listOf(row.id), recovered.visibleExercises.map { it.id })
        assertEquals("  ROW  ", recovered.query)
        assertEquals(CanonicalMuscle.BACK, recovered.selectedMuscle)
        assertEquals(EquipmentType.OTHER, recovered.selectedEquipment)
        assertEquals(original, deps.exerciseRepository.observeAll().first())
        assertFalse("second retry must be refused: ${vm.uiState.value}", vm.retryRead())
    }

    @Test fun aLateFailureRetainsManualFieldsAndClearedContextThroughRetry() = runBlocking {
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        val storedRow = deps.exerciseRepository.getById(row.id)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        vm.onQueryChange("  ROW  ")
        vm.onMuscleSelected(CanonicalMuscle.BACK)
        vm.clearFilters()
        vm.openEdit(row)
        val draft = ExerciseEditorDraft(id = row.id, name = "  Raw authored name  ", muscleGroup = "Back", notes = "Unsaved notes")
        vm.updateEditor(draft)
        fail.value = true
        val failed = vm.uiState.awaitConfined { it.readState == LibraryReadState.STALE }
        assertEquals(draft, failed.editor)
        assertEquals("  ROW  ", failed.query)
        drainDeniedActions(what = "stale Library Save", actions = { vm.saveEditor() })
        assertEquals(storedRow, deps.exerciseRepository.getById(row.id))
        fail.value = false
        assertTrue(vm.retryRead())
        val recovered = vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT && !it.retryPending }
        assertEquals(draft, recovered.editor)
        assertEquals("  ROW  ", recovered.query)
        assertEquals(null, recovered.selectedMuscle)
        assertEquals(null, recovered.selectedEquipment)
        assertEquals(storedRow, deps.exerciseRepository.getById(row.id))
    }

    @Test fun anAbsentCompositionCannotKeepStaleWriteAdmissionAlive() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        vm.openEdit(row)
        vm.updateEditor(ExerciseEditorDraft(id = row.id, name = "Must not save", muscleGroup = "Back"))
        vm.requestDelete(row)
        vm.uiState.awaitConfined { it.pendingDelete?.id == row.id }
        vm.openAddToRoutine(row)
        vm.uiState.awaitConfined { it.addToRoutine?.id == row.id }
        val originalCatalog = deps.exerciseRepository.observeAll().first()
        val originalRoutines = deps.routineRepository.observeAll().first()
        val originalDismissals = deps.preferencesRepository.dismissedCollisionIds.first()
        collector?.cancel()
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(5_001)
        dispatcher.scheduler.runCurrent()
        assertEquals(LibraryReadState.CURRENT, vm.uiState.value.readState)
        fail.value = true
        pumpUntil(what = "required source failure observed", timeoutMs = 10_000, done = { faultObserved.value })
        dispatcher.scheduler.runCurrent()
        drainDeniedActions(what = "absent composition guarded writes", actions = {
            vm.saveEditor()
            vm.confirmDelete()
            vm.addToRoutine(fixture.routine.id)
            vm.keepBothNames(row)
        })
        assertEquals(originalCatalog, deps.exerciseRepository.observeAll().first())
        assertEquals(originalRoutines, deps.routineRepository.observeAll().first())
        assertEquals(originalDismissals, deps.preferencesRepository.dismissedCollisionIds.first())
        collectOwner()
        assertEquals(LibraryReadState.STALE, vm.uiState.awaitConfined { it.readState == LibraryReadState.STALE }.readState)
    }

    @Test fun retryDoesNotAdmitDuplicateRequestsWhileFreshReadsAreHeld() = runBlocking {
        faultSource = Source.CATALOG
        fail.value = true
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.UNAVAILABLE }
        readGate = CompletableDeferred()
        fail.value = false
        assertTrue(vm.retryRead())
        val beforeDuplicate = vm.uiState.value
        val duplicateAccepted = vm.retryRead()
        assertFalse(
            "held duplicate Retry: before=$beforeDuplicate; after=${vm.uiState.value}; gate=${readGate!!.isCompleted}; " +
                ShadowLog.getLogs().filter { it.throwable != null }.takeLast(5).joinToString { "${it.tag}: ${it.msg}: ${it.throwable}" },
            duplicateAccepted,
        )
        assertTrue(vm.uiState.awaitConfined { it.retryPending }.retryPending)
        drainDeniedActions(what = "held Retry guarded Save", actions = {
            vm.openCreate()
            vm.saveEditor()
        })
        assertTrue(deps.exerciseRepository.observeAll().first().isEmpty())
        readGate!!.complete(Unit)
        val ready = vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT && !it.retryPending }
        assertTrue(ready.exercises.isEmpty())
        assertFalse(ready.isLoading)
        assertEquals(null, ready.editor)
    }

    @Test fun anAddSuspendedBeforeRecoveryCannotUseThePreviousLiftClassification() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        vm.openAddToRoutine(row)
        val original = deps.database.routineDao().getAllRoutineExercises()
        routineLookupGate = CompletableDeferred()
        vm.addToRoutine(fixture.routine.id)
        pumpUntil(what = "routine lookup entered", timeoutMs = 10_000, done = { routineLookupEntered.value })
        fail.value = true
        vm.uiState.awaitConfined { it.readState == LibraryReadState.STALE }
        deps.exerciseRepository.updateCustom(row.id, row.name, row.muscleGroup, row.notes, LoadType.BODYWEIGHT)
        fail.value = false
        assertTrue(vm.retryRead())
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT && it.addToRoutine?.loadType == LoadType.BODYWEIGHT }
        routineLookupGate!!.complete(Unit)
        vm.uiState.awaitConfined { it.error == LibraryReadCopy.REFRESHED }
        assertEquals(original, deps.database.routineDao().getAllRoutineExercises())
        vm.openAddToRoutine(deps.exerciseRepository.getById(row.id)!!)
        vm.addToRoutine(fixture.routine.id)
        vm.uiState.awaitConfined { it.addToRoutine == null && it.message?.startsWith("Added ") == true }
        val added = deps.database.routineDao().getAllRoutineExercises().single { it.exerciseId == row.id }
        assertEquals(12, added.targetReps)
        assertEquals(60, added.restSeconds)
        assertEquals(original.size + 1, deps.database.routineDao().getAllRoutineExercises().size)
    }

    @Test fun aFailedInitialRoutineLookupReportsFailureWithoutWriting() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        vm.openAddToRoutine(row)
        val original = deps.database.routineDao().getAllRoutineExercises()
        failRoutineLookup = true
        vm.addToRoutine(fixture.routine.id)
        vm.uiState.awaitConfined { it.error == "Could not add that lift to the routine." }
        assertEquals(original, deps.database.routineDao().getAllRoutineExercises())
        assertEquals(null, vm.uiState.value.addToRoutine)
    }

    @Test fun anAddHeldInsideTheTransactionCannotWriteAfterRetryBegins() = runBlocking {
        val fixture = seedTestWorkout(deps)
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        vm.openAddToRoutine(row)
        val original = deps.database.routineDao().getAllRoutineExercises()
        val originalRoutine = deps.database.routineDao().getById(fixture.routine.id)
        heldLookup = 2
        routineLookupGate = CompletableDeferred()
        vm.addToRoutine(fixture.routine.id)
        pumpUntil(what = "routine lookup entered", timeoutMs = 10_000, done = { routineLookupEntered.value })
        fail.value = true
        vm.uiState.awaitConfined { it.readState == LibraryReadState.STALE }
        readGate = CompletableDeferred()
        fail.value = false
        assertTrue(vm.retryRead())
        vm.uiState.awaitConfined { it.retryPending }
        routineLookupGate!!.complete(Unit)
        vm.uiState.awaitConfined { it.error == LibraryReadCopy.REFRESHED }
        assertEquals(original, deps.database.routineDao().getAllRoutineExercises())
        assertEquals(originalRoutine, deps.database.routineDao().getById(fixture.routine.id))
        assertEquals(null, vm.uiState.value.message)
        assertEquals(null, vm.uiState.value.addToRoutine)
        readGate!!.complete(Unit)
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT && !it.retryPending }
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun aFailedDeleteDoesNotClaimSuccessOrRemoveTheLift() = runBlocking {
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        vm.requestDelete(row)
        vm.uiState.awaitConfined { it.pendingDelete?.id == row.id }
        val original = deps.exerciseRepository.observeAll().first()
        failDelete = true
        vm.confirmDelete()
        vm.uiState.awaitConfined { it.error == "Delete could not be confirmed. Check the library before trying again." }
        assertEquals(original, deps.exerciseRepository.observeAll().first())
        assertEquals(null, vm.uiState.value.pendingDelete)
        assertEquals(null, vm.uiState.value.message)
    }

    @Test fun aFailedCollisionChoiceDoesNotClaimConfirmation() = runBlocking {
        val row = insertTestExercise(deps, "ex-custom-row", "My row", muscleGroup = "Back", isCustom = true)
        mountOwner()
        vm.uiState.awaitConfined { it.readState == LibraryReadState.CURRENT }
        val original = deps.preferencesRepository.dismissedCollisionIds.first()
        failPreferenceWrite = true
        vm.keepBothNames(row)
        vm.uiState.awaitConfined { it.error == "Could not confirm that choice. Try again." }
        assertEquals(original, deps.preferencesRepository.dismissedCollisionIds.first())
        assertEquals(null, vm.uiState.value.message)
    }

    private fun mountOwner() {
        val exerciseDao = object : ExerciseDao by deps.database.exerciseDao() {
            override fun observeAll(): Flow<List<ExerciseEntity>> = guarded(Source.CATALOG, deps.database.exerciseDao().observeAll())
            override fun observeBuiltInCollisions(): Flow<List<ExerciseEntity>> =
                guarded(Source.COLLISIONS, deps.database.exerciseDao().observeBuiltInCollisions())
            override suspend fun deleteCustom(id: String) {
                if (failDelete) throw IOException("Synthetic delete failure")
                deps.database.exerciseDao().deleteCustom(id)
            }
        }
        val routineDao = object : RoutineDao by deps.database.routineDao() {
            override fun observeAll(): Flow<List<RoutineWithExercises>> = guarded(Source.ROUTINES, deps.database.routineDao().observeAll())
            override suspend fun getById(id: String): RoutineWithExercises? {
                if (failRoutineLookup) throw IOException("Synthetic routine lookup failure")
                lookupCount += 1
                if (lookupCount == heldLookup) {
                    routineLookupEntered.value = true
                    routineLookupGate?.await()
                }
                return deps.database.routineDao().getById(id)
            }
        }
        val container = object : AppDependencies by deps {
            override val exerciseRepository = ExerciseRepository(
                exerciseDao = exerciseDao, routineDao = deps.database.routineDao(),
                workoutDao = deps.database.workoutDao(), catalogDao = deps.database.catalogDao(), database = deps.database,
            )
            override val routineRepository = RoutineRepository(routineDao = routineDao, database = deps.database)
        }
        vm = ExerciseLibraryViewModel(application = ApplicationProvider.getApplicationContext(), container = container)
        collectOwner()
    }

    private fun collectOwner() { collector = CoroutineScope(mainDispatcher).launch { vm.uiState.collect {} } }

    /** Inventory checks must follow any accidentally admitted write, even without a UI subscriber. */
    private suspend fun drainDeniedActions(what: String, actions: () -> Unit) {
        val parent = checkNotNull(vm.viewModelScope.coroutineContext[Job])
        val existing = parent.children.toSet()
        val frozenAt = dispatcher.scheduler.currentTime
        actions()
        val admitted = parent.children.filterNot { it in existing }.toList()
        dispatcher.scheduler.runCurrent()
        pumpUntil(what = what, done = { admitted.all { it.isCompleted } })
        assertEquals("denied-action checks advance no virtual time", frozenAt, dispatcher.scheduler.currentTime)
    }

    /** Keep real subscriptions and process current Main work without advancing debounce time. */
    private suspend fun <T> Flow<T>.awaitConfined(predicate: (T) -> Boolean): T = coroutineScope {
        val frozenAt = dispatcher.scheduler.currentTime
        val result = async { this@awaitConfined.awaitFirst(predicate) }
        pumpUntil(what = "Main-owned Library state", done = { result.isCompleted })
        assertEquals("waiting on Main advances no virtual time", frozenAt, dispatcher.scheduler.currentTime)
        result.await()
    }

    private suspend fun pumpUntil(
        what: String,
        timeoutMs: Long = TestWaits.FLOW_MS,
        done: () -> Boolean,
    ) {
        withTimeout(timeoutMs) {
            while (!done()) {
                dispatcher.scheduler.runCurrent()
                if (!done()) delay(10)
            }
        }
        assertTrue(what, done())
    }

    private fun <T> guarded(source: Source, values: Flow<T>): Flow<T> = fail.flatMapLatest { failed ->
        flow {
            if (source == faultSource) {
                if (failed) {
                    faultObserved.value = true
                    throw IOException("Synthetic required Library read failure")
                }
                readGate?.await()
            }
            emitAll(values)
        }
    }
}
