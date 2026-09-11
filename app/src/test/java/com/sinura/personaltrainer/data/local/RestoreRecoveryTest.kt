package com.sinura.personaltrainer.data.local

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.RestoreJournal
import com.sinura.personaltrainer.data.backup.RestoreJournalRecord
import com.sinura.personaltrainer.data.backup.RestoreRecovery
import com.sinura.personaltrainer.data.backup.RestoreWitness
import com.sinura.personaltrainer.data.backup.witnessFixture
import com.sinura.personaltrainer.data.repository.StartSessionOutcome
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The restore fault matrix (docs/HANDOFF-2026-09-06.md §2.4) on real Room and DataStore.
 *
 * Each test leaves the journal exactly as a crash at one boundary would, then runs the
 * launch-time recovery and checks both stores and the journal afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestoreRecoveryTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    // -------------------------------------------------------------------------------------
    // R01: same counts, same ids, different content.
    // -------------------------------------------------------------------------------------

    @Test
    fun crashBeforeTheWipeCommittedLeavesTheOriginalUntouched() = runBlocking {
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val current = deps.localBackupRepository.createSnapshot()
        val incoming = sameShapeHeavier(current)
        // The count fingerprint cannot tell these apart; that is the whole finding.
        assertEquals(RestoreJournal.fingerprint(current), RestoreJournal.fingerprint(incoming))
        assertNotEquals(RestoreWitness.of(current), RestoreWitness.of(incoming))

        stageAsRestoreWould(current, incoming)
        deps.restoreJournal.mark(RestoreJournal.WIPING)
        // No replaceRoom: the process died before the transaction committed.

        val recovery = deps.backupService.recoverInterruptedRestore()

        assertEquals(RestoreRecovery.NothingChanged, recovery)
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(100.0, deps.database.workoutDao().getAllSets().single().weightKg, 0.0)
        assertEquals(WeightUnit.LBS, deps.preferencesRepository.weightUnit.first())
        assertNull(deps.preferencesRepository.restoreRecoveryNote.first())
    }

    @Test
    fun crashAfterTheWipeCommittedFinishesFromRoom() = runBlocking {
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val current = deps.localBackupRepository.createSnapshot()
        val incoming = sameShapeHeavier(current)

        stageAsRestoreWould(current, incoming)
        deps.restoreJournal.mark(RestoreJournal.WIPING)
        deps.localBackupRepository.replaceRoom(incoming)
        // The process died between the commit and mark(ROOM).

        val recovery = deps.backupService.recoverInterruptedRestore()

        assertEquals(RestoreRecovery.Finished("same-shape.json"), recovery)
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(120.0, deps.database.workoutDao().getAllSets().single().weightKg, 0.0)
        assertEquals(WeightUnit.KG, deps.preferencesRepository.weightUnit.first())
    }

    @Test
    fun identicalContentEitherWayIsFinished() = runBlocking {
        // before == after: whichever side of the commit the crash was on, Room holds the
        // same tables, so the preferences half is owed regardless.
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val current = deps.localBackupRepository.createSnapshot()
        val incoming = current.copy(preferences = current.preferences.copy(weightUnit = "kg"))
        assertEquals(RestoreWitness.of(current), RestoreWitness.of(incoming))

        stageAsRestoreWould(current, incoming)
        deps.restoreJournal.mark(RestoreJournal.WIPING)

        assertEquals(RestoreRecovery.Finished("same-shape.json"), deps.backupService.recoverInterruptedRestore())
        assertEquals(WeightUnit.KG, deps.preferencesRepository.weightUnit.first())
        assertFalse(deps.restoreJournal.isOpen())
    }

    @Test
    fun aJournalFromBeforeWitnessesFallsBackToCounts() = runBlocking {
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val current = deps.localBackupRepository.createSnapshot()
        val incoming = current.copy(
            preferences = current.preferences.copy(weightUnit = "kg"),
            sessions = current.sessions + current.sessions.first().copy(id = "extra-session"),
        )
        deps.restoreJournal.stage(
            RestoreJournalRecord(
                phase = RestoreJournal.STAGED,
                sourceName = "old-build.json",
                snapshotId = "pre-restore-1.json",
                beforeFingerprint = deps.localBackupRepository.roomFingerprint(),
                afterFingerprint = RestoreJournal.fingerprint(incoming),
            ),
            BackupJson.encode(incoming),
        )
        deps.restoreJournal.mark(RestoreJournal.WIPING)

        // Counts differ and Room still matches the before-fingerprint: rolled back.
        assertEquals(RestoreRecovery.NothingChanged, deps.backupService.recoverInterruptedRestore())
        assertEquals(1, deps.database.workoutDao().getAllSessions().size)
        assertFalse(deps.restoreJournal.isOpen())
    }

    // -------------------------------------------------------------------------------------
    // R02: preferences fail after Room committed.
    // -------------------------------------------------------------------------------------

    @Test
    fun aFailedPreferencesWriteKeepsTheJournalAndRetriesOnTheNextLaunch() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        var flaky: FlakyDataStore? = null
        deps.close()
        deps = FakeAppDependencies(context = context, prefsStoreDecorator = { FlakyDataStore(it).also { flaky = it } })
        val store = checkNotNull(flaky)
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val incoming = sameShapeHeavier(deps.localBackupRepository.createSnapshot())

        store.failWrites.set(true)
        val result = deps.backupService.restoreFromJson(json = BackupJson.encode(incoming), sourceName = "phone.json")

        // Room is in; the commit is a success with settings owed, not a failure.
        assertTrue(result.settingsPending)
        assertFalse(result.preferencesRestored)
        assertEquals(120.0, deps.database.workoutDao().getAllSets().single().weightKg, 0.0)
        assertEquals(WeightUnit.LBS, deps.preferencesRepository.weightUnit.first())
        assertEquals(RestoreJournal.ROOM, deps.restoreJournal.read()?.phase)
        assertEquals("phone.json", deps.backupService.pendingRecovery())
        assertTrue(deps.backupService.restoreInProgress())
        // The training data is final, so a start is not refused while settings are owed.
        assertFalse(deps.backupService.restoreBlocksStart())

        // Still failing: the next launch keeps the journal rather than discarding the input.
        assertEquals(RestoreRecovery.SettingsPending("phone.json"), deps.backupService.recoverInterruptedRestore())
        assertEquals(RestoreJournal.ROOM, deps.restoreJournal.read()?.phase)

        store.failWrites.set(false)
        assertEquals(RestoreRecovery.Finished("phone.json"), deps.backupService.recoverInterruptedRestore())
        assertEquals(WeightUnit.KG, deps.preferencesRepository.weightUnit.first())
        assertFalse(deps.restoreJournal.isOpen())
        assertNull(deps.backupService.pendingRecovery())
    }

    // -------------------------------------------------------------------------------------
    // R03: cleanup interruption and lost input.
    // -------------------------------------------------------------------------------------

    @Test
    fun aCrashBetweenTheTwoCleanupDeletesNeverBlocksAStart() = runBlocking {
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val current = deps.localBackupRepository.createSnapshot()
        stageAsRestoreWould(current, current)
        deps.restoreJournal.mark(RestoreJournal.ROOM)
        deps.restoreJournal.mark(RestoreJournal.PREFS)
        deps.restoreJournal.mark(RestoreJournal.DONE)
        assertTrue(File(deps.restoreJournalDir, RestoreJournal.INCOMING_FILE).delete())
        assertTrue(deps.restoreJournal.isOpen())

        assertEquals(RestoreRecovery.Finished("same-shape.json"), deps.backupService.recoverInterruptedRestore())
        assertFalse(deps.restoreJournal.isOpen())
        assertTrue(deps.workoutRepository.startFreeWorkoutSafely() is StartSessionOutcome.Started)
    }

    @Test
    fun aRoomJournalWhoseInputIsGoneClosesWithANote() = runBlocking {
        // The previous build's cleanup order could leave exactly this. Room is restored,
        // the settings half can never be applied, and the owner is told — once, durably.
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val current = deps.localBackupRepository.createSnapshot()
        stageAsRestoreWould(current, current)
        deps.restoreJournal.mark(RestoreJournal.ROOM)
        assertTrue(File(deps.restoreJournalDir, RestoreJournal.INCOMING_FILE).delete())

        assertEquals(RestoreRecovery.SettingsLost("same-shape.json"), deps.backupService.recoverInterruptedRestore())
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(RestoreJournal.SETTINGS_LOST, deps.preferencesRepository.restoreRecoveryNote.first())

        deps.preferencesRepository.setRestoreRecoveryNote(null)
        assertNull(deps.preferencesRepository.restoreRecoveryNote.first())
    }

    @Test
    fun aStagedJournalIsClosedAndNothingChanged() = runBlocking {
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val current = deps.localBackupRepository.createSnapshot()
        stageAsRestoreWould(current, sameShapeHeavier(current))

        assertEquals(RestoreRecovery.NothingChanged, deps.backupService.recoverInterruptedRestore())
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(100.0, deps.database.workoutDao().getAllSets().single().weightKg, 0.0)
    }

    @Test
    fun noJournalIsNone() = runBlocking {
        assertEquals(RestoreRecovery.None, deps.backupService.recoverInterruptedRestore())
    }

    // -------------------------------------------------------------------------------------
    // The witness invariant the whole scheme rests on.
    // -------------------------------------------------------------------------------------

    @Test
    fun witnessOfADocumentEqualsWitnessOfRoomAfterReplacingWithIt() = runBlocking {
        // Every table replaceRoom writes, with the nulls the mappers default and nested
        // lists in non-sorted order. If this ever fails, recovery would report a committed
        // restore as unresolved — safe, but the owner would be told to check a safety copy.
        val document = witnessFixture()
        deps.localBackupRepository.replaceRoom(document)
        assertEquals(RestoreWitness.of(document), deps.localBackupRepository.roomWitness())
    }

    @Test
    fun aFullRestoreStillClosesTheJournalAndAppliesSettings() = runBlocking {
        seedTestWorkout(deps = deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        deps.preferencesRepository.setWeightUnit(WeightUnit.LBS)
        val incoming = sameShapeHeavier(deps.localBackupRepository.createSnapshot())

        val result = deps.backupService.restoreFromJson(json = BackupJson.encode(incoming), sourceName = "phone.json")

        assertTrue(result.preferencesRestored)
        assertFalse(result.settingsPending)
        assertFalse(deps.restoreJournal.isOpen())
        assertEquals(WeightUnit.KG, deps.preferencesRepository.weightUnit.first())
        assertEquals(120.0, deps.database.workoutDao().getAllSets().single().weightKg, 0.0)
        assertNull(deps.backupService.pendingRecovery())
    }

    /** Same sessions, same ids, same counts: heavier sets and a different unit. */
    private fun sameShapeHeavier(current: BackupDocument): BackupDocument = current.copy(
        setLogs = current.setLogs.map { it.copy(weightKg = 120.0) },
        preferences = current.preferences.copy(weightUnit = "kg"),
    )

    private suspend fun stageAsRestoreWould(current: BackupDocument, incoming: BackupDocument) {
        deps.restoreJournal.stage(
            RestoreJournalRecord(
                phase = RestoreJournal.STAGED,
                sourceName = "same-shape.json",
                snapshotId = "pre-restore-1.json",
                beforeFingerprint = deps.localBackupRepository.roomFingerprint(),
                afterFingerprint = RestoreJournal.fingerprint(incoming),
                beforeWitness = RestoreWitness.of(current),
                afterWitness = RestoreWitness.of(incoming),
            ),
            BackupJson.encode(incoming),
        )
    }
}

/** A preferences store whose writes fail on demand — the disk-full half of the matrix. */
private class FlakyDataStore(
    private val delegate: DataStore<Preferences>,
) : DataStore<Preferences> {
    val failWrites = AtomicBoolean(false)

    override val data: Flow<Preferences>
        get() = delegate.data

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (failWrites.get()) throw IOException("simulated: no space left on device")
        return delegate.updateData(transform)
    }
}
