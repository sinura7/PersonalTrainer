package com.sinura.personaltrainer.data.repository

import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GymRepositoryTest {

    private lateinit var db: TemperDatabase
    private lateinit var repo: GymRepository

    @Before
    fun setUp() {
        db = TemperDatabase.inMemoryBuilder(ApplicationProvider.getApplicationContext())
            .allowMainThreadQueries()
            .build()
        repo = GymRepository(db, db.gymDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun addValidatesUniqueness() = runBlocking {
        val first = repo.add("Alpha Gym", "Alpha", "AL")
        assertTrue(first is GymSaveOutcome.Saved)
        val dup = repo.add("alpha gym", "Other", "OT")
        assertEquals(GymSaveOutcome.NameTaken, dup)
    }

    @Test
    fun archiveRefusesLastActive() = runBlocking {
        val gyms = db.gymDao().getAll().filter { it.archivedAtMs == null }
        assertEquals(5, gyms.size)
        gyms.drop(1).forEach { runBlocking { repo.archive(it.id) } }
        val last = db.gymDao().getAll().single { it.archivedAtMs == null }
        assertEquals(GymArchiveOutcome.LastActiveGym, repo.archive(last.id))
    }
}
