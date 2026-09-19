package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.local.dao.WorkoutDao
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManualRestStartTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ViewModel>()
    private var writeGate: CompletableDeferred<Unit>? = null
    private val entered = CompletableDeferred<Unit>()
    private var terminalGate: CompletableDeferred<Unit>? = null
    private val terminalEntered = CompletableDeferred<Unit>()

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(), scheduler = dispatcher,
            workoutDaoDecorator = { real -> object : WorkoutDao by real {
                override suspend fun finishSession(id: String, notes: String, durationMinutes: Int, finishedAt: Long): Int {
                    terminalGate?.let { terminalEntered.complete(Unit); it.await() }
                    return real.finishSession(id, notes, durationMinutes, finishedAt)
                }

                override suspend fun deleteInProgressSession(id: String): Int {
                    terminalGate?.let { terminalEntered.complete(Unit); it.await() }
                    return real.deleteInProgressSession(id)
                }
            } },
            prefsStoreDecorator = { real -> object : DataStore<Preferences> by real {
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                    writeGate?.let { gate -> entered.complete(Unit); gate.await() }
                    return real.updateData(transform)
                }
            } },
        )
    }

    @After fun cleanup() {
        writeGate?.complete(Unit)
        terminalGate?.complete(Unit)
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        deps.restTimerController.stop()
        dispatcher.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun skipCannotBeUndoneByDelayedManualStartPreferences() = activeScenario(timeSet = false)
    @Test fun setStopwatchCannotRaceWithDelayedManualRestStart() = activeScenario(timeSet = true)

    @Test fun finishingCancelsQueuedRestAndRejectsManualRestart() = terminalScenario(discard = false)
    @Test fun discardingCancelsQueuedRestAndRejectsManualRestart() = terminalScenario(discard = true)

    private fun terminalScenario(discard: Boolean) = runBlocking {
        val session = seedTestWorkout(deps).session
        val vm = ActiveWorkoutViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to session.id)), deps)
        models += vm
        vm.uiState.awaitFirst { it.canLog }
        vm.logSet()
        vm.uiState.awaitFirst { it.session?.sets?.size == 1 && !it.logging && !it.save.pending }
        terminalGate = CompletableDeferred()
        if (discard) vm.discardWorkout() else vm.finishWorkout()
        withTimeout(5_000) { terminalEntered.await() }
        assertFalse(deps.restTimerStore.current().running)
        dispatcher.scheduler.advanceTimeBy(500)
        dispatcher.scheduler.runCurrent()
        assertFalse("Queued receipt rest must not start during terminal write", deps.restTimerStore.current().running)
        vm.startSelectedRest()
        assertFalse("Manual rest must not start during terminal write", deps.restTimerStore.current().running)
        terminalGate!!.complete(Unit)
        vm.exitRequested.awaitFirst { it != null }
        vm.startSelectedRest()
        assertFalse("Terminal workout cannot restart rest", deps.restTimerStore.current().running)
    }

    private fun activeScenario(timeSet: Boolean) = runBlocking {
        val session = seedTestWorkout(deps).session
        val vm = ActiveWorkoutViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to session.id)), deps)
        models += vm
        vm.uiState.awaitFirst { it.canLog }
        vm.selectRestDuration(135)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { it.lastPresetSeconds == 135 }
        writeGate = CompletableDeferred()
        vm.startSelectedRest()
        withTimeout(5_000) { entered.await() }
        assertTrue("Start responds before preference IO completes", deps.restTimerStore.current().running)
        if (timeSet) vm.startSetStopwatch() else vm.skipRest()
        assertFalse(deps.restTimerStore.current().running)
        writeGate!!.complete(Unit)
        deps.preferencesRepository.restAlarmEligible.awaitFirst { it }
        assertFalse("Finishing preference IO cannot restart canceled rest", deps.restTimerStore.current().running)
        if (timeSet) assertTrue(vm.setStopwatch.value.running)
    }

    @Test fun expandedRestSkipCannotBeUndoneByDelayedPreferences() = runBlocking {
        val session = seedTestWorkout(deps).session
        val vm = RestTimerViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("sessionId" to session.id)), deps)
        models += vm
        vm.uiState.awaitFirst { it.loadState == SessionLoadState.FOUND && it.floor.exerciseName != null }
        vm.selectRestDuration(135)
        deps.preferencesRepository.restTimerPreferences.awaitFirst { it.lastPresetSeconds == 135 }
        writeGate = CompletableDeferred()
        vm.startSelectedRest()
        withTimeout(5_000) { entered.await() }
        assertTrue(deps.restTimerStore.current().running)
        vm.skipRest()
        writeGate!!.complete(Unit)
        deps.preferencesRepository.restAlarmEligible.awaitFirst { it }
        assertFalse(deps.restTimerStore.current().running)
    }
}
