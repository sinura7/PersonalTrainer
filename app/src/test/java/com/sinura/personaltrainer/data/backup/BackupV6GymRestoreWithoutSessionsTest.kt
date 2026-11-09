package com.sinura.personaltrainer.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupV6GymRestoreWithoutSessionsTest {
    private lateinit var database: TemperDatabase
    private lateinit var local: LocalBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = TemperDatabase.inMemoryBuilder(context)
            .allowMainThreadQueries()
            .build()
        local = LocalBackupRepository(
            database = database,
            preferencesRepository = PreferencesRepository(context),
            activityDao = database.activityDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun v6RestoreWritesGymsBeforeActivitiesWhenSessionsAreEmpty() = runBlocking {
        val gym = BackupTestFixtures.v6Gym()
        val document = BackupDocument(
            version = 6,
            exportedAt = "2026-11-09T12:00:00Z",
            preferences = BackupPreferences(weightUnit = "kg"),
            exercises = emptyList(),
            routines = emptyList(),
            routineExercises = emptyList(),
            sessions = emptyList(),
            sessionExercises = emptyList(),
            setLogs = emptyList(),
            gyms = listOf(gym),
            activities = listOf(
                BackupActivity(
                    id = "act-1",
                    status = "COMPLETED",
                    origin = "BACKDATED",
                    source = "TEMPER",
                    title = "Easy run",
                    notes = "",
                    performedStart = BackupCapturedTime(1L, "UTC", 0, 20_000L),
                    performedEnd = BackupCapturedTime(2L, "UTC", 0, 20_000L),
                    templateId = null,
                    occurrenceId = null,
                    createdAtMs = 1L,
                    updatedAtMs = 1L,
                    revision = 1L,
                    blocks = emptyList(),
                ),
            ),
        )
        local.replaceRoom(document)
        val gyms = database.gymDao().getAll()
        assertEquals(1, gyms.size)
        assertEquals(gym.id, gyms.single().id)
        assertEquals(1, database.activityDao().getAllGraphs().size)
    }
}
