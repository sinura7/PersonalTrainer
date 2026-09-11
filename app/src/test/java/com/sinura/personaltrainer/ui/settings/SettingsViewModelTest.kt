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
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val idle = withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }
        assertFalse(idle.sessionLive)

        deps.workoutRepository.startFreeWorkout("Legs")
        val live = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.sessionLive }
        }
        assertTrue(live.sessionLive)
    }

    @Test
    fun turningAutomaticBackupOnAsksForAPassphraseBeforeArming() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val idle = withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }
        assertFalse(idle.autoBackupEnabled)
        assertFalse(idle.pendingAutoBackupArm)

        viewModel!!.backup.setAutoBackupEnabled(true)

        // The toggle alone must not arm anything: without a sealed passphrase the only
        // copy an unattended path could write would be plaintext.
        val asking = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.pendingAutoBackupArm }
        }
        assertFalse(asking.autoBackupEnabled)
        assertNull(deps.preferencesRepository.autoBackupSettings().sealedPassphrase)
    }

    @Test
    fun aConfirmedPassphraseArmsAutomaticBackupAndSurvivesAsCiphertext() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }

        viewModel!!.backup.setAutoBackupEnabled(true)
        assertTrue(viewModel!!.backup.submitAutoBackupPassphrase("correct horse", "correct horse"))

        val armed = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.autoBackupEnabled }
        }
        assertFalse(armed.pendingAutoBackupArm)

        val settings = deps.preferencesRepository.autoBackupSettings()
        assertTrue(settings.enabled)
        val sealed = settings.sealedPassphrase
        assertNotNull(sealed)
        // What is stored is not the passphrase. The sealer is the only thing that reads it.
        assertFalse(sealed!!.contains("correct horse"))
        assertEquals("correct horse", String(deps.backupPassphraseSealer.open(sealed)!!))
    }

    @Test
    fun aMismatchedConfirmationArmsNothing() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }

        viewModel!!.backup.setAutoBackupEnabled(true)
        assertFalse(viewModel!!.backup.submitAutoBackupPassphrase("correct horse", "clopper horse"))

        assertFalse(deps.preferencesRepository.autoBackupSettings().enabled)
        assertNull(deps.preferencesRepository.autoBackupSettings().sealedPassphrase)
    }

    @Test
    fun aFailedLockScreenRevealsNothing() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }

        viewModel!!.backup.setAutoBackupEnabled(true)
        viewModel!!.backup.submitAutoBackupPassphrase("correct horse", "correct horse")
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first { it.autoBackupEnabled } }

        viewModel!!.backup.onPasswordRevealAuthenticated(false)

        assertNull(viewModel!!.backup.revealedPassword.value)
    }

    @Test
    fun aPassedLockScreenShowsTheStoredPassword() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }

        viewModel!!.backup.setAutoBackupEnabled(true)
        viewModel!!.backup.submitAutoBackupPassphrase("correct horse", "correct horse")
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first { it.autoBackupEnabled } }

        viewModel!!.backup.onPasswordRevealAuthenticated(true)

        // This is the whole point of the row: arming stopped the app asking again, so the
        // sealed copy has to be readable back or a forgotten password seals Drive for good.
        val shown = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.revealedPassword.first { it != null }
        }
        assertEquals("correct horse", shown)

        viewModel!!.backup.dismissRevealedPassword()
        assertNull(viewModel!!.backup.revealedPassword.value)
    }

    @Test
    fun revealingWithNothingStoredSaysSoInsteadOfShowingBlank() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }

        viewModel!!.backup.onPasswordRevealAuthenticated(true)

        assertNull(viewModel!!.backup.revealedPassword.value)
        val failed = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.error != null }
        }
        assertTrue(failed.error!!.contains("No backup password"))
    }

    @Test
    fun turningItOffForgetsThePassphrase() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }

        viewModel!!.backup.setAutoBackupEnabled(true)
        viewModel!!.backup.submitAutoBackupPassphrase("correct horse", "correct horse")
        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first { it.autoBackupEnabled } }

        viewModel!!.backup.setAutoBackupEnabled(false)

        withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first { !it.autoBackupEnabled } }
        // An unopenable secret for a feature that is off helps nobody, so it goes too.
        assertNull(deps.preferencesRepository.autoBackupSettings().sealedPassphrase)
    }

    @Test
    fun liveCardioMarksRestoreBlocked() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val idle = withTimeout(TestWaits.FLOW_MS) { viewModel!!.backup.uiState.first() }
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
        val live = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.sessionLive }
        }
        assertTrue(live.sessionLive)
    }

    @Test
    fun backupOlderThanFourteenDaysSurfacesThePrompt() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        val now = System.currentTimeMillis()
        val old = now - BackupPrompt.STALE_AFTER_MS - 1_000L
        deps.preferencesRepository.setLastBackup("personal-trainer-backup-old.json", old)
        deps.preferencesRepository.setLastVerifiedBackup("personal-trainer-backup-old.json", old)
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        val stale = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.lastBackupAt != null }
        }
        assertTrue(stale.backupStale)

        // A fresh upload alone is NOT freshness any more. Nothing has read this file back, so
        // the prompt stays up — and says so, rather than claiming there was no backup.
        deps.preferencesRepository.setLastBackup("personal-trainer-backup-now.json", now)
        val written = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.lastBackupAt == now }
        }
        assertTrue(written.backupStale)
        assertEquals(BackupPrompt.UNVERIFIED_CAPTION, written.backupCaption)

        // Read back and proven: only now does the nag clear.
        deps.preferencesRepository.setLastVerifiedBackup("personal-trainer-backup-now.json", now)
        val verified = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.lastVerifiedBackupAt == now }
        }
        assertFalse(verified.backupStale)
        assertEquals(BackupPrompt.FRESH_CAPTION, verified.backupCaption)
    }

    @Test
    fun configuringRestMarksExactAlarmPromptEligible() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.uiState.first()
        viewModel!!.uiState.awaitFirst { !it.offerExactAlarmAccess }
        viewModel!!.refreshAlarmCapability()
        assertFalse(deps.preferencesRepository.restAlarmEligible.first())

        viewModel!!.setDefaultRestSeconds(75)
        withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { it.restTimer.defaultRestSeconds == 75 }
        }
        assertTrue(deps.preferencesRepository.restAlarmEligible.first())
        assertFalse(viewModel!!.uiState.value.offerExactAlarmAccess)
    }

    /** The third rest toggle writes the device-local key and counts as configuring rest. */
    @Test
    fun tickToggleWritesThePreferenceAndMarksExactAlarmPromptEligible() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        assertTrue(viewModel!!.uiState.first().restTimer.tickEnabled)
        assertFalse(deps.preferencesRepository.restAlarmEligible.first())
        viewModel!!.setRestTickEnabled(false)
        withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { !it.restTimer.tickEnabled }
        }
        assertTrue(deps.preferencesRepository.restAlarmEligible.first())
    }

    @Test
    fun playCompleteCueHandsLivePrefsToThePreview() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.uiState.first()
        viewModel!!.setRestSoundEnabled(false)
        withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { !it.restTimer.soundEnabled }
        }
        var seenSound: Boolean? = null
        var seenVibrate: Boolean? = null
        viewModel!!.previewRestCompleteCue { _, prefs ->
            seenSound = prefs.soundEnabled
            seenVibrate = prefs.vibrationEnabled
        }
        assertEquals(false, seenSound)
        assertEquals(true, seenVibrate)
    }

    @Test
    fun playCompleteCueOnTheRealPathDoesNotThrow() = runBlocking {
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.uiState.first()
        viewModel!!.previewRestCompleteCue()
    }

    @Test
    fun bestEffortAndEligibleOffersExactAlarmSettings() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        deps.setExactAlarmAttempt(ExactAlarmAttempt.BEST_EFFORT)
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        // Keep the DataStore-backed rest prefs flowing. first { !it } on the
        // offer flag alone can complete on the stateIn initial value before
        // setRestSoundEnabled's edit is observed.
        viewModel!!.uiState.first()
        viewModel!!.uiState.awaitFirst { !it.offerExactAlarmAccess }
        viewModel!!.setRestSoundEnabled(false)
        withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.uiState.first { !it.restTimer.soundEnabled }
        }
        assertTrue(deps.preferencesRepository.restAlarmEligible.first())
        val offered = withTimeout(TestWaits.FLOW_MS) { viewModel!!.uiState.first { it.offerExactAlarmAccess } }
        assertTrue(offered.offerExactAlarmAccess)
    }

    @Test
    fun bestEffortWithoutEligibilityDoesNotOfferExactAlarmSettings() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        deps.setExactAlarmAttempt(ExactAlarmAttempt.BEST_EFFORT)
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)

        viewModel!!.uiState.awaitFirst { !it.offerExactAlarmAccess }
        assertFalse(deps.preferencesRepository.restAlarmEligible.first())
        assertFalse(viewModel!!.uiState.value.offerExactAlarmAccess)
    }

    @Test
    fun commitRestoreSurfacesSafetyCopyThenDeleteRemovesIt() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val json = deps.backupService.exportJson()
        deps.backupService.restoreFromJson(json, sourceName = "phone.json")

        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        val listed = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.safetySnapshots.isNotEmpty() }
        }
        assertEquals(1, listed.safetySnapshots.size)
        val snap = listed.safetySnapshots.single()
        assertEquals(SafetySnapshotMeta.TITLE, snap.title)
        assertTrue(SafetySnapshot.isSafeId(snap.id))
        assertFalse(snap.id.contains("/"))
        assertTrue(snap.authored.sessions >= 1)

        viewModel!!.backup.deleteSafetySnapshot(snap.id)
        val empty = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first {
                it.safetySnapshots.isEmpty() && it.status?.contains("deleted") == true
            }
        }
        assertTrue(empty.safetySnapshots.isEmpty())
    }

    @Test
    fun safetyCopyRestoreGoesThroughPreview() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        seedTestWorkout(
            deps,
            finish = true,
            loggedSets = listOf(TestSetInput(100.0, 5)),
        )
        val json = deps.backupService.exportJson()
        deps.backupService.restoreFromJson(json, sourceName = "phone.json")
        val id = deps.backupService.listSafetySnapshots().single().id

        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.backup.uiState.awaitFirst { it.safetySnapshots.isNotEmpty() }
        viewModel!!.backup.requestSafetyRestore(id)
        val preview = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.pendingPreview != null }
        }
        assertEquals(SafetySnapshotMeta.TITLE, preview.pendingPreview?.sourceName)
        assertTrue(preview.pendingPreview?.body?.contains("This file:") == true)
        assertTrue(preview.pendingPreview?.body?.contains("saved first") == true)
    }

    @Test
    fun fileExportAsksForAPasswordThenOpensThePicker() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            deps,
            envelopeIterations = 1_000,
        )
        viewModel!!.backup.uiState.first()
        viewModel!!.backup.beginFileExport()
        val protect = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.pendingProtect == BackupProtectKind.FILE_EXPORT }
        }
        assertEquals(BackupProtectKind.FILE_EXPORT, protect.pendingProtect)
        assertFalse(viewModel!!.backup.submitProtect("short", "short"))
        assertTrue(viewModel!!.backup.submitProtect("long-enough", "long-enough"))
        val picker = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.launchExportPicker }
        }
        assertTrue(picker.launchExportPicker)
        assertFalse(picker.pendingProtect != null)
    }

    @Test
    fun plaintextExportWarnsBeforeOpeningThePicker() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        viewModel!!.backup.uiState.first()
        viewModel!!.backup.beginFileExport()
        viewModel!!.backup.beginPlaintextExport()
        val warned = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.pendingPlaintextWarning }
        }
        assertTrue(warned.pendingPlaintextWarning)
        viewModel!!.backup.confirmPlaintextWarning()
        val picker = withTimeout(TestWaits.FLOW_MS) {
            viewModel!!.backup.uiState.first { it.launchExportPicker }
        }
        assertTrue(picker.launchExportPicker)
    }

    @Test
    fun shrinkingTrainingDaysTrimsPreferredDays() = runBlocking {
        deps = FakeAppDependencies(
            ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        deps.preferencesRepository.setPreferredDays(
            setOf(
                com.sinura.personaltrainer.domain.Weekday.MONDAY,
                com.sinura.personaltrainer.domain.Weekday.TUESDAY,
                com.sinura.personaltrainer.domain.Weekday.WEDNESDAY,
                com.sinura.personaltrainer.domain.Weekday.THURSDAY,
                com.sinura.personaltrainer.domain.Weekday.FRIDAY,
            ),
        )
        deps.preferencesRepository.setWeekStart(com.sinura.personaltrainer.domain.Weekday.MONDAY)
        withTimeout(TestWaits.FLOW_MS) {
            deps.preferencesRepository.preferredDays.first { it.size == 5 }
        }
        deps.preferencesRepository.setTrainingDaysPerWeek(3)
        val days = withTimeout(TestWaits.FLOW_MS) {
            deps.preferencesRepository.preferredDays.first { it.size == 3 }
        }
        assertEquals(
            setOf(
                com.sinura.personaltrainer.domain.Weekday.MONDAY,
                com.sinura.personaltrainer.domain.Weekday.TUESDAY,
                com.sinura.personaltrainer.domain.Weekday.WEDNESDAY,
            ),
            days,
        )
    }
}
