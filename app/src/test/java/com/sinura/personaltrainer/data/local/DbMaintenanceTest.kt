package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SeedMetaEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.MuscleNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The seeder, which is now allowed to run on every launch and therefore has to be safe to.
 *
 * v1's rule — seed only when the table is empty — was safe precisely because it almost never
 * fired. Upsert-by-id fires constantly, so every guarantee it makes needs a test: it must not
 * duplicate anything, must not delete anything, must not stamp over the owner's own notes, and
 * must not resolve a name collision on the user's behalf.
 */
@RunWith(RobolectricTestRunner::class)
class DbMaintenanceTest {

    private lateinit var database: TrainerDatabase
    private lateinit var maintenance: DbMaintenance

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        maintenance = DbMaintenance(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun seedIsIdempotent() = runBlocking {
        maintenance.seedCatalog()
        val firstExercises = database.exerciseDao().getAll().sortedBy { it.id }
        val firstCredits = database.catalogDao().getAllCredits()
            .sortedWith(compareBy({ it.exerciseId }, { it.muscleKey }))

        // Force a second full pass rather than letting the version check short-circuit it: the
        // claim under test is that reconciliation converges, not that it is skipped.
        database.catalogDao().upsertSeedMeta(SeedMetaEntity(id = 1, catalogVersion = 0))
        maintenance.seedCatalog()

        assertEquals(firstExercises, database.exerciseDao().getAll().sortedBy { it.id })
        assertEquals(
            firstCredits,
            database.catalogDao().getAllCredits()
                .sortedWith(compareBy({ it.exerciseId }, { it.muscleKey })),
        )
        assertEquals(DefaultExercises.catalog().size, firstExercises.size)
    }

    @Test
    fun upsertUpdatesBuiltInInPlacePreservingNotes() = runBlocking {
        // A v1-shaped row, as the migration would have left it: no equipment, no junction, and
        // the owner's own cue in the notes.
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "ex-barbell-back-squat",
                name = "Barbell Back Squat",
                muscleGroup = "Quads",
                notes = "brace before the walkout",
                isCustom = false,
                nameKey = "barbell back squat",
            ),
        )

        maintenance.seedCatalog()

