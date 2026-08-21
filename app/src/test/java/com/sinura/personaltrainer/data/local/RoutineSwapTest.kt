package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.repository.RoutineRepository
import com.sinura.personaltrainer.domain.Exercise
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Swapping a lift in a routine keeps the plan and changes only the implement.
 *
 * The claim worth testing is what SURVIVES, not what changes: position and targets. A swap
 * implemented as remove-then-add would pass a "the new lift is there" assertion and still be
 * useless, because the row would have moved to the bottom with its targets reset.
 */
@RunWith(RobolectricTestRunner::class)
class RoutineSwapTest {
    private lateinit var database: TrainerDatabase
    private lateinit var routines: RoutineRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        routines = RoutineRepository(database.routineDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(id: String, name: String) = ExerciseEntity(
        id = id, name = name, muscleGroup = "Chest", notes = "", isCustom = false,
        equipment = "BARBELL", loadType = "EXTERNAL", movementKey = "bench-press",
        imageKey = null, nameKey = name.lowercase(),
    )

    private fun domain(id: String, name: String) = Exercise(
        id = id, name = name, muscleGroup = "Chest", notes = "", isCustom = false,
    )

    private suspend fun seed() {
        database.exerciseDao().insertAll(
            listOf(
                entity("ex-barbell-bench-press", "Barbell Bench Press"),
                entity("ex-machine-chest-press", "Machine Chest Press"),
                entity("ex-dumbbell-bench-press", "Dumbbell Bench Press"),
            ),
        )
        database.routineDao().upsertRoutine(
            RoutineEntity(id = "r1", name = "Push", notes = "", createdAt = 0L, updatedAt = 0L),
        )
        database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "item-1", routineId = "r1", exerciseId = "ex-barbell-bench-press",
                sortOrder = 2, targetSets = 4, targetReps = 8, targetWeightKg = 90.0,
                restSeconds = 120,
            ),
        )
    }

    @Test
    fun theRowKeepsItsPositionAndTargets() = runBlocking {
        seed()
        val refusal = routines.swapExercise("r1", "item-1", domain("ex-machine-chest-press", "Machine Chest Press"))
        assertNull(refusal)

        val item = database.routineDao().getById("r1")!!.items.single()
        assertEquals("ex-machine-chest-press", item.exercise.id)
        assertEquals("the row must not move", 2, item.item.sortOrder)
        assertEquals(4, item.item.targetSets)
        assertEquals(8, item.item.targetReps)
        assertEquals(120, item.item.restSeconds)
        // The one thing deliberately dropped: a weight chosen for a barbell is not a starting
        // point on a machine.
        assertNull(item.item.targetWeightKg)
    }

    @Test
    fun swappingToSomethingTheRoutineAlreadyHasIsRefused() = runBlocking {
        seed()
        database.routineDao().upsertRoutineExercise(
            RoutineExerciseEntity(
                id = "item-2", routineId = "r1", exerciseId = "ex-dumbbell-bench-press",
                sortOrder = 3, targetSets = 3, targetReps = 10, targetWeightKg = null,
                restSeconds = 90,
            ),
        )
        val refusal = routines.swapExercise("r1", "item-1", domain("ex-dumbbell-bench-press", "Dumbbell Bench Press"))
        assertNotNull("a duplicate must be refused with a reason", refusal)
        // And nothing changed.
        assertEquals(
            "ex-barbell-bench-press",
            database.routineDao().getById("r1")!!.items.single { it.item.id == "item-1" }.exercise.id,
        )
    }

    @Test
    fun aMissingRoutineOrRowIsRefusedRatherThanIgnored() = runBlocking {
        seed()
        assertNotNull(routines.swapExercise("nope", "item-1", domain("ex-machine-chest-press", "Machine Chest Press")))
        assertNotNull(routines.swapExercise("r1", "nope", domain("ex-machine-chest-press", "Machine Chest Press")))
    }
}
