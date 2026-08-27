package com.sinura.personaltrainer.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupEnvelope
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.SafetySnapshot
import java.io.File
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prepare must not write. A catalog-only file must not wipe authored history.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestorePrepareTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun prepareDoesNotMutateExistingSessions() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val before = checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
        val json = deps.backupRepository.exportJson()
        val plan = deps.backupRepository.prepareRestore(json, sourceName = "same.json")
        val after = checkNotNull(deps.workoutRepository.getSession(fixture.session.id))
        assertEquals(before.id, after.id)
        assertEquals(before.finishedAt, after.finishedAt)
        assertEquals(1, plan.local.sessions)
        assertTrue(plan.incoming.sessions >= 1)
    }

    @Test
    fun protectedExportRoundTripsThroughPrepare() = runBlocking {
        seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val password = "correct-horse".toCharArray()
        val envelope = deps.backupRepository.exportProtected(password, iterations = 1_000)
        assertTrue(BackupEnvelope.looksLike(envelope))
        try {
            deps.backupRepository.prepareRestore(envelope, sourceName = "locked.json")
            fail("envelope without a password must ask, not decode")
        } catch (thrown: BackupException) {
            assertEquals(BackupEnvelope.NEED_PASSWORD, thrown.message)
        }
        val plan = deps.backupRepository.prepareRestore(
            envelope,
            sourceName = "locked.json",
            password = password,
        )
        assertTrue(plan.incoming.sessions >= 1)
        assertEquals(plan.local.sessions, plan.incoming.sessions)
    }

    @Test
    fun catalogOnlyFileIsRefusedAndLeavesHistory() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        try {
            deps.backupRepository.prepareRestore(catalogOnlyJson(), sourceName = "catalog.json")
            fail("catalog-only restore should refuse")
        } catch (thrown: BackupException) {
            assertTrue(thrown.message?.contains("refused") == true)
        }
        assertEquals(fixture.session.id, deps.workoutRepository.getSession(fixture.session.id)?.id)
    }

    @Test
    fun authoredInventoryCountsBodyweightAndBlocks() = runBlocking {
        assertEquals(0, deps.backupRepository.authoredInventory().bodyweightEntries)
        assertEquals(0, deps.backupRepository.authoredInventory().blocks)
        deps.preferencesRepository.recordBodyweight(82.0, epochDay = 20_000)
        assertEquals(1, deps.backupRepository.authoredInventory().bodyweightEntries)
        deps.preferencesRepository.setTrainingBlock(
            com.sinura.personaltrainer.domain.TrainingBlock(startEpochDay = 20_000, weeks = 12),
        )
        assertEquals(1, deps.backupRepository.authoredInventory().blocks)
    }

    @Test
    fun catalogOnlyFileIsRefusedWhenPhoneHasOnlyBodyweight() = runBlocking {
        deps.preferencesRepository.recordBodyweight(80.0, epochDay = 20_000)
        try {
            deps.backupRepository.prepareRestore(catalogOnlyJson(), sourceName = "catalog.json")
            fail("catalog-only restore should refuse")
        } catch (thrown: BackupException) {
            assertEquals(AuthoredInventory.EMPTY_INCOMING_REFUSED, thrown.message)
        }
        assertEquals(80.0, deps.preferencesRepository.bodyweightKg.first()!!, 0.0001)
    }

    @Test
    fun commitRestoreAbortsWhenSafetyDirIsUnwritable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val blocker = File(context.cacheDir, "not-a-safety-dir-${System.nanoTime()}").apply {
            writeText("nope")
        }
        deps.close()
        deps = FakeAppDependencies(context, safetySnapshotDir = blocker)
        val fixture = seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val json = deps.backupRepository.exportJson()
        val plan = deps.backupRepository.prepareRestore(json, sourceName = "same.json")
        try {
            deps.backupRepository.commitRestore(plan)
            fail("unwritable safety dir should abort restore")
        } catch (thrown: BackupException) {
            assertEquals(SafetySnapshot.MISSING_DIR, thrown.message)
        }
        assertEquals(fixture.session.id, deps.workoutRepository.getSession(fixture.session.id)?.id)
        assertTrue(deps.safetySnapshotDir.isFile)
    }

    @Test
    fun commitRestoreWritesAListableSafetyCopy() = runBlocking {
        val fixture = seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val incoming = deps.backupRepository.exportJson()
        val result = deps.backupRepository.restoreFromJson(incoming, sourceName = "phone.json")
        val snaps = deps.backupRepository.listSafetySnapshots()
        assertEquals(1, snaps.size)
        assertEquals(result.safetySnapshotId, snaps.single().id)
        assertTrue(SafetySnapshot.isSafeId(snaps.single().id))
        assertFalse(snaps.single().id.contains("/"))
        assertEquals(1, snaps.single().authored.sessions)
        assertEquals(fixture.session.id, deps.workoutRepository.getSession(fixture.session.id)?.id)
    }

    @Test
    fun successfulCommitClearsTheRestoreJournal() = runBlocking {
        seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val json = deps.backupRepository.exportJson()
        deps.backupRepository.restoreFromJson(json, sourceName = "phone.json")
        assertFalse(deps.backupRepository.restoreInProgress())
        assertFalse(deps.restoreJournal.isOpen())
    }

    @Test
    fun recoverFinishesPreferencesAfterRoomCommit() = runBlocking {
        seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.preferencesRepository.setWeightUnit(com.sinura.personaltrainer.domain.WeightUnit.LBS)
        val incoming = BackupJson.decode(deps.backupRepository.exportJson()).let { doc ->
            doc.copy(preferences = doc.preferences.copy(weightUnit = "kg"))
        }
        val incomingJson = BackupJson.encode(incoming)
        deps.restoreJournal.stage(
            com.sinura.personaltrainer.data.backup.RestoreJournalRecord(
                phase = com.sinura.personaltrainer.data.backup.RestoreJournal.STAGED,
                sourceName = "phone.json",
                snapshotId = "pre-restore-1.json",
                beforeFingerprint = "before",
                afterFingerprint = com.sinura.personaltrainer.data.backup.RestoreJournal.fingerprint(incoming),
            ),
            incomingJson,
        )
        deps.restoreJournal.mark(com.sinura.personaltrainer.data.backup.RestoreJournal.ROOM)
        deps.localBackupRepository.replaceRoom(incoming)
        assertEquals(
            com.sinura.personaltrainer.domain.WeightUnit.LBS,
            deps.preferencesRepository.weightUnit.first(),
        )
        assertTrue(deps.backupRepository.recoverInterruptedRestore())
        assertEquals(
            com.sinura.personaltrainer.domain.WeightUnit.KG,
            deps.preferencesRepository.weightUnit.first(),
        )
        assertFalse(deps.restoreJournal.isOpen())
    }

    @Test
    fun startWaitsForTheMaintenanceLock() = runBlocking {
        val holding = CompletableDeferred<Unit>()
        val holder = launch {
            deps.dbMaintenance.withMaintenanceLock {
                holding.complete(Unit)
                delay(200)
            }
        }
        holding.await()
        val started = async { deps.workoutRepository.startFreeWorkoutSafely() }
        delay(50)
        assertFalse(started.isCompleted)
        holder.join()
        val outcome = started.await()
        assertTrue(outcome is com.sinura.personaltrainer.data.repository.StartSessionOutcome.Started)
    }

    @Test
    fun startRefusesWhileRestoreJournalIsOpen() = runBlocking {
        deps.restoreJournal.stage(
            com.sinura.personaltrainer.data.backup.RestoreJournalRecord(
                phase = com.sinura.personaltrainer.data.backup.RestoreJournal.ROOM,
                sourceName = "phone.json",
                snapshotId = "pre-restore-1.json",
                beforeFingerprint = "before",
                afterFingerprint = "after",
            ),
            BackupJson.encode(
                BackupDocument(
                    exportedAt = "2026-08-24T10:00:00Z",
                    preferences = BackupPreferences(weightUnit = "kg"),
                    exercises = emptyList(),
                    routines = emptyList(),
                    routineExercises = emptyList(),
                    sessions = emptyList(),
                    sessionExercises = emptyList(),
                    setLogs = emptyList(),
                ),
            ),
        )
        val outcome = deps.workoutRepository.startFreeWorkoutSafely()
        assertTrue(outcome is com.sinura.personaltrainer.data.repository.StartSessionOutcome.Unavailable)
        assertEquals(
            com.sinura.personaltrainer.data.backup.RestoreJournal.INTERRUPTED,
            (outcome as com.sinura.personaltrainer.data.repository.StartSessionOutcome.Unavailable).message,
        )
    }

    @Test
    fun prepareRestoreRefusesWhileAStrengthSessionIsLive() = runBlocking {
        seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val json = deps.backupRepository.exportJson()
        deps.workoutRepository.startFreeWorkout("Legs")
        try {
            deps.backupRepository.prepareRestore(json, sourceName = "phone.json")
            fail("restore must refuse a live strength session")
        } catch (thrown: BackupException) {
            assertTrue(thrown.message.orEmpty().contains("session in progress"))
        }
        assertTrue(deps.workoutRepository.getInProgress() != null)
    }

    @Test
    fun prepareRestoreRefusesWhileLiveCardioIsRunning() = runBlocking {
        seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val json = deps.backupRepository.exportJson()
        val now = com.sinura.personaltrainer.util.JvmTime.captureNow()
        val started = deps.startLiveActivity(
            "Easy run",
            listOf(
                com.sinura.personaltrainer.domain.CardioBlock(
                    id = "blk-live",
                    sortOrder = 0,
                    type = com.sinura.personaltrainer.domain.CardioType.RUN,
                    indoor = false,
                    elapsedSeconds = 0,
                    movingSeconds = 0,
                    distanceMeters = null,
                    elevationMeters = null,
                    heartRateBpm = null,
                    energyKj = null,
                    rpe = null,
                    routeRef = null,
                ),
            ),
            now,
        )
        assertTrue(started is com.sinura.personaltrainer.domain.ActivityWrite.Accepted)
        try {
            deps.backupRepository.prepareRestore(json, sourceName = "phone.json")
            fail("restore must refuse live cardio")
        } catch (thrown: BackupException) {
            assertTrue(thrown.message.orEmpty().contains("session in progress"))
        }
        assertTrue(deps.activityRepository.getLive() != null)
    }
}

private fun catalogOnlyJson(): String = BackupJson.encode(
    BackupDocument(
        exportedAt = "2026-08-24T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(
            BackupExercise(
                id = "ex-squat",
                name = "Barbell Back Squat",
                muscleGroup = "Quads",
                notes = "",
                isCustom = false,
                equipment = "BARBELL",
                loadType = "EXTERNAL",
            ),
        ),
        routines = emptyList(),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
    ),
)
