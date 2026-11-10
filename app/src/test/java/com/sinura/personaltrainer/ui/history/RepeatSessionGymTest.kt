package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.testutil.TestGymIds
import com.sinura.personaltrainer.testutil.awaitFirst
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RepeatSessionGymTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(
            context = ApplicationProvider.getApplicationContext(),
            scheduler = dispatcher,
        )
        viewModel = HistoryViewModel(ApplicationProvider.getApplicationContext(), deps)
    }

    @After
    fun tearDown() {
        deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun repeatCarriesChosenGym() = runBlocking {
        val finished = seedTestWorkout(deps, finish = true).session
        viewModel.repeatSession(finished.id)
        viewModel.fulfillRepeatGym(TestGymIds.WESTON)
        viewModel.navigateToSession.awaitFirst { it != null }
        val live = deps.workoutRepository.getInProgress()
        checkNotNull(live)
        assertEquals(TestGymIds.WESTON, deps.database.workoutDao().getSession(live.id)?.session?.gymId)
    }

    @Test
    fun repeatGymPickRequiredBeforeFulfill() = runBlocking {
        val finished = seedTestWorkout(deps, finish = true).session
        viewModel.repeatSession(finished.id)
        assertEquals(finished.id, viewModel.repeatGymPick.value)
        assertNull(deps.workoutRepository.getInProgress())
    }
}
