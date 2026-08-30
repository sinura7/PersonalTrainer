package com.sinura.personaltrainer.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.CatalogMeta
import com.sinura.personaltrainer.domain.MuscleNormalizer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Create / rename / delete / search are the paths that fail quietly: a
 * duplicate nameKey would split history across two rows, a LIKE wildcard
 * would return the wrong catalog, and deleting a built-in would orphan sets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ExerciseRepositoryTest {
    private lateinit var deps: FakeAppDependencies
    private lateinit var repository: ExerciseRepository

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        repository = deps.exerciseRepository
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun createCustomStoresNameKeyAndDerivedCredits() = runBlocking {
        val result = repository.createCustom(name = "  My Squat  ", muscleGroup = "Quads")
        val saved = (result as SaveExerciseResult.Saved).exercise
        assertTrue(saved.id.startsWith("ex-custom-"))
        assertEquals("My Squat", saved.name)
        assertEquals("Quads", saved.muscleGroup)
        assertTrue(saved.isCustom)
        assertEquals(MuscleNormalizer.deriveCredits("Quads"), saved.muscles)
        val row = deps.database.exerciseDao().getById(saved.id)
        assertEquals(MuscleNormalizer.nameKeyOf("My Squat"), row?.nameKey)
    }

    @Test
    fun createCustomRefusesANameAnotherLiftAlreadyAnswersTo() = runBlocking {
        insertBuiltIn(id = "ex-bench", name = "Bench Press")
        val clash = repository.createCustom(name = "bench press", muscleGroup = "Chest")
        val duplicate = clash as SaveExerciseResult.DuplicateName
        assertEquals("ex-bench", duplicate.existing.id)
    }

    @Test
    fun createCustomRefusesABlankMuscleGroup() = runBlocking {
        assertEquals(
            SaveExerciseResult.MissingMuscle,
            repository.createCustom(name = "Hang", muscleGroup = "  "),
        )
        assertEquals(0, deps.database.exerciseDao().count())
    }

    @Test
    fun hyphenatedAndSpacedNamesAreDifferentLifts() = runBlocking {
        val first = repository.createCustom(name = "Push-Up", muscleGroup = "Chest")
        val second = repository.createCustom(name = "Push Up", muscleGroup = "Chest")
        assertTrue(first is SaveExerciseResult.Saved)
        assertTrue(second is SaveExerciseResult.Saved)
        assertTrue(
            (first as SaveExerciseResult.Saved).exercise.id !=
                (second as SaveExerciseResult.Saved).exercise.id,
        )
    }

    @Test
    fun updateCustomReDerivesCreditsWhenTheGroupChanges() = runBlocking {
        val saved = (repository.createCustom(name = "Hang", muscleGroup = "Chest") as SaveExerciseResult.Saved)
            .exercise
        val updated = repository.updateCustom(
            id = saved.id,
            name = "Hang",
            muscleGroup = "Quads",
            notes = "now a squat",
        ) as SaveExerciseResult.Saved
        assertEquals("Quads", updated.exercise.muscleGroup)
        assertEquals("now a squat", updated.exercise.notes)
        assertEquals(MuscleNormalizer.deriveCredits("Quads"), updated.exercise.muscles)
        assertEquals(
            MuscleNormalizer.deriveCredits("Quads"),
            repository.getById(saved.id)?.muscles,
        )
    }

    @Test
    fun updateCustomOnABuiltInOrMissingIdIsNull() = runBlocking {
        insertBuiltIn(id = "ex-bench", name = "Bench Press")
        assertNull(
            repository.updateCustom(
                id = "ex-bench",
                name = "Bench",
                muscleGroup = "Chest",
                notes = "",
            ),
        )
        assertNull(
            repository.updateCustom(
                id = "gone",
                name = "Gone",
                muscleGroup = "Chest",
                notes = "",
            ),
        )
    }

    @Test
    fun updateCustomRefusesANameThatCollidesWithAnotherLift() = runBlocking {
        insertBuiltIn(id = "ex-bench", name = "Bench Press")
        val custom = (repository.createCustom(name = "My Lift", muscleGroup = "Chest") as SaveExerciseResult.Saved)
            .exercise
        val clash = repository.updateCustom(
            id = custom.id,
            name = "bench press",
            muscleGroup = "Chest",
            notes = "",
        ) as SaveExerciseResult.DuplicateName
        assertEquals("ex-bench", clash.existing.id)
    }

    @Test
    fun deleteCustomRefusesBuiltInsMissingIdsAndInUseRows() = runBlocking {
        insertBuiltIn(id = "ex-bench", name = "Bench Press")
        assertEquals(DeleteExerciseResult.NotCustom, repository.deleteCustom("ex-bench"))
        assertEquals(DeleteExerciseResult.Missing, repository.deleteCustom("gone"))

        val inRoutine = (repository.createCustom(name = "Routine lift", muscleGroup = "Chest") as SaveExerciseResult.Saved)
            .exercise
        val routine = deps.routineRepository.create(name = "Push")
        deps.routineRepository.addExercise(
            routineId = routine.id,
            exercise = inRoutine,
            targetSets = 3,
            targetReps = 5,
            targetWeightKg = 60.0,
            restSeconds = 90,
        )
        val blockedByRoutine = repository.deleteCustom(inRoutine.id) as DeleteExerciseResult.InUse
        assertEquals(1, blockedByRoutine.usage.routineCount)

        val logged = (repository.createCustom(name = "Logged lift", muscleGroup = "Chest") as SaveExerciseResult.Saved)
            .exercise
        insertSessionWithSet(exerciseId = logged.id)
        val blockedByHistory = repository.deleteCustom(logged.id) as DeleteExerciseResult.InUse
        assertTrue(blockedByHistory.usage.historySetCount >= 1)

        val free = (repository.createCustom(name = "Free lift", muscleGroup = "Chest") as SaveExerciseResult.Saved)
            .exercise
        assertEquals(DeleteExerciseResult.Deleted, repository.deleteCustom(free.id))
        assertNull(deps.database.exerciseDao().getById(free.id))
    }

    @Test
    fun searchEscapesLikeWildcardsAndUnionsCatalogAliases() = runBlocking {
        insertBuiltIn(id = "ex-overhead-press", name = "Overhead Press", muscleGroup = "Shoulders")
        insertBuiltIn(id = "ex-100", name = "100kg Test Lift")
        insertBuiltIn(id = "ex-bench", name = "Barbell Bench Press")

        assertTrue(CatalogMeta.matchesSearchTerms("ohp", "ex-overhead-press"))
        val aliasHits = repository.search("ohp").first()
        assertEquals(listOf("ex-overhead-press"), aliasHits.map { it.id })

        val percentHits = repository.search("100%").first()
        assertTrue(percentHits.none { it.id == "ex-100" })

        val benchHits = repository.search("bench").first()
        assertEquals(listOf("ex-bench"), benchHits.map { it.id })
    }

    @Test
    fun observeNameCollisionsFlagsOnlyCustomsSharingABuiltInKey() = runBlocking {
        insertBuiltIn(id = "ex-bench", name = "Barbell Bench Press")
        insertCustom(id = "mine", name = "Barbell Bench Press")
        insertCustom(id = "other", name = "My Own Lift")
        val flagged = repository.observeNameCollisions().first()
        assertEquals(listOf("mine"), flagged.map { it.id })
    }

    @Test
    fun aSessionExerciseWithoutSetsStillBlocksDelete() = runBlocking {
        val custom = (repository.createCustom(name = "Queued lift", muscleGroup = "Chest") as SaveExerciseResult.Saved)
            .exercise
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "sess-1",
                routineId = null,
                routineName = "Live",
                date = 1L,
                notes = "",
                durationMinutes = 0,
                startedAt = 1L,
                finishedAt = null,
            ),
        )
        deps.database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = "se-1",
                sessionId = "sess-1",
                exerciseId = custom.id,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = null,
                restSeconds = 90,
            ),
        )
        val blocked = repository.deleteCustom(custom.id) as DeleteExerciseResult.InUse
        assertEquals(1, blocked.usage.sessionCount)
        assertEquals(0, blocked.usage.historySetCount)
    }

    private suspend fun insertBuiltIn(
        id: String,
        name: String,
        muscleGroup: String = "Chest",
    ) {
        deps.database.exerciseDao().insert(
            ExerciseEntity(
                id = id,
                name = name,
                muscleGroup = muscleGroup,
                notes = "",
                isCustom = false,
                nameKey = MuscleNormalizer.nameKeyOf(name),
            ),
        )
    }

    private suspend fun insertCustom(id: String, name: String) {
        deps.database.exerciseDao().insert(
            ExerciseEntity(
                id = id,
                name = name,
                muscleGroup = "Chest",
                notes = "",
                isCustom = true,
                nameKey = MuscleNormalizer.nameKeyOf(name),
            ),
        )
    }

    private suspend fun insertSessionWithSet(exerciseId: String) {
        deps.database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "sess-log",
                routineId = null,
                routineName = "Live",
                date = 1L,
                notes = "",
                durationMinutes = 40,
                startedAt = 1L,
                finishedAt = 2L,
            ),
        )
        deps.database.workoutDao().insertSet(
            SetLogEntity(
                id = "set-1",
                sessionId = "sess-log",
                exerciseId = exerciseId,
                setNumber = 1,
                weightKg = 60.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAt = 1L,
            ),
        )
    }
}