        val row = database.exerciseDao().getById("ex-barbell-back-squat")!!
        assertEquals("brace before the walkout", row.notes)
        assertEquals("BARBELL", row.equipment)
        assertEquals("squat", row.movementKey)
        assertEquals(4, database.catalogDao().creditsFor(row.id).size)
    }

    @Test
    fun customCollisionIsInsertedAndFlagged() = runBlocking {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "ex-custom-curl",
                name = "barbell curl",
                muscleGroup = "Biceps",
                notes = "mine",
                isCustom = true,
                nameKey = "barbell curl",
            ),
        )

        maintenance.seedCatalog()

        // Both rows survive. The custom one is what the user's history points at; the built-in
        // is what this build ships. Neither is safe to remove on their behalf.
        assertNotNull(database.exerciseDao().getById("ex-custom-curl"))
        assertNotNull(database.exerciseDao().getById("ex-barbell-curl"))

        val meta = database.catalogDao().getSeedMeta()!!
        assertTrue(meta.pendingCollisions, meta.pendingCollisions.contains("ex-custom-curl"))
        assertTrue(meta.pendingCollisions, meta.pendingCollisions.contains("ex-barbell-curl"))
        assertTrue(meta.pendingCollisions, meta.pendingCollisions.contains("barbell curl"))
    }

    @Test
    fun seederNeverDeletesRows() = runBlocking {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "ex-custom-sled",
                name = "Sled Push",
                muscleGroup = "Legs",
                notes = "",
                isCustom = true,
            ),
        )
        maintenance.seedCatalog()
        database.catalogDao().upsertSeedMeta(SeedMetaEntity(id = 1, catalogVersion = 0))
        maintenance.seedCatalog()

        assertEquals(38, database.exerciseDao().getAll().size)
        assertNotNull(database.exerciseDao().getById("ex-custom-sled"))
    }

    @Test
    fun junctionDerivedForJunctionlessCustoms() = runBlocking {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "ex-custom-goodmorning",
                name = "Good Morning",
                muscleGroup = "Posterior chain",
                notes = "",
                isCustom = true,
            ),
        )

        maintenance.seedCatalog()

        val credits = database.catalogDao().creditsFor("ex-custom-goodmorning")
            .associate { it.muscleKey to it.weight }
        // The v1 derivation, written down: primary at 1.0, each derived secondary at 0.4.
        assertEquals(mapOf("back" to 1.0, "hamstrings" to 0.4, "glutes" to 0.4), credits)
        assertEquals("good morning", database.exerciseDao().getById("ex-custom-goodmorning")!!.nameKey)
    }

    @Test
    fun bumpingTheCatalogVersionAddsTheNewBatchAndLeavesCustomsAlone() = runBlocking {
        // The upgrade path this phase actually ships on: a phone sitting at the v2 catalog
        // (37 built-ins) that installs a build carrying v4 (98). The seeder has never had to
        // add rows to a populated table before — v3 was the first batch to do it — so the
        // claims worth pinning are that the new rows arrive, the old ones are not duplicated,
        // and the owner's own lifts are untouched by a catalog they are not part of.
        //
        // The v2-era table is built directly rather than seeded-then-pruned: this is the row
        // set the previous release actually left on disk.
        database.exerciseDao().insertAll(
            DefaultExercises.catalog().take(37).map { seed ->
                ExerciseEntity(
                    id = seed.id, name = seed.name, muscleGroup = seed.muscleGroup, notes = "",
                    isCustom = false, equipment = seed.equipment.name,
                    loadType = seed.loadType.name, movementKey = seed.movementKey,
                    imageKey = null, nameKey = MuscleNormalizer.nameKeyOf(seed.name),
                )
            },
        )
        database.catalogDao().upsertSeedMeta(SeedMetaEntity(id = 1, catalogVersion = 2))
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "custom-1", name = "My Own Lift", muscleGroup = "Chest", notes = "keep this",
                isCustom = true, equipment = "OTHER", loadType = "EXTERNAL", movementKey = null,
                imageKey = null, nameKey = "my own lift",
            ),
        )

        maintenance.seedCatalog()

        val all = database.exerciseDao().getAll()
        assertEquals(
            "every built-in plus the custom",
            DefaultExercises.catalog().size + 1,
            all.size,
        )
        assertEquals(DefaultExercises.catalog().size, all.count { !it.isCustom })
        assertEquals("ids must stay unique across a bump", all.size, all.map { it.id }.toSet().size)
        assertEquals(
            "the stored version must catch up",
            DefaultExercises.CATALOG_VERSION,
            database.catalogDao().getSeedMeta()?.catalogVersion,
        )

        val survivor = all.single { it.id == "custom-1" }
        assertEquals("keep this", survivor.notes)
        assertTrue(survivor.isCustom)

        // A batch-3 row is present, which is the whole point of the bump.
        assertNotNull(all.firstOrNull { it.id == "ex-hip-abduction-machine" })

        // And a second run changes nothing.
        maintenance.seedCatalog()
        assertEquals(
            DefaultExercises.catalog().size + 1,
            database.exerciseDao().getAll().size,
        )
    }

    @Test
    fun seedSkipsWhenCatalogVersionCurrent() = runBlocking {
        database.catalogDao().upsertSeedMeta(
            SeedMetaEntity(id = 1, catalogVersion = DefaultExercises.CATALOG_VERSION),
        )

        maintenance.seedCatalog()

        assertEquals("a current version must short-circuit", 0, database.exerciseDao().count())
    }

    @Test
    fun concurrentSeedAndRestoreSerialize() = runBlocking {
        // Two full passes launched at once. Without the mutex they interleave inside the same
        // tables and the catalog ends up duplicated or half-written.
        val jobs = (0 until 4).map {
            async(Dispatchers.IO) {
                database.catalogDao().upsertSeedMeta(SeedMetaEntity(id = 1, catalogVersion = 0))
                maintenance.seedCatalog()
            }
        }
        jobs.awaitAll()

        assertEquals(37, database.exerciseDao().getAll().size)
        val credits = database.catalogDao().getAllCredits()
        assertEquals(
            "no duplicate junction rows",
            credits.size,
            credits.map { it.exerciseId to it.muscleKey }.toSet().size,
        )
        assertEquals(
            DefaultExercises.CATALOG_VERSION,
            database.catalogDao().getSeedMeta()!!.catalogVersion,
        )
    }
}
