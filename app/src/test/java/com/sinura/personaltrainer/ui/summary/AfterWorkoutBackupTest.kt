package com.sinura.personaltrainer.ui.summary

import android.app.Activity
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.repository.AfterWorkoutBackup
import com.sinura.personaltrainer.data.repository.AfterWorkoutUpload
import com.sinura.personaltrainer.domain.AutoBackupPolicy
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import com.sinura.personaltrainer.ui.settings.SettingsViewModel
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Drive copy after a finished workout ran on the summary's `viewModelScope`: Done or Back
 * a few seconds after finishing cancelled it at its next suspension, with nothing uploaded,
 * nothing recorded and nothing said (audit BK-4). The app owns it now; the summary starts it
 * and watches. The upload is a fake that holds until released, since the real one needs
 * Google Play services.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AfterWorkoutBackupTest {
    private val uploads = AtomicInteger()
    private val started = CompletableDeferred<Unit>()
    private val release = CompletableDeferred<Unit>()
    private val passwords = CopyOnWriteArrayList<String>()

    @Volatile private var consentAnswer: Boolean? = null
    private val inFlight = AtomicInteger()
    @Volatile private var overlapped = false

    /** What the fake upload does once released; the default is a clean upload. */
    private var afterRelease: suspend (suspend (IntentSender) -> Boolean) -> Unit = {}
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<WorkoutSummaryViewModel>()
    private val settingsModels = mutableListOf<SettingsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            afterWorkoutUpload = AfterWorkoutUpload { _, launchResolution, password ->
                if (inFlight.incrementAndGet() > 1) overlapped = true
                uploads.incrementAndGet()
                passwords += String(password)
                started.complete(Unit)
                try {
                    release.await()
                    afterRelease(launchResolution)
                } finally {
                    inFlight.decrementAndGet()
                }
            },
        )
    }

    @After
    fun tearDown() {
        release.complete(Unit)
        runBlocking {
            viewModels.forEach { it.clearAndJoinForTest() }
            settingsModels.forEach { it.clearAndJoinForTest() }
        }
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun leavingTheSummaryMidUploadDoesNotCancelTheCopy() = runBlocking {
        val sessionId = armedWorkout()
        val summary = summary(sessionId)
        summary.maybeAutoBackup(activity())
        withTimeout(TestWaits.FLOW_MS) { started.await() }
        summary.uiState.awaitFirst { it.autoBackup == AutoBackupPolicy.RUNNING }

        // Done: the summary pops and its ViewModel is cleared while the upload is in flight.
        summary.clearAndJoinForTest()
        release.complete(Unit)

        awaitBackedUp(sessionId)
        assertEquals(1, uploads.get())
        assertEquals(listOf(PASSWORD), passwords)
    }

    @Test
    fun aRecreatedSummaryWatchesTheCopyAlreadyRunningInsteadOfStartingAnother() = runBlocking {
        val sessionId = armedWorkout()
        val first = summary(sessionId)
        first.maybeAutoBackup(activity())
        withTimeout(TestWaits.FLOW_MS) { started.await() }
        first.clearAndJoinForTest()

        val second = summary(sessionId)
        second.maybeAutoBackup(activity())
        release.complete(Unit)

        second.uiState.awaitFirst { it.autoBackup == AutoBackupPolicy.DONE }
        deps.afterWorkoutBackup.joinRunningForTest()
        assertEquals(1, uploads.get())
        assertFalse(overlapped)
    }

    @Test
    fun armedWithNoDriveAccountTurnsAutomaticBackupOffInsteadOfUploading() = runBlocking {
        // What a Drive sign-out left behind before R1-1: the switch on, the password kept.
        deps.preferencesRepository.armAutoBackup(checkNotNull(deps.backupPassphraseSealer.seal(PASSWORD.toCharArray())))
        val sessionId = seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5))).session.id

        summary(sessionId).maybeAutoBackup(activity())

        withTimeout(TestWaits.FLOW_MS) {
            while (deps.preferencesRepository.autoBackupSettings().enabled) delay(10)
        }
        assertEquals(0, uploads.get())
        assertFalse(started.isCompleted)
    }

    @Test
    fun aFailedUploadSaysSoAndLeavesNoSignInNote() = runBlocking {
        afterRelease = { throw IOException("offline") }
        val sessionId = armedWorkout()
        val summary = summary(sessionId)
        summary.maybeAutoBackup(activity())
        release.complete(Unit)

        summary.uiState.awaitFirst { it.autoBackup == AutoBackupPolicy.FAILED }
        assertFalse(deps.preferencesRepository.autoBackupNeedsSignIn.first())
        assertNull(deps.preferencesRepository.autoBackupSettings().lastBackedUpSessionId)
    }

    @Test
    fun aLapsedGrantSaysSignInAgainInsteadOfShowingAConsentSheet() = runBlocking {
        afterRelease = { launchResolution ->
            val sender = PendingIntent.getActivity(
                ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE,
            ).intentSender
            consentAnswer = launchResolution(sender)
            throw BackupException("Google sign-in was cancelled.")
        }
        val sessionId = armedWorkout()
        val summary = summary(sessionId)
        summary.maybeAutoBackup(activity())
        release.complete(Unit)

        summary.uiState.awaitFirst { it.autoBackup == AutoBackupPolicy.NEEDS_SIGN_IN }
        // The copy declined: it never puts a Google dialog over the summary.
        assertEquals(false, consentAnswer)
        assertTrue(deps.preferencesRepository.autoBackupNeedsSignIn.first())
        assertTrue(deps.preferencesRepository.autoBackupSettings().enabled)
    }

    @Test
    fun aPasswordThatWillNotOpenTurnsAutomaticBackupOffInsteadOfUploading() = runBlocking {
        deps.preferencesRepository.setDriveAccountEmail("owner@example.com")
        // What a reinstall or a cleared Keystore leaves: a sealed blob nothing can open.
        deps.preferencesRepository.armAutoBackup("not a sealed password")
        val sessionId = seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5))).session.id

        summary(sessionId).maybeAutoBackup(activity())
        deps.afterWorkoutBackup.joinRunningForTest()

        assertFalse(deps.preferencesRepository.autoBackupSettings().enabled)
        assertEquals(0, uploads.get())
    }

    @Test
    fun signingOutOrSwitchingOffStopsACopyInFlight() = runBlocking {
        val sessionId = armedWorkout()
        summary(sessionId).maybeAutoBackup(activity())
        withTimeout(TestWaits.FLOW_MS) { started.await() }

        deps.afterWorkoutBackup.cancelRunning()
        release.complete(Unit)
        deps.afterWorkoutBackup.joinRunningForTest()

        assertNull(deps.preferencesRepository.autoBackupSettings().lastBackedUpSessionId)
        // A stopped copy leaves no "Backing up…" behind.
        assertNull(deps.afterWorkoutBackup.status(sessionId).first())
    }

    @Test
    fun switchingAutomaticBackupOffInSettingsStopsACopyInFlight() = runBlocking {
        val sessionId = armedWorkout()
        summary(sessionId).maybeAutoBackup(activity())
        withTimeout(TestWaits.FLOW_MS) { started.await() }
        val settings = SettingsViewModel(ApplicationProvider.getApplicationContext<Application>(), deps)
        settingsModels += settings

        settings.backup.setAutoBackupEnabled(false)
        withTimeout(TestWaits.FLOW_MS) {
            while (deps.preferencesRepository.autoBackupSettings().enabled) delay(10)
        }
        release.complete(Unit)
        deps.afterWorkoutBackup.joinRunningForTest()

        // Left running, the copy would have written its session back after the switch-off.
        assertNull(deps.preferencesRepository.autoBackupSettings().lastBackedUpSessionId)
    }

    @Test
    fun aCopyThatGoogleNeverAnswersGivesUpAndSaysSo() = runBlocking {
        deps.preferencesRepository.setDriveAccountEmail("owner@example.com")
        deps.preferencesRepository.armAutoBackup(checkNotNull(deps.backupPassphraseSealer.seal(PASSWORD.toCharArray())))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val backup = AfterWorkoutBackup(
                prefs = deps.preferencesRepository,
                sealer = deps.backupPassphraseSealer,
                scope = scope,
                upload = AfterWorkoutUpload { _, _, _ -> awaitCancellation() },
                uploadLimitMs = 200,
            )

            backup.start("session-1", activity())

            withTimeout(TestWaits.FLOW_MS) { backup.status("session-1").first { it == AutoBackupPolicy.FAILED } }
            backup.joinRunningForTest()
            assertNull(deps.preferencesRepository.autoBackupSettings().lastBackedUpSessionId)
        } finally {
            scope.cancel()
        }
    }

    private suspend fun armedWorkout(): String {
        deps.preferencesRepository.setDriveAccountEmail("owner@example.com")
        deps.preferencesRepository.armAutoBackup(checkNotNull(deps.backupPassphraseSealer.seal(PASSWORD.toCharArray())))
        return seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5))).session.id
    }

    private suspend fun awaitBackedUp(sessionId: String) {
        withTimeout(TestWaits.FLOW_MS) {
            while (deps.preferencesRepository.autoBackupSettings().lastBackedUpSessionId != sessionId) delay(10)
        }
    }

    private fun summary(sessionId: String): WorkoutSummaryViewModel =
        WorkoutSummaryViewModel(
            application = ApplicationProvider.getApplicationContext<Application>(),
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId)),
            container = deps,
        ).also(viewModels::add)

    private fun activity(): Activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

    private companion object {
        const val PASSWORD = "correct-horse"
    }
}
