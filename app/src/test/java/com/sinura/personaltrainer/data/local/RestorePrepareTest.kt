package com.sinura.personaltrainer.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.backup.SafetySnapshot
import java.io.File
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
