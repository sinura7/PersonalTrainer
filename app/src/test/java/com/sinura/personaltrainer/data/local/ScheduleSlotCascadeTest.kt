package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Deleting a routine takes its pins with it.
 *
 * The derived week assumes this: it heals from a deleted routine by simply not finding the
 * slot, and if the cascade were not real the slot would survive pointing at nothing and the
 * week would show a day whose routine cannot be resolved. That is the "phantom name" the
 * healing test in `WeekDerivationTest` simulates — this is the proof the simulation matches
 * what SQLite actually does.
 *
 * Runs only under `./gradlew testDebugUnitTest` (Phase-2 Robolectric lane).
 */
@RunWith(RobolectricTestRunner::class)
class ScheduleSlotCascadeTest {

    private lateinit var database: TrainerDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun scheduleSlotCascadeOnRoutineDelete() = runBlocking {
        database.routineDao().upsertRoutine(RoutineEntity("r1", "Push", "", STAMP, STAMP))
        database.routineDao().upsertRoutine(RoutineEntity("r2", "Pull", "", STAMP, STAMP))
        database.scheduleDao().replaceAll(
            listOf(
                slot(id = "slot-1", position = 0, routineId = "r1"),
                slot(id = "slot-2", position = 1, routineId = "r2"),
                slot(id = "slot-3", position = 2, routineId = null, focusKind = "LEGS"),
            ),
        )
        assertEquals(3, database.scheduleDao().count())

        database.routineDao().deleteRoutine("r1")

        val remaining = database.scheduleDao().getAll()
        assertEquals(2, remaining.size)
        assertTrue("the deleted routine's slot must go with it", remaining.none { it.id == "slot-1" })
        // A focus-only slot has no routine to lose, so it is untouched.
        assertTrue(remaining.any { it.id == "slot-3" })
    }

    private fun slot(
        id: String,
        position: Int,
        routineId: String?,
        focusKind: String? = null,
    ): ScheduleSlotEntity = ScheduleSlotEntity(
        id = id,
        position = position,
        routineId = routineId,
        focusKind = focusKind,
        anchorDay = 0,
        createdAt = STAMP,
        updatedAt = STAMP,
    )

    private companion object {
        const val STAMP = 1_700_000_000_000L
    }
}
