package com.sinura.personaltrainer.ui.settings

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.BackupPrompt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Restore is disabled while a session is live. That flag must come from the
 * in-progress row, not from a remembered backup status.
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
}
