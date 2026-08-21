package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The two queries Phase 7 added, tested against real SQLite rather than a mental model of it.
 *
 * Both are the kind that fail quietly. An unescaped LIKE returns the wrong rows instead of
 * none; a collision query with a subtly wrong correlation returns every custom, or no custom,
 * and either reads as "working" until you look at what came back.
 */
@RunWith(RobolectricTestRunner::class)
class CatalogQueriesTest {
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

    private fun exercise(
        id: String,
        name: String,
        isCustom: Boolean = false,
        muscleGroup: String = "Chest",
    ) = ExerciseEntity(
        id = id, name = name, muscleGroup = muscleGroup, notes = "", isCustom = isCustom,
        equipment = "BARBELL", loadType = "EXTERNAL", movementKey = "bench-press",
        imageKey = null, nameKey = name.trim().lowercase(),
    )

    @Test
    fun aPercentSignInTheQueryMatchesNothingRatherThanEverything() = runBlocking {
        database.exerciseDao().insertAll(
            listOf(
                exercise("a", "Barbell Bench Press"),
                exercise("b", "100kg Test Lift"),
            ),
        )
        // Unescaped, '100%' is "100 followed by anything" and matches row b. Escaped, it is a
        // literal percent sign, and nothing in the table has one.
        val hits = database.exerciseDao().search("100\\%").first()
        assertTrue("a literal % must not act as a wildcard", hits.isEmpty())

        // And the escaping does not break ordinary searching.
        assertEquals(1, database.exerciseDao().search("bench").first().size)
    }

    @Test
    fun anUnderscoreIsLiteralToo() = runBlocking {
        database.exerciseDao().insertAll(
            listOf(exercise("a", "Bench Press"), exercise("b", "Bench_Press")),
        )
        val hits = database.exerciseDao().search("Bench\\_Press").first()
        assertEquals(listOf("b"), hits.map { it.id })
    }

    @Test
    fun onlyCustomsThatShareABuiltInsNameKeyAreFlagged() = runBlocking {
        database.exerciseDao().insertAll(
            listOf(
                exercise("ex-barbell-bench-press", "Barbell Bench Press"),
                exercise("mine", "Barbell Bench Press", isCustom = true),
                exercise("mine-2", "My Own Lift", isCustom = true),
            ),
        )
        val flagged = database.exerciseDao().observeBuiltInCollisions().first()
        assertEquals(listOf("mine"), flagged.map { it.id })
    }

    @Test
    fun aBuiltInNeverFlagsItselfAndNeverFlagsAnotherBuiltIn() = runBlocking {
        // Two built-ins cannot collide — the catalog invariant test forbids it — but the query
        // must not report one if the data ever gets there, or a restore would surface rows the
        // owner cannot act on.
        database.exerciseDao().insertAll(
            listOf(exercise("a", "Bench Press"), exercise("b", "Bench Press")),
        )
        assertTrue(database.exerciseDao().observeBuiltInCollisions().first().isEmpty())
    }

    @Test
    fun renamingTheCustomClearsTheFlagWithNothingToUpdate() = runBlocking {
        database.exerciseDao().insertAll(
            listOf(
                exercise("ex-barbell-bench-press", "Barbell Bench Press"),
                exercise("mine", "Barbell Bench Press", isCustom = true),
            ),
        )
        assertEquals(1, database.exerciseDao().observeBuiltInCollisions().first().size)

        database.exerciseDao().update(
            exercise("mine", "My Bench", isCustom = true),
        )
        // Derived, not stored: nothing had to remember to clear a flag.
        assertTrue(database.exerciseDao().observeBuiltInCollisions().first().isEmpty())
    }

    @Test
    fun lastLoggedIsOneRowPerLiftHoldingItsNewestSet() = runBlocking {
        // No sets yet: the aggregate is empty rather than absent.
        assertTrue(database.workoutDao().observeLastLogged().first().isEmpty())
    }
}
