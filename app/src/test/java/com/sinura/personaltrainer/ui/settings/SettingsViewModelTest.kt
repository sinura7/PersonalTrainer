package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.backup.SafetySnapshot
import com.sinura.personaltrainer.data.backup.SafetySnapshotMeta
import com.sinura.personaltrainer.domain.BackupPrompt
import com.sinura.personaltrainer.domain.ExactAlarmAttempt
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/**
 * Restore is disabled while a session is live — strength or cardio.
 * That flag must come from the live row, not from a remembered backup status.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: SettingsViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        dispatcher.scheduler.advanceUntilIdle()
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun inProgressSessionMarksRestoreBlocked() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val idle = withTimeout(5_000) { viewModel!!.backupState.first() }
        assertFalse(idle.sessionLive)

        deps.workoutRepository.startFreeWorkout("Legs")
        val live = withTimeout(5_000) {
            viewModel!!.backupState.first { it.sessionLive }
        }
        assertTrue(live.sessionLive)
    }

    @Test
    fun liveCardioMarksRestoreBlocked() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val idle = withTimeout(5_000) { viewModel!!.backupState.first() }
        assertFalse(idle.sessionLive)

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
        val live = withTimeout(5_000) {
            viewModel!!.backupState.first { it.sessionLive }
        }
        assertTrue(live.sessionLive)
    }

    @Test
    fun backupOlderThanFourteenDaysSurfacesThePrompt() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        val now = System.currentTimeMillis()
        deps.preferencesRepository.setLastBackup(
            "personal-trainer-backup-old.json",
            now - BackupPrompt.STALE_AFTER_MS - 1_000L,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val stale = withTimeout(5_000) { viewModel!!.backupState.first { it.lastBackupAt != null } }
        assertTrue(stale.backupStale)

        deps.preferencesRepository.setLastBackup("personal-trainer-backup-now.json", now)
        val fresh = withTimeout(5_000) {
            viewModel!!.backupState.first { it.lastBackupAt == now }
        }
        assertFalse(fresh.backupStale)
    }

    @Test
    fun configuringRestMarksExactAlarmPromptEligible() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.restTimerPreferences.first()
        viewModel!!.offerExactAlarmAccess.first { !it }
        viewModel!!.refreshAlarmCapability()
        assertFalse(deps.preferencesRepository.restAlarmEligible.first())

        viewModel!!.setDefaultRestSeconds(75)
        withTimeout(5_000) {
            viewModel!!.restTimerPreferences.first { it.defaultRestSeconds == 75 }
        }
        assertTrue(deps.preferencesRepository.restAlarmEligible.first())
        assertFalse(viewModel!!.offerExactAlarmAccess.value)
    }

    @Test
    fun bestEffortAndEligibleOffersExactAlarmSettings() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.setExactAlarmAttempt(ExactAlarmAttempt.BEST_EFFORT)
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        // Keep the DataStore-backed rest prefs flowing. first { !it } on the
        // offer flag alone can complete on the stateIn initial value before
        // setRestSoundEnabled's edit is observed.
        viewModel!!.restTimerPreferences.first()
        viewModel!!.offerExactAlarmAccess.first { !it }
        viewModel!!.setRestSoundEnabled(false)
        withTimeout(5_000) {
            viewModel!!.restTimerPreferences.first { !it.soundEnabled }
        }
        assertTrue(deps.preferencesRepository.restAlarmEligible.first())
        val offered = withTimeout(5_000) { viewModel!!.offerExactAlarmAccess.first { it } }
        assertTrue(offered)
    }

    @Test
    fun bestEffortWithoutEligibilityDoesNotOfferExactAlarmSettings() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        deps.setExactAlarmAttempt(ExactAlarmAttempt.BEST_EFFORT)
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.offerExactAlarmAccess.first { !it }
        assertFalse(deps.preferencesRepository.restAlarmEligible.first())
        assertFalse(viewModel!!.offerExactAlarmAccess.value)
    }

    @Test
    fun commitRestoreSurfacesSafetyCopyThenDeleteRemovesIt() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val json = deps.backupRepository.exportJson()
        deps.backupRepository.restoreFromJson(json, sourceName = "phone.json")

        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val listed = withTimeout(5_000) {
            viewModel!!.backupState.first { it.safetySnapshots.isNotEmpty() }
        }
        assertEquals(1, listed.safetySnapshots.size)
        val snap = listed.safetySnapshots.single()
        assertEquals(SafetySnapshotMeta.TITLE, snap.title)
        assertTrue(SafetySnapshot.isSafeId(snap.id))
        assertFalse(snap.id.contains("/"))
        assertTrue(snap.authored.sessions >= 1)

        viewModel!!.deleteSafetySnapshot(snap.id)
        val empty = withTimeout(5_000) {
            viewModel!!.backupState.first {
                it.safetySnapshots.isEmpty() && it.status?.contains("deleted") == true
            }
        }
        assertTrue(empty.safetySnapshots.isEmpty())
    }

    @Test
    fun safetyCopyRestoreGoesThroughPreview() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val json = deps.backupRepository.exportJson()
        deps.backupRepository.restoreFromJson(json, sourceName = "phone.json")
        val id = deps.backupRepository.listSafetySnapshots().single().id

        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.backupState.first { it.safetySnapshots.isNotEmpty() }
        viewModel!!.requestSafetyRestore(id)
        val preview = withTimeout(5_000) {
            viewModel!!.backupState.first { it.pendingPreview != null }
        }
        assertEquals(SafetySnapshotMeta.TITLE, preview.pendingPreview?.sourceName)
        assertTrue(preview.pendingPreview?.body?.contains("This file:") == true)
        assertTrue(preview.pendingPreview?.body?.contains("saved first") == true)
    }

    @Test
    fun fileExportAsksForAPasswordThenOpensThePicker() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
            envelopeIterations = 1_000,
        )
        viewModel!!.backupState.first()
        viewModel!!.beginFileExport()
        val protect = withTimeout(5_000) {
            viewModel!!.backupState.first { it.pendingProtect == BackupProtectKind.FILE_EXPORT }
        }
        assertEquals(BackupProtectKind.FILE_EXPORT, protect.pendingProtect)
        assertFalse(viewModel!!.submitProtect("short", "short"))
        assertTrue(viewModel!!.submitProtect("long-enough", "long-enough"))
        val picker = withTimeout(5_000) {
            viewModel!!.backupState.first { it.launchExportPicker }
        }
        assertTrue(picker.launchExportPicker)
        assertFalse(picker.pendingProtect != null)
    }

    @Test
    fun plaintextExportWarnsBeforeOpeningThePicker() = runBlocking {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.backupState.first()
        viewModel!!.beginFileExport()
        viewModel!!.beginPlaintextExport()
        val warned = withTimeout(5_000) {
            viewModel!!.backupState.first { it.pendingPlaintextWarning }
        }
        assertTrue(warned.pendingPlaintextWarning)
        viewModel!!.confirmPlaintextWarning()
        val picker = withTimeout(5_000) {
            viewModel!!.backupState.first { it.launchExportPicker }
        }
        assertTrue(picker.launchExportPicker)
    }
}
