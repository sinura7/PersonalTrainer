package com.sinura.personaltrainer.ui.activity

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.Exercise
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ActivityComposerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var deps: FakeAppDependencies
    private var viewModel: ActivityComposerViewModel? = null

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        runBlocking { viewModel?.clearAndJoinForTest() }
        viewModel = null
        if (::deps.isInitialized) deps.close()
        Dispatchers.resetMain()
    }

    @Test
    fun backdatedStrengthWritesAnActivityWithSetsAndNoFutureDate() = runBlocking {
        val exercise = seedLift()
        viewModel = composer("strength")
        viewModel!!.addStrength(exercise, 100.0, 5)
        viewModel!!.setEpochDay(20_000L)
        viewModel!!.save()
        val id = withTimeout(5_000) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertEquals(ActivityOrigin.BACKDATED, session.origin)
        assertEquals(1, session.strengthSetCount())
        assertTrue(session.cardioBlocks.isEmpty())
        assertEquals(0, deps.database.workoutDao().getAllSets().size)
    }

    @Test
    fun typedCardioWritesZeroStrengthRows() = runBlocking {
        viewModel = composer("cardio")
        viewModel!!.addCardio(CardioType.RUN, 30, 5.0, false)
        viewModel!!.save()
        val id = withTimeout(5_000) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertTrue(session.isCardioOnly)
        assertEquals(0, session.strengthSetCount())
        assertEquals(0, deps.database.workoutDao().getAllSets().size)
    }

    @Test
    fun mixedSessionKeepsSeparateModalityBlocks() = runBlocking {
        val exercise = seedLift()
        viewModel = composer("mixed")
        viewModel!!.addStrength(exercise, 80.0, 8)
        viewModel!!.addCardio(CardioType.RIDE, 20, null, true)
        viewModel!!.save()
        val id = withTimeout(5_000) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertTrue(session.isMixed)
        assertEquals(1, session.strengthBlocks.size)
        assertEquals(1, session.cardioBlocks.size)
        assertEquals(0, session.strengthBlocks.first().sortOrder)
        assertEquals(1, session.cardioBlocks.first().sortOrder)
    }

    @Test
    fun untitledCardioUsesGymNameNotSchemaEnum() = runBlocking {
        viewModel = composer("cardio")
        viewModel!!.addCardio(CardioType.RUN, 30, 5.0, false)
        viewModel!!.save()
        val id = withTimeout(5_000) { viewModel!!.savedId.first { it != null } }!!
        val session = deps.activityRepository.get(id)!!
        assertEquals("Run", session.title)
        assertNotEquals("RUN", session.title)
    }

    @Test
    fun removeStrengthDropsTheLineWithoutSaving() = runBlocking {
        val exercise = seedLift()
        viewModel = composer("strength")
        val keepAlive = launch { viewModel!!.uiState.collect { } }
        try {
            viewModel!!.addStrength(exercise, 100.0, 5)
            assertEquals(1, viewModel!!.uiState.value.strength.size)
            viewModel!!.removeStrength(0)
            assertEquals(0, viewModel!!.uiState.value.strength.size)
        } finally {
            keepAlive.cancel()
        }
    }

    @Test
    fun futureDateIsClampedToToday() = runBlocking {
        viewModel = composer("cardio")
        val today = viewModel!!.uiState.value.epochDay
        viewModel!!.setEpochDay(today + 3)
        assertEquals(today, viewModel!!.uiState.value.epochDay)
    }

    private fun composer(mode: String) = ActivityComposerViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        SavedStateHandle(mapOf("mode" to mode)),
        deps,
    )

    private suspend fun seedLift(): Exercise {
        val saved = deps.exerciseRepository.createCustom("Squat", "Quads")
        check(saved is com.sinura.personaltrainer.data.repository.SaveExerciseResult.Saved)
        return saved.exercise
    }
}
