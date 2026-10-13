package com.sinura.personaltrainer.ui.history

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.clearAndJoinForTest
import com.sinura.personaltrainer.data.draft.AtomicDraftFiles
import com.sinura.personaltrainer.data.draft.DraftFileIO
import com.sinura.personaltrainer.data.draft.DurableDraftStore
import com.sinura.personaltrainer.data.draft.HistoryPeriodDrafts
import com.sinura.personaltrainer.domain.AnalyticsHorizon
import com.sinura.personaltrainer.domain.HistoryPeriodSelection
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual VM construction and private files across a copied framework handle. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HistoryPeriodRestorationTest {
    @get:Rule val temporary = TemporaryFolder()
    private val main = UnconfinedTestDispatcher()
    private val io = StandardTestDispatcher(main.scheduler)
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<HistoryViewModel>()
    private val historical = HistoryPeriodSelection(AnalyticsHorizon.YEAR, 19723L, false)

    @Before fun setUp() {
        Dispatchers.setMain(main)
        deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext(), scheduler = main)
    }

    @After fun tearDown() {
        models.forEach(::retire)
        runBlocking { models.forEach { it.clearAndJoinForTest() } }
        main.scheduler.advanceUntilIdle()
        deps.close()
        Dispatchers.resetMain()
    }

    @Test fun recreationDuringPrivateReadDoesNotPromoteTheFallbackOrOverwriteTheStoredPeriod() = runBlocking {
        val root = seededPreference()
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, io))
        val firstHandle = SavedStateHandle()
        val first = model(firstHandle, drafts)
        assertFalse(first.periodNavigationReady.value)
        assertTrue(firstHandle.keys().isEmpty())
        val recreatedHandle = copy(firstHandle)
        retire(first)
        val recreated = model(recreatedHandle, drafts)
        main.scheduler.advanceUntilIdle()
        assertEquals(historical, recreated.requestedPeriod.value)
        assertEquals(historical, recreated.resumedPeriod.value)
        assertTrue(recreated.periodNavigationReady.value)
        assertEquals(historical, HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered)
        assertEquals("YEAR", recreatedHandle.get<String>("history.period.horizon"))
    }

    @Test fun aFailedReadAndRecreationRetainThePreferenceUntilSuccessfulRetry() = runBlocking {
        val root = seededPreference()
        var fail = true
        val files = object : DraftFileIO by AtomicDraftFiles {
            override fun read(file: File): ByteArray? {
                if (fail) throw IOException("synthetic unavailable private storage")
                return AtomicDraftFiles.read(file)
            }
        }
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, io, files))
        val handle = SavedStateHandle()
        val first = model(handle, drafts)
        main.scheduler.advanceUntilIdle()
        assertNotNull(first.periodMemory.snapshot.value.error)
        assertTrue(handle.keys().isEmpty())
        val restoredHandle = copy(handle)
        retire(first)
        val recreated = model(restoredHandle, drafts)
        main.scheduler.advanceUntilIdle()
        assertNotNull(recreated.periodMemory.snapshot.value.error)
        assertTrue(restoredHandle.keys().isEmpty())
        fail = false
        recreated.periodMemory.retry()
        main.scheduler.advanceUntilIdle()
        assertEquals(historical, recreated.requestedPeriod.value)
        assertEquals(historical, HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered)
    }

    @Test fun anExplicitChoiceWhilePrivateReadIsPendingWinsAndReopensExactly() = runBlocking {
        val root = seededPreference()
        val drafts = HistoryPeriodDrafts(DurableDraftStore(root, io))
        val handle = SavedStateHandle()
        val first = model(handle, drafts)
        first.selectDay(20000L)
        val chosen = HistoryPeriodSelection(AnalyticsHorizon.DAY, 20000L, false)
        assertEquals(chosen, first.requestedPeriod.value)
        val restoredHandle = copy(handle)
        retire(first)
        val recreated = model(restoredHandle, drafts)
        main.scheduler.advanceUntilIdle()
        assertEquals(chosen, recreated.requestedPeriod.value)
        assertEquals(chosen, HistoryPeriodDrafts(DurableDraftStore(root)).open().remembered)
    }

    private suspend fun seededPreference(): File = temporary.newFolder().also { root ->
        val seed = HistoryPeriodDrafts(DurableDraftStore(root))
        seed.keep(seed.open(), historical)
    }

    private fun model(handle: SavedStateHandle, drafts: HistoryPeriodDrafts) = HistoryViewModel(
        ApplicationProvider.getApplicationContext(), handle, deps, drafts,
    ).also(models::add)

    private fun copy(handle: SavedStateHandle) = SavedStateHandle(
        handle.keys().associateWith { handle.get<Any?>(it) },
    )

    private fun retire(model: HistoryViewModel) {
        model.viewModelScope.coroutineContext[Job]?.cancel()
        // Cancellation of queued IO also needs the test dispatcher to run its continuation.
        main.scheduler.runCurrent()
    }
}
