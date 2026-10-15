package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.WeightUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PreferencesRoomHistoryTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext<Context>())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun writingHeatWindowDoesNotReEmitWeightUnit() = runBlocking {
        val emissions = mutableListOf<WeightUnit>()
        val job = launch { deps.preferencesRepository.weightUnit.collect { emissions.add(it) } }
        while (emissions.isEmpty()) delay(10)
        val before = emissions.size
        deps.preferencesRepository.setHeatWindow(HeatWindow.CURRENT_MONTH)
        repeat(20) { delay(10) }
        assertEquals(before, emissions.size)
        job.cancel()
    }

    @Test
    fun recordBodyweightWritesRoomAndReadsItBack() = runBlocking {
        deps.preferencesRepository.recordBodyweight(82.5, 20_000L)
        val log = deps.preferencesRepository.bodyweightLog.first()
        assertEquals(1, log.size)
        assertEquals(20_000L, log.single().epochDay)
        assertEquals(82.5, log.single().kg, 0.0)
        assertTrue(log.single().zoneId.isNotBlank())
        assertEquals(1, deps.database.bodyweightDao().count())
    }

    @Test
    fun correctingAnOlderDayKeepsTheNewestAsCurrentAndDeleteRemovesOnlyThatDay() = runBlocking {
        deps.preferencesRepository.recordBodyweight(78.0, 20_000L)
        deps.preferencesRepository.recordBodyweight(82.0, 20_010L)
        deps.preferencesRepository.recordBodyweight(79.0, 20_000L)
        val log = deps.preferencesRepository.bodyweightLog.first()
        assertEquals(79.0, log.first { it.epochDay == 20_000L }.kg, 0.0)
        assertEquals(82.0, deps.preferencesRepository.bodyweightKg.first()!!, 0.0)
        deps.preferencesRepository.deleteBodyweightDay(20_010L)
        assertEquals(listOf(20_000L), deps.preferencesRepository.bodyweightLog.first().map { it.epochDay })
        assertEquals(79.0, deps.preferencesRepository.bodyweightKg.first()!!, 0.0)
        deps.preferencesRepository.deleteBodyweightDay(20_000L)
        assertTrue(deps.preferencesRepository.bodyweightLog.first().isEmpty())
        assertNull(deps.preferencesRepository.bodyweightKg.first())
        deps.preferencesRepository.recordBodyweight(78.0, 20_000L)
        deps.preferencesRepository.recordBodyweight(82.0, 20_010L)
        deps.preferencesRepository.setBodyweightKg(null)
        deps.preferencesRepository.recordBodyweight(79.0, 20_000L)
        assertNull("clearing the current figure survives correcting an older day", deps.preferencesRepository.bodyweightKg.first())
    }

    @Test
    fun beginBlockArchivesAFinishedBlockInRoom() = runBlocking {
        deps.preferencesRepository.beginBlock(TrainingBlock(20_000L, 12), 20_000L)
        deps.preferencesRepository.beginBlock(TrainingBlock(20_084L, 12), 20_084L)
        assertEquals(20_084L, deps.preferencesRepository.trainingBlock.first()?.startEpochDay)
        assertEquals(listOf(20_000L), deps.preferencesRepository.pastBlocks.first().map { it.startEpochDay })
        assertEquals(2, deps.database.trainingBlockDao().count())
    }

    @Test
    fun importCopiesEncodedDataStoreHistoryOnce() = runBlocking {
        val store = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = {
                java.io.File(
                    ApplicationProvider.getApplicationContext<Context>().cacheDir,
                    "isolated-prefs-${System.nanoTime()}.preferences_pb",
                )
            },
        )
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isolated = PreferencesRepository(context, store)
        isolated.recordBodyweight(80.0, 19_000L)
        isolated.beginBlock(TrainingBlock(19_000L, 12), 19_000L)
        val room = PreferencesRepository(context, store, deps.database.bodyweightDao(), deps.database.trainingBlockDao())
        room.importEncodedHistoryIfNeeded()
        assertEquals(listOf(19_000L), room.bodyweightLog.first().map { it.epochDay })
        room.deleteBodyweightDay(19_000L)
        room.importEncodedHistoryIfNeeded()
        assertTrue(room.bodyweightLog.first().isEmpty())
        assertNull(room.bodyweightKg.first())
    }
}
