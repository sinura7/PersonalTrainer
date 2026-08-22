package com.sinura.personaltrainer.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.domain.ScheduleConfidence
import com.sinura.personaltrainer.domain.SessionFocusKind
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import java.time.DayOfWeek
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ScheduleRepository against real Android SQLite.
 *
 * The week is a persisted thing, not a recompute: pin, swap and accept have to land as
 * rows the next process can still read, with the routine foreign key the cascade in
 * [com.sinura.personaltrainer.data.local.ScheduleSlotCascadeTest] already assumes.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleRepositoryInstrumentedTest {

    private lateinit var database: TrainerDatabase
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = ScheduleRepository(database.scheduleDao())
        runBlocking {
            database.routineDao().upsertRoutine(
                RoutineEntity(id = PUSH, name = "Push", notes = "", createdAt = STAMP, updatedAt = STAMP),
            )
            database.routineDao().upsertRoutine(
                RoutineEntity(id = PULL, name = "Pull", notes = "", createdAt = STAMP, updatedAt = STAMP),
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun pinWritesARoutineSlotAndUnpinRemovesIt() = runBlocking {
        val slot = repository.pin(routineId = PUSH, focusKind = null, anchorDay = DayOfWeek.MONDAY)

        val stored = repository.slots()
        assertEquals(1, stored.size)
        assertEquals(slot.id, stored.single().id)
        assertEquals(PUSH, stored.single().routineId)
        assertNull(stored.single().focusKind)
        assertEquals(DayOfWeek.MONDAY, stored.single().anchorDay)
        assertEquals(0, stored.single().position)

        repository.unpin(slot.id)
        assertTrue(repository.slots().isEmpty())
        assertTrue(repository.observeSlots().first().isEmpty())
    }

    @Test
    fun pinRefusesASlotThatIsBothOrNeither() = runBlocking {
        val both = runCatching {
            repository.pin(routineId = PUSH, focusKind = SessionFocusKind.PUSH, anchorDay = null)
        }
        val neither = runCatching {
            repository.pin(routineId = null, focusKind = null, anchorDay = DayOfWeek.WEDNESDAY)
        }

        assertTrue(both.isFailure)
        assertTrue(neither.isFailure)
        assertTrue(repository.slots().isEmpty())
    }

    @Test
    fun swapRoutineReplacesThePinAndClearsFocus() = runBlocking {
        val slot = repository.pin(
            routineId = null,
            focusKind = SessionFocusKind.LEGS,
            anchorDay = DayOfWeek.FRIDAY,
        )

        repository.swapRoutine(slot.id, PULL)

        val stored = repository.slots().single()
        assertEquals(PULL, stored.routineId)
        assertNull(stored.focusKind)
        assertEquals(DayOfWeek.FRIDAY, stored.anchorDay)
    }

    @Test
    fun acceptFillsAppendsAnchoredSlotsAndSkipsRestDays() = runBlocking {
        repository.pin(routineId = PUSH, focusKind = null, anchorDay = DayOfWeek.MONDAY)

        repository.acceptFills(
            listOf(
                fill(epochDay = 20_000, day = DayOfWeek.MONDAY, rest = true),
                fill(epochDay = 20_001, day = DayOfWeek.TUESDAY, rest = false, routineId = PULL),
                fill(
                    epochDay = 20_002,
                    day = DayOfWeek.WEDNESDAY,
                    rest = false,
                    routineId = null,
                    focusKind = SessionFocusKind.LEGS,
                ),
            ),
        )

        val slots = repository.observeSlots().first()
        assertEquals(3, slots.size)
        assertEquals(PUSH, slots[0].routineId)
        assertEquals(0, slots[0].position)
        assertEquals(PULL, slots[1].routineId)
        assertEquals(DayOfWeek.TUESDAY, slots[1].anchorDay)
        assertEquals(1, slots[1].position)
        assertNull(slots[2].routineId)
        assertEquals(SessionFocusKind.LEGS, slots[2].focusKind)
        assertEquals(DayOfWeek.WEDNESDAY, slots[2].anchorDay)
        assertEquals(2, slots[2].position)
    }

    private fun fill(
        epochDay: Long,
        day: DayOfWeek,
        rest: Boolean,
        routineId: String? = null,
        focusKind: SessionFocusKind = SessionFocusKind.FULL_BODY,
    ) = SuggestedTrainingDay(
        epochDay = epochDay,
        dayOfWeek = day,
        isRest = rest,
        focusKind = focusKind,
        focusTitle = focusKind.label,
        routineId = routineId,
        routineName = routineId,
        reason = "test",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private companion object {
        const val PUSH = "routine-push"
        const val PULL = "routine-pull"
        const val STAMP = 1_700_000_000_000L
    }
}
