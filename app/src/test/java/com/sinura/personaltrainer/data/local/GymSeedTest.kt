package com.sinura.personaltrainer.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.dao.GymDao
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GymSeedTest {

    private lateinit var db: TemperDatabase
    private lateinit var gymDao: GymDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TemperDatabase::class.java,
        ).build()
        gymDao = db.gymDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun freshCreateSeedsFiveGyms() {
        GymSeed.insertAll(db.openHelper.writableDatabase, 1L)
        val gyms = runBlocking { gymDao.getAll() }
        assertEquals(5, gyms.size)
        assertEquals(GymSeed.ROWS.map { it.id }.toSet(), gyms.map { it.id }.toSet())
    }

    @Test
    fun seedingTwiceAddsNothing() {
        val sqlite = db.openHelper.writableDatabase
        GymSeed.insertAll(sqlite, 1L)
        GymSeed.insertAll(sqlite, 2L)
        assertEquals(5, runBlocking { gymDao.getAll() }.size)
    }
}

private fun <T> runBlocking(block: suspend () -> T): T =
    kotlinx.coroutines.runBlocking { block() }
