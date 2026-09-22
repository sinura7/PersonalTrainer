package com.sinura.personaltrainer.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.data.local.entity.ActivityBlockEntity
import com.sinura.personaltrainer.data.local.entity.ActivityCardioIntervalEntity
import com.sinura.personaltrainer.data.local.entity.ActivitySessionEntity
import com.sinura.personaltrainer.data.local.entity.ActivityStrengthSetEntity
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.RoutineExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.data.repository.prefs.SettingsStore
import com.sinura.personaltrainer.domain.SyncEntityType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * A pulled update changes that row and nothing under it. A pulled delete still takes the rows
 * under it, as it should.
 *
 * The update cases here lost data before packet S0b, by one of two mechanisms. REPLACE (and the
 * session's delete-then-insert) removes the old row first, so ON DELETE CASCADE takes its
 * children: an activity's blocks and sets, a template's blocks, a routine's lifts, and the
 * routine link on finished workouts. ABORT throws on a row that is already here, and a throw
 * stops every table after it, on every pass, for good.
 */
@RunWith(RobolectricTestRunner::class)
class SyncPullInPlaceTest {
    private lateinit var database: TemperDatabase
    private lateinit var remote: FakeSyncRemote
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TemperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        remote = FakeSyncRemote()
        val prefsFile = File(context.filesDir, "sync-in-place-test-${System.nanoTime()}.preferences_pb")
        engine = SyncEngine(
            syncDao = database.syncDao(),
            activityDao = database.activityDao(),
            plannerDao = database.plannerDao(),
            routineDao = database.routineDao(),
            exerciseDao = database.exerciseDao(),
            catalogDao = database.catalogDao(),
            bodyweightDao = database.bodyweightDao(),
            goalDao = database.goalDao(),
            settingsStore = SettingsStore(PreferenceDataStoreFactory.create { prefsFile }),
            remote = remote,
            nowMillis = { 5_000L },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aNewerServerSessionUpdatesInPlaceAndKeepsItsBlocksAndSets() = runTest {
        seedStrengthActivity()
        val edited = session().copy(notes = "Edited on the other phone", revision = 2L, updatedAtMs = 900L)
        remote.seed(SyncEntityType.ACTIVITY_SESSION, encodeSync(edited.toRemote(userId = USER)))

        assertTrue(engine.run(USER).isSuccess)

        assertEquals("Edited on the other phone", database.activityDao().getSessionRow(SESSION)!!.notes)
        assertNotNull(database.activityDao().getBlock(BLOCK))
        assertNotNull(database.activityDao().getStrengthSet(SET))
    }

    @Test
    fun pullingBackTheBlockThisPhoneUploadedKeepsItsSets() = runTest {
        seedStrengthActivity()
        remote.seed(
            SyncEntityType.ACTIVITY_BLOCK,
            encodeSync(strengthBlock().copy(rpe = 8).toRemote(userId = USER, updatedAtMs = 500L)),
        )

        assertTrue(engine.run(USER).isSuccess)

        assertEquals(8, database.activityDao().getBlock(BLOCK)!!.rpe)
        assertNotNull(database.activityDao().getStrengthSet(SET))
    }

    @Test
    fun aSetOrIntervalAlreadyHereDoesNotStopTheTablesAfterIt() = runTest {
        seedStrengthActivity()
        database.activityDao().insertBlock(cardioBlock())
        database.activityDao().insertCardioIntervals(listOf(interval()))
        remote.seed(
            SyncEntityType.ACTIVITY_STRENGTH_SET,
            encodeSync(strengthSet().copy(reps = 6).toRemote(userId = USER, updatedAtMs = 500L)),
        )
        remote.seed(
            SyncEntityType.ACTIVITY_CARDIO_INTERVAL,
            encodeSync(interval().copy(elapsedSeconds = 660L).toRemote(userId = USER, updatedAtMs = 500L)),
        )
        // Schedule rules pull after sets and intervals: the witness that the pass got past them.
        remote.seed(SyncEntityType.SCHEDULE_RULE, encodeSync(rule().toRemote(userId = USER)))

        assertTrue(engine.run(USER).isSuccess)

        assertEquals(6, database.activityDao().getStrengthSet(SET)!!.reps)
        assertEquals(660L, database.activityDao().getCardioInterval(INTERVAL)!!.elapsedSeconds)
        assertNotNull(database.plannerDao().getRule(RULE))
    }

    @Test
    fun aNewerServerTemplateKeepsItsBlocks() = runTest {
        database.activityDao().upsertTemplate(ActivityTemplateEntity(TEMPLATE, "Push day", "", 100L))
        database.activityDao().insertBlock(strengthBlock().copy(id = TEMPLATE_BLOCK, sessionId = null, templateId = TEMPLATE))
        remote.seed(
            SyncEntityType.ACTIVITY_TEMPLATE,
            encodeSync(ActivityTemplateEntity(TEMPLATE, "Push day, renamed", "", 900L).toRemote(userId = USER)),
        )

        assertTrue(engine.run(USER).isSuccess)

        assertEquals("Push day, renamed", database.activityDao().getTemplateRow(TEMPLATE)!!.title)
        assertNotNull(database.activityDao().getBlock(TEMPLATE_BLOCK))
    }

    @Test
    fun aNewerServerRoutineKeepsItsLiftsAndTheHistoryLinkToIt() = runTest {
        seedLift(ExerciseEntity(id = LIFT, name = "Squat", muscleGroup = "Quads", notes = "", isCustom = false))
        database.routineDao().upsertRoutine(RoutineEntity(ROUTINE, "Legs", "", 1L, 100L))
        database.routineDao().upsertRoutineExercise(routineLift(LIFT))
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO workout_sessions " +
                "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                "('finished-1', '$ROUTINE', 'Legs', 1000, '', 40, 1000, 2000)",
        )
        remote.seed(
            SyncEntityType.ROUTINE,
            encodeSync(RoutineEntity(ROUTINE, "Legs, renamed", "", 1L, 900L).toRemote(userId = USER)),
        )

        assertTrue(engine.run(USER).isSuccess)

        val routine = database.routineDao().getById(ROUTINE)!!
        assertEquals("Legs, renamed", routine.routine.name)
        assertEquals(listOf(ROUTINE_LIFT), routine.items.map { it.item.id })
        database.openHelper.readableDatabase
            .query("SELECT routineId FROM workout_sessions WHERE id = 'finished-1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(ROUTINE, cursor.getString(0))
            }
    }

    @Test
    fun aFreshPhonePullsEveryNewRowIn() = runTest {
        // The insert half of each in-place write: nothing is on this phone yet.
        remote.seed(SyncEntityType.ACTIVITY_SESSION, encodeSync(session().toRemote(userId = USER)))
        remote.seed(
            SyncEntityType.ACTIVITY_TEMPLATE,
            encodeSync(ActivityTemplateEntity(TEMPLATE, "Push day", "", 100L).toRemote(userId = USER)),
        )
        remote.seed(SyncEntityType.ACTIVITY_BLOCK, encodeSync(strengthBlock().toRemote(userId = USER, updatedAtMs = 300L)))
        remote.seed(SyncEntityType.ACTIVITY_BLOCK, encodeSync(cardioBlock().toRemote(userId = USER, updatedAtMs = 300L)))
        remote.seed(
            SyncEntityType.ACTIVITY_STRENGTH_SET,
            encodeSync(strengthSet().toRemote(userId = USER, updatedAtMs = 400L)),
        )
        remote.seed(
            SyncEntityType.ACTIVITY_CARDIO_INTERVAL,
            encodeSync(interval().toRemote(userId = USER, updatedAtMs = 400L)),
        )

        assertTrue(engine.run(USER).isSuccess)

        assertNotNull(database.activityDao().getSessionRow(SESSION))
        assertNotNull(database.activityDao().getTemplateRow(TEMPLATE))
        assertNotNull(database.activityDao().getBlock(BLOCK))
        assertNotNull(database.activityDao().getBlock(CARDIO_BLOCK))
        assertEquals(5, database.activityDao().getStrengthSet(SET)!!.reps)
        assertEquals(600L, database.activityDao().getCardioInterval(INTERVAL)!!.elapsedSeconds)
    }

    @Test
    fun aServerDeleteStillTakesTheRowsUnderIt() = runTest {
        seedStrengthActivity()
        database.activityDao().upsertTemplate(ActivityTemplateEntity(TEMPLATE, "Push day", "", 100L))
        database.activityDao().insertBlock(strengthBlock().copy(id = TEMPLATE_BLOCK, sessionId = null, templateId = TEMPLATE))
        remote.seed(
            SyncEntityType.ACTIVITY_SESSION,
            encodeSync(session().copy(updatedAtMs = 900L).toRemote(userId = USER, deletedAtMs = 900L)),
        )
        remote.seed(
            SyncEntityType.ACTIVITY_TEMPLATE,
            encodeSync(ActivityTemplateEntity(TEMPLATE, "Push day", "", 900L).toRemote(userId = USER, deletedAtMs = 900L)),
        )

        assertTrue(engine.run(USER).isSuccess)

        assertNull(database.activityDao().getSessionRow(SESSION))
        assertNull(database.activityDao().getBlock(BLOCK))
        assertNull(database.activityDao().getStrengthSet(SET))
        assertNull(database.activityDao().getTemplateRow(TEMPLATE))
        assertNull(database.activityDao().getBlock(TEMPLATE_BLOCK))
    }

    @Test
    fun aLiftDeletedAfterLeavingItsRoutineIsDeletedInTheSamePass() = runTest {
        // The other phone only lets a custom lift go once no routine uses it, so its server
        // history is: routine lift deleted, then the lift and its credits. Routine lifts are
        // pulled last, so the lift delete arrives while the routine lift is still here.
        seedCustomLiftInARoutine()
        remote.seed(SyncEntityType.CUSTOM_EXERCISE, encodeSync(customLiftTombstone()))
        remote.seed(SyncEntityType.EXERCISE_MUSCLE, encodeSync(creditTombstone()))
        remote.seed(
            SyncEntityType.ROUTINE_EXERCISE,
            encodeSync(routineLift(CUSTOM).toRemote(userId = USER, updatedAtMs = 850L, deletedAtMs = 850L)),
        )

        assertTrue(engine.run(USER).isSuccess)

        assertNull(database.exerciseDao().getById(CUSTOM))
        assertEquals(emptyList<ExerciseMuscleEntity>(), database.catalogDao().creditsFor(CUSTOM))
        assertEquals(emptyList<Any>(), database.routineDao().getById(ROUTINE)!!.items)
    }

    @Test
    fun aDeletedCustomLiftThisPhonesHistoryUsesIsKeptWithItsCreditsAndThePullGoesOn() = runTest {
        seedLift(customLift())
        database.catalogDao().insertCredits(listOf(credit()))
        // A finished strength workout from before activity history: these tables do not sync,
        // so nothing will ever release the lift here.
        database.openHelper.writableDatabase.apply {
            execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, routineId, routineName, date, notes, durationMinutes, startedAt, finishedAt) VALUES " +
                    "('finished-1', NULL, NULL, 1000, '', 40, 1000, 2000)",
            )
            execSQL(
                "INSERT INTO set_logs " +
                    "(id, sessionId, exerciseId, setNumber, weightKg, reps, rpe, isWarmup, completedAt) VALUES " +
                    "('sl-1', 'finished-1', '$CUSTOM', 1, 40.0, 8, NULL, 0, 1500)",
            )
        }
        remote.seed(SyncEntityType.CUSTOM_EXERCISE, encodeSync(customLiftTombstone()))
        remote.seed(SyncEntityType.EXERCISE_MUSCLE, encodeSync(creditTombstone()))
        remote.seed(SyncEntityType.SCHEDULE_RULE, encodeSync(rule().toRemote(userId = USER)))

        assertTrue(engine.run(USER).isSuccess)

        // History still points at it, so this phone keeps it, still crediting its muscle...
        assertNotNull(database.exerciseDao().getById(CUSTOM))
        assertEquals(listOf(credit()), database.catalogDao().creditsFor(CUSTOM))
        // ...and the tables after custom lifts are still pulled.
        assertNotNull(database.plannerDao().getRule(RULE))
    }

    @Test
    fun anUnusedCustomLiftIsStillDeletedWithItsCredits() = runTest {
        seedLift(customLift())
        database.catalogDao().insertCredits(listOf(credit()))
        remote.seed(SyncEntityType.CUSTOM_EXERCISE, encodeSync(customLiftTombstone()))

        assertTrue(engine.run(USER).isSuccess)

        assertNull(database.exerciseDao().getById(CUSTOM))
        assertEquals(emptyList<ExerciseMuscleEntity>(), database.catalogDao().creditsFor(CUSTOM))
    }

    @Test
    fun aPulledSetWhoseBlockIsNotHereIsSkippedAndThePullGoesOn() = runTest {
        remote.seed(
            SyncEntityType.ACTIVITY_STRENGTH_SET,
            encodeSync(strengthSet().toRemote(userId = USER, updatedAtMs = 400L)),
        )
        remote.seed(SyncEntityType.SCHEDULE_RULE, encodeSync(rule().toRemote(userId = USER)))

        assertTrue(engine.run(USER).isSuccess)

        assertNull(database.activityDao().getStrengthSet(SET))
        assertNotNull(database.plannerDao().getRule(RULE))
    }

    private suspend fun seedCustomLiftInARoutine() {
        seedLift(customLift())
        database.catalogDao().insertCredits(listOf(credit()))
        database.routineDao().upsertRoutine(RoutineEntity(ROUTINE, "Legs", "", 1L, 100L))
        database.routineDao().upsertRoutineExercise(routineLift(CUSTOM))
    }

    private fun customLift() =
        ExerciseEntity(id = CUSTOM, name = "Landmine", muscleGroup = "Shoulders", notes = "", isCustom = true)

    private fun customLiftTombstone() =
        customLift().copy(updatedAtMs = 900L).toCustomRemote(userId = USER, createdAtMs = 1L, deletedAtMs = 900L)

    private fun credit() = ExerciseMuscleEntity(exerciseId = CUSTOM, muscleKey = "shoulders", weight = 1.0)

    private fun creditTombstone() = credit().toRemote(userId = USER, updatedAtMs = 900L, deletedAtMs = 900L)

    private suspend fun seedStrengthActivity() {
        database.activityDao().insertSession(session())
        database.activityDao().insertBlock(strengthBlock())
        database.activityDao().insertStrengthSets(listOf(strengthSet()))
    }

    private suspend fun seedLift(lift: ExerciseEntity) {
        database.exerciseDao().insertAll(listOf(lift))
    }

    private fun session() = ActivitySessionEntity(
        id = SESSION,
        status = "COMPLETED",
        origin = "LIVE",
        source = "STRENGTH",
        title = "Lift",
        notes = "",
        performedStartInstantMs = 100L,
        performedStartZoneId = "UTC",
        performedStartOffsetSeconds = 0,
        performedStartLocalEpochDay = 0L,
        performedEndInstantMs = 200L,
        performedEndZoneId = "UTC",
        performedEndOffsetSeconds = 0,
        performedEndLocalEpochDay = 0L,
        templateId = null,
        occurrenceId = null,
        createdAtMs = 100L,
        updatedAtMs = 200L,
        revision = 1L,
        liveToken = null,
    )

    private fun strengthBlock() = block(id = BLOCK, kind = "STRENGTH")

    private fun cardioBlock() = block(id = CARDIO_BLOCK, kind = "CARDIO").copy(cardioType = "RUN")

    private fun block(id: String, kind: String) = ActivityBlockEntity(
        id = id,
        sessionId = SESSION,
        templateId = null,
        sortOrder = 0,
        kind = kind,
        exerciseId = null,
        exerciseName = "Bench",
        loadType = "EXTERNAL",
        equipment = null,
        musclesEncoded = null,
        cardioType = null,
        indoor = null,
        elapsedSeconds = null,
        movingSeconds = null,
        distanceMeters = null,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = null,
        routeRef = null,
    )

    private fun strengthSet() = ActivityStrengthSetEntity(
        id = SET,
        blockId = BLOCK,
        setNumber = 1,
        weightKg = 60.0,
        reps = 5,
        rpe = null,
        isWarmup = false,
        completedAtMs = 150L,
    )

    private fun interval() = ActivityCardioIntervalEntity(
        id = INTERVAL,
        blockId = CARDIO_BLOCK,
        sortOrder = 0,
        elapsedSeconds = 600L,
        distanceMeters = 2_000.0,
        rpe = null,
    )

    private fun routineLift(exerciseId: String) = RoutineExerciseEntity(
        id = ROUTINE_LIFT,
        routineId = ROUTINE,
        exerciseId = exerciseId,
        sortOrder = 0,
        targetSets = 3,
        targetReps = 5,
        targetWeightKg = 100.0,
        restSeconds = 120,
    )

    private fun rule() = ScheduleRuleEntity(
        id = RULE,
        weekday = 3,
        hour = 18,
        minute = 0,
        modality = "STRENGTH",
        zonePolicy = "DEVICE",
        fixedZoneId = null,
        routineId = null,
        templateId = null,
        focusKind = null,
        reminderOffsetMinutes = 0,
        enabled = 1,
        createdAtMs = 1L,
        updatedAtMs = 700L,
    )

    private companion object {
        const val USER = "user-1"
        const val SESSION = "sess-1"
        const val BLOCK = "block-1"
        const val CARDIO_BLOCK = "block-cardio"
        const val SET = "set-1"
        const val INTERVAL = "interval-1"
        const val TEMPLATE = "template-1"
        const val TEMPLATE_BLOCK = "template-block-1"
        const val ROUTINE = "routine-legs"
        const val ROUTINE_LIFT = "re-1"
        const val LIFT = "ex-squat"
        const val CUSTOM = "ex-custom"
        const val RULE = "rule-witness"
    }
}
