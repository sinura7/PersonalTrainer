package com.sinura.personaltrainer.ui.progress

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.domain.ActivityBlock
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CapturedCivilTime
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.IdPort
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.StrengthBlock
import com.sinura.personaltrainer.domain.StrengthSet
import com.sinura.personaltrainer.util.JvmTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F1: Body weekly volume uses workout and activity repository lanes together. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class BodyWeekLanesTest {
    private lateinit var deps: FakeAppDependencies
    private val squat = "ex-squat"
    private val bench = "ex-bench"
    private val performed = CapturedCivilTime(
        instantMillis = SINCE + 10,
        zoneId = JvmTime.defaultZoneId(),
        offsetSeconds = 0,
        localEpochDay = 20_000L,
    )

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        runBlocking {
            deps.database.exerciseDao().insertAll(
                listOf(
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = squat,
                        name = "Squat",
                        muscleGroup = "Legs",
                        notes = "",
                        isCustom = false,
                    ),
                    com.sinura.personaltrainer.data.local.entity.ExerciseEntity(
                        id = bench,
                        name = "Bench",
                        muscleGroup = "Chest",
                        notes = "",
                        isCustom = false,
                    ),
                ),
            )
        }
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun repositoriesMatchProgressLaneMerge() = runBlocking {
        insertWorkoutSets()
        val accepted = deps.activityRepository.confirm(
            draft = draft(listOf(benchBlock())),
            now = performed,
            ids = ids(),
            clock = JvmTime,
        )
        assertTrue(accepted is ActivityWrite.Accepted)
        val merged =
            deps.workoutRepository.workingSetCountsSince(SINCE) +
                deps.activityRepository.workingSetCountsSince(SINCE)
        assertEquals(4, merged.sumOf { it.workingSets })
        assertTrue(merged.any { it.exerciseId == squat && it.routineName == "Push" })
        assertTrue(merged.any { it.exerciseId == bench })
    }

    private suspend fun insertWorkoutSets() {
        deps.database.workoutDao().upsertSession(
            com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity(
                id = "w1",
                routineId = null,
                routineName = "Push",
                date = SINCE,
                notes = "",
                durationMinutes = 30,
                startedAt = SINCE,
                finishedAt = SINCE + 1,
            ),
        )
        repeat(3) { idx ->
            deps.database.workoutDao().insertSet(
                com.sinura.personaltrainer.data.local.entity.SetLogEntity(
                    id = "w1-$idx",
                    sessionId = "w1",
                    exerciseId = squat,
                    setNumber = idx + 1,
                    weightKg = 100.0,
                    reps = 5,
                    rpe = null,
                    isWarmup = false,
                    completedAt = SINCE + 1 + idx,
                ),
            )
        }
    }

    private fun benchBlock() = StrengthBlock(
        id = "blk-bench",
        sortOrder = 0,
        exerciseId = bench,
        exerciseName = "Bench",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = "set-bench",
                setNumber = 1,
                weightKg = 80.0,
                reps = 8,
                rpe = null,
                isWarmup = false,
                completedAtMs = performed.instantMillis,
            ),
        ),
    )

    private fun draft(blocks: List<ActivityBlock>) = ActivityDraft(
        status = ActivityStatus.COMPLETED,
        origin = ActivityOrigin.BACKDATED,
        title = "Mixed",
        performedStart = performed,
        blocks = blocks,
    )

    private fun ids(): IdPort {
        var n = 0
        return IdPort { "id-${++n}" }
    }

    private companion object {
        const val SINCE = 1_700_000_000_000L
    }
}
