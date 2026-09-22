package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAccountAuth
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.repository.prefs.SettingsStore
import com.sinura.personaltrainer.domain.AccountSession
import com.sinura.personaltrainer.domain.AccountSyncGate
import com.sinura.personaltrainer.domain.SyncEntityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The pause that stands between Temper Account and the rows on this phone.
 *
 * The 22 September audit found that pulling a routine the server holds as newer replaced the
 * local row, and the replace cascaded to the routine's lifts. Packet S0b fixed the pull, but
 * other defects remain until S1, so while [AccountSyncGate.SYNC_PAUSED] holds no pass may run
 * at all — and the upload queue must keep filling so nothing waiting to go up is lost.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPauseTest {
    private lateinit var database: TemperDatabase
    private lateinit var remote: FakeSyncRemote
    private lateinit var settingsStore: SettingsStore
    private val signedIn = AccountSession(email = "owner@example.com", userId = USER)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = FakeSyncRemote()
        val prefsFile = File(context.filesDir, "sync-pause-test-${System.nanoTime()}.preferences_pb")
        settingsStore = SettingsStore(PreferenceDataStoreFactory.create { prefsFile })
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun theShippedGateKeepsSyncPausedAndInAppDeletionOff() {
        // Flipping either is a packet of its own (S1 resumes sync, S2b restores deletion), and
        // it should have to change this line on purpose.
        assertTrue(AccountSyncGate.SYNC_PAUSED)
        assertFalse(AccountSyncGate.IN_APP_DELETE_AVAILABLE)
    }

    @Test
    fun aPausedPassLeavesTheRoutineAndItsLiftsAlone() = runTest {
        seedRoutineWithOneLift(updatedAt = 100L)
        remote.seed(
            SyncEntityType.ROUTINE,
            encodeSync(RoutineEntity(ROUTINE, "Server name", "", 1L, 900L).toRemote(userId = USER)),
        )
        val coordinator = coordinator(paused = true, scheduler = CountingScheduler())

        assertTrue(coordinator.runPass(USER).isSuccess)

        val routine = database.routineDao().getById(ROUTINE)!!
        assertEquals("Legs", routine.routine.name)
        assertEquals(100L, routine.routine.updatedAt)
        assertEquals(listOf("re-1"), routine.items.map { it.item.id })
        assertEquals(0, remote.upserts.size)
    }

    @Test
    fun withoutThePauseThePullNowUpdatesTheRoutineAndKeepsItsLift() = runTest {
        // Until packet S0b this test asserted the defect the pause stands in front of: the
        // newer server routine went in with REPLACE, and ON DELETE CASCADE took its lift. The
        // pull now updates in place (SyncPullInPlaceTest covers every table it writes).
        seedRoutineWithOneLift(updatedAt = 100L)
        remote.seed(
            SyncEntityType.ROUTINE,
            encodeSync(RoutineEntity(ROUTINE, "Server name", "", 1L, 900L).toRemote(userId = USER)),
        )
        val coordinator = coordinator(paused = false, scheduler = CountingScheduler())

        assertTrue(coordinator.runPass(USER).isSuccess)

        val routine = database.routineDao().getById(ROUTINE)!!
        assertEquals("Server name", routine.routine.name)
        assertEquals(listOf("re-1"), routine.items.map { it.item.id })
    }

    @Test
    fun aPausedPassUploadsNothingAndKeepsTheQueue() = runTest {
        SyncOutboxWriter(syncDao = database.syncDao(), nowMillis = { 1L })
            .enqueueScheduleRules(userId = USER, rules = listOf(rule("rule-queued")))
        val coordinator = coordinator(paused = true, scheduler = CountingScheduler())

        assertTrue(coordinator.runPass(USER).isSuccess)

        assertEquals(0, remote.upserts.size)
        assertEquals(1, database.syncDao().pendingCount())
    }

    @Test
    fun anUnpausedPassStillRunsSoThePauseIsTheOnlyDifference() = runTest {
        SyncOutboxWriter(syncDao = database.syncDao(), nowMillis = { 1L })
            .enqueueScheduleRules(userId = USER, rules = listOf(rule("rule-sent")))
        val coordinator = coordinator(paused = false, scheduler = CountingScheduler())

        assertTrue(coordinator.runPass(USER).isSuccess)

        assertEquals(1, remote.upserts.count { it.first == SyncEntityType.SCHEDULE_RULE })
        assertEquals(0, database.syncDao().pendingCount())
    }

    @Test
    fun aPausedCoordinatorSchedulesNothingAndSaysSo() = runTest {
        val scheduler = CountingScheduler()
        val paused = coordinator(paused = true, scheduler = scheduler)

        paused.requestSync()

        assertEquals(0, scheduler.calls)
        val status = paused.status.first()
        assertTrue(status.paused)
        assertTrue(status.active)

        coordinator(paused = false, scheduler = scheduler).requestSync()
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun thePausableSchedulerDropsEveryRequestWhilePaused() {
        val delegate = CountingScheduler()
        PausableSyncScheduler(delegate = delegate, paused = true).apply {
            enqueueOneShot()
            enqueueOneShot()
        }
        assertEquals(0, delegate.calls)

        PausableSyncScheduler(delegate = delegate, paused = false).enqueueOneShot()
        assertEquals(1, delegate.calls)
    }

    @Test
    fun editsStillLandInTheQueueWhileNothingIsScheduled() = runTest {
        database.plannerDao().upsertRule(rule("rule-edited"))
        val scheduler = CountingScheduler()
        val authoring = SyncAuthoring(
            auth = FakeAccountAuth(initialSession = signedIn),
            outbox = SyncOutboxWriter(syncDao = database.syncDao(), nowMillis = { 1L }),
            activityDao = database.activityDao(),
            plannerDao = database.plannerDao(),
            routineDao = database.routineDao(),
            exerciseDao = database.exerciseDao(),
            catalogDao = database.catalogDao(),
            bodyweightDao = database.bodyweightDao(),
            goalDao = database.goalDao(),
            settingsStore = settingsStore,
            requestSync = PausableSyncScheduler(delegate = scheduler, paused = true)::enqueueOneShot,
        )

        authoring.onScheduleChanged()

        assertTrue(database.syncDao().pendingCount() > 0)
        assertEquals(0, scheduler.calls)
    }

    @Test
    fun theContainerHandsTheGateToBothTheSchedulerAndTheCoordinator() {
        // AppContainer opens a real database and DataStore, so no JVM test builds it. The two
        // places the gate must reach are read from the wiring instead.
        val roots = listOf(File("src/main/java"), File("app/src/main/java"))
        val container = roots
            .map { File(it, "com/sinura/personaltrainer/AppContainer.kt") }
            .first { it.isFile }
            .readText()
        val scheduler = container.substringAfter("private val syncScheduler").substringBefore(")\n")
        assertTrue(scheduler.contains("PausableSyncScheduler("))
        assertTrue(scheduler.contains("paused = AccountSyncGate.SYNC_PAUSED"))
        val coordinator = container.substringAfter("SyncCoordinator(").substringBefore("\n    )\n")
        assertTrue(coordinator.contains("paused = AccountSyncGate.SYNC_PAUSED"))
    }

    private fun coordinator(paused: Boolean, scheduler: SyncScheduler) = SyncCoordinator(
        auth = FakeAccountAuth(initialSession = signedIn),
        syncDao = database.syncDao(),
        engine = SyncEngine(
            syncDao = database.syncDao(),
            activityDao = database.activityDao(),
            plannerDao = database.plannerDao(),
            routineDao = database.routineDao(),
            exerciseDao = database.exerciseDao(),
            catalogDao = database.catalogDao(),
            bodyweightDao = database.bodyweightDao(),
            goalDao = database.goalDao(),
            settingsStore = settingsStore,
            remote = remote,
            nowMillis = { 5_000L },
        ),
        scheduler = scheduler,
        paused = paused,
    )

    private suspend fun seedRoutineWithOneLift(updatedAt: Long) {
        database.exerciseDao().insertAll(
            listOf(
                ExerciseEntity(
                    id = "ex-squat",
                    name = "Squat",
                    muscleGroup = "Quads",
                    notes = "",
                    isCustom = false,
                    nameKey = "squat",
                ),
            ),
        )
        database.routineDao().upsertRoutine(RoutineEntity(ROUTINE, "Legs", "", 1L, updatedAt))
        database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "re-1",
                routineId = ROUTINE,
                exerciseId = "ex-squat",
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 120,
            ),
        )
    }

    private fun rule(id: String) = ScheduleRuleEntity(
        id = id,
        weekday = 2,
        hour = 7,
        minute = 0,
        modality = "STRENGTH",
        zonePolicy = "DEVICE",
        fixedZoneId = null,
        routineId = null,
        templateId = null,
        focusKind = null,
        reminderOffsetMinutes = 0,
        enabled = 1,
        createdAtMs = 1L,
        updatedAtMs = 2L,
    )

    private class CountingScheduler : SyncScheduler {
        var calls = 0
        override fun enqueueOneShot() {
            calls++
        }
    }

    private companion object {
        const val USER = "user-1"
        const val ROUTINE = "routine-legs"
    }
}
