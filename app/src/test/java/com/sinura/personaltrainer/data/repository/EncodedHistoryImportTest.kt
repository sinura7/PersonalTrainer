package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.domain.TrainingBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class EncodedHistoryImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val store = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(context.cacheDir, "import-${System.nanoTime()}.preferences_pb") },
    )

    @After
    fun tearDown() {
        scope.cancel()
        database.close()
    }

    @Test
    fun importMovesEncodedStringsIntoEmptyRoomTables() = runBlocking {
        val writer = PreferencesRepository(context, store)
        writer.recordBodyweight(81.0, 20_010L)
        writer.beginBlock(TrainingBlock(20_000L, 12), 20_000L)
        writer.beginBlock(TrainingBlock(20_084L, 12), 20_084L)

        val reader = PreferencesRepository(
            context,
            store,
            bodyweightDao = database.bodyweightDao(),
            trainingBlockDao = database.trainingBlockDao(),
        )
        assertEquals(0, database.bodyweightDao().count())
        reader.importEncodedHistoryIfNeeded()
        assertEquals(listOf(20_010L), reader.bodyweightLog.first().map { it.epochDay })
        assertEquals(20_084L, reader.trainingBlock.first()?.startEpochDay)
        assertEquals(listOf(20_000L), reader.pastBlocks.first().map { it.startEpochDay })
    }
}
