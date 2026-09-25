package com.sinura.personaltrainer.ui.summary

import android.app.Activity
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.repository.AfterWorkoutUpload
import com.sinura.personaltrainer.domain.AutoBackupPolicy
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.TestWaits
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private lateinit var deps: FakeAppDependencies
    private val viewModels = mutableListOf<WorkoutSummaryViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            afterWorkoutUpload = AfterWorkoutUpload { _, _, password ->
                uploads.incrementAndGet()
                assertEquals(PASSWORD, String(password))
                started.complete(Unit)
                release.await()
            },
        )
    }

    @After
    fun tearDown() {
        release.complete(Unit)
        runBlocking { viewModels.forEach { it.clearAndJoinForTest() } }
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
        assertEquals(1, uploads.get())
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
