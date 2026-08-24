package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.BodyweightEntry
import com.sinura.personaltrainer.domain.TrainingBlock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
    fun beginBlockArchivesAFinishedBlockInRoom() = runBlocking {
        deps.preferencesRepository.beginBlock(TrainingBlock(20_000L, 12), 20_000L)
        deps.preferencesRepository.beginBlock(TrainingBlock(20_084L, 12), 20_084L)
        assertEquals(20_084L, deps.preferencesRepository.trainingBlock.first()?.startEpochDay)
        assertEquals(listOf(20_000L), deps.preferencesRepository.pastBlocks.first().map { it.startEpochDay })
        assertEquals(2, deps.database.trainingBlockDao().count())
    }

    @Test
    fun importCopiesEncodedDataStoreHistoryOnce() = runBlocking {
        val isolated = PreferencesRepository(
            ApplicationProvider.getApplicationContext(),
            androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                produceFile = {
                    java.io.File(
                        ApplicationProvider.getApplicationContext<Context>().cacheDir,
                        "isolated-prefs-${System.nanoTime()}.preferences_pb",
                    )
                },
            ),
        )
        isolated.recordBodyweight(80.0, 19_000L)
        isolated.beginBlock(TrainingBlock(19_000L, 12), 19_000L)

        // Production path: Room empty, leftover encoded strings.
        deps.preferencesRepository.importEncodedHistoryIfNeeded()
        assertTrue(deps.preferencesRepository.bodyweightLog.first().isEmpty())
        assertEquals(0, deps.database.bodyweightDao().count())
    }
}
