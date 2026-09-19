package com.sinura.personaltrainer.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.RoutineEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleSlotEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import com.sinura.personaltrainer.data.repository.LocalBackupRepository
import com.sinura.personaltrainer.data.repository.PreferencesRepository
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.RestTimerPreferences
import com.sinura.personaltrainer.domain.SchedulePreferences
import com.sinura.personaltrainer.domain.TrainingAge
import com.sinura.personaltrainer.domain.TrainingBlock
import com.sinura.personaltrainer.domain.TrainingEmphasis
import com.sinura.personaltrainer.domain.TrainingGoal
import com.sinura.personaltrainer.domain.TrainingPlace
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.Weekday
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Backup v2, end to end, against a real database.
 *
 * A schema change without a matching backup change is the quietest way to lose data this app
 * has: the export succeeds, the file looks fine, and the restore silently drops every field the
 * format does not know about. These tests are the reason the format co-evolved in the same phase
 * as the schema rather than in the next one.
 *
 * Exercised through `LocalBackupRepository` + `DbMaintenance` rather than `BackupService` —
 * that is the same decode → validate → replaceWith → reconcile sequence, minus the Drive clients
 * that have no business being constructed in a database test.
 */
@RunWith(RobolectricTestRunner::class)
class BackupV2RoundTripTest {

    private lateinit var database: TrainerDatabase
    private lateinit var preferences: PreferencesRepository
    private lateinit var local: LocalBackupRepository
    private lateinit var maintenance: DbMaintenance
    private lateinit var prefsScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, TrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        maintenance = DbMaintenance(database)
        prefsScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = PreferenceDataStoreFactory.create(
            scope = prefsScope,
            produceFile = {
                File(context.cacheDir, "backup-v2-${System.nanoTime()}.preferences_pb")
            },
        )
        preferences = PreferencesRepository(context, store)
        local = LocalBackupRepository(
            database = database,
            preferencesRepository = preferences,
        )
    }

    @After
    fun tearDown() {
        prefsScope.cancel()
        database.close()
    }

    @Test
    fun v2ExportRestoreV2IsLossless() = runBlocking {
        maintenance.seedCatalog()
        seedUserData()

        val json = BackupJson.encode(local.createSnapshot())
        assertTrue(json.contains("\"version\": ${BackupJson.CURRENT_VERSION}"))
        assertTrue(json.contains("exerciseMuscles"))
        assertTrue(json.contains("scheduleSlots"))

        val before = tableFingerprint()
        restore(json)

        assertEquals(before, tableFingerprint())

        // Reconciliation is idempotent on top of a restore, not just on a cold start: run the
        // whole thing again and nothing may move.
        restore(json)
        assertEquals(before, tableFingerprint())
    }

    @Test
    fun v1FileRestoreOntoV2RebuildsCatalog() = runBlocking {
        maintenance.seedCatalog()
        seedUserData()

        restore(V1_FIXTURE)

        // The built-ins come back at whatever this build ships, even though the file described
        // none of them: reconciliation is what re-derives the catalog.
        val exercises = database.exerciseDao().getAll()
        assertEquals(DefaultExercises.catalog().size + 1, exercises.size)
        assertEquals(DefaultExercises.catalog().size, exercises.count { !it.isCustom })
        val squat = database.exerciseDao().getById("ex-barbell-back-squat")!!
        assertEquals("BARBELL", squat.equipment)
        assertEquals("squat", squat.movementKey)

        // The custom from the file keeps its id and gains derived credits.
        val custom = database.exerciseDao().getById("ex-custom-1")
        assertNotNull(custom)
        assertEquals("sled push", custom!!.nameKey)
        assertTrue(database.catalogDao().creditsFor("ex-custom-1").isNotEmpty())

        assertEquals(
            DefaultExercises.CATALOG_VERSION,
            database.catalogDao().getSeedMeta()!!.catalogVersion,
        )
        // Collision detection re-ran on the restored rows; nothing here collides.
        assertEquals("[]", database.catalogDao().getSeedMeta()!!.pendingCollisions)
    }

    @Test
    fun aRestoreBringsTheGuidedSetupAnswersWithIt() = runBlocking {
        // The failure this exists to catch is silent and one-way: you set the app up, back it
        // up, restore onto a new phone, and it asks you the six setup questions again with your
        // whole training history already on screen. Every value below is deliberately
        // non-default at export and overwritten with a different non-default before the
        // restore, so neither "the export wrote nothing" nor "the restore wrote nothing" can
        // pass — and so the test does not care what ran before it.
        maintenance.seedCatalog()
        seedUserData()
        preferences.setWeightUnit(WeightUnit.LBS)
        preferences.setTrainingGoal(TrainingGoal.STRENGTH)
        preferences.setTrainingEmphasis(TrainingEmphasis.UPPER)
        preferences.setAvailableEquipment(setOf("BARBELL", "DUMBBELL"))
        preferences.setHeatWindow(HeatWindow.CURRENT_MONTH)
        preferences.recordBodyweight(82.5, 20_000L)
        preferences.setOnboardingComplete(true)
        preferences.dismissCollision("ex-custom-1")

        val json = BackupJson.encode(local.createSnapshot())

        preferences.setWeightUnit(WeightUnit.KG)
        preferences.setTrainingGoal(TrainingGoal.HYPERTROPHY)
        preferences.setTrainingEmphasis(TrainingEmphasis.LOWER)
        preferences.setAvailableEquipment(setOf("CABLE"))
        preferences.setHeatWindow(HeatWindow.CURRENT_WEEK)
        preferences.recordBodyweight(60.0, 20_100L)
        preferences.setOnboardingComplete(false)

        restore(json)

        assertEquals(WeightUnit.LBS, preferences.weightUnit.first())
        val coach = preferences.coachPreferences.first()
        assertEquals(TrainingGoal.STRENGTH, coach.goal)
        assertEquals(TrainingEmphasis.UPPER, coach.emphasis)
        assertEquals(setOf("BARBELL", "DUMBBELL"), coach.availableEquipment)
        assertEquals(HeatWindow.CURRENT_MONTH, preferences.heatWindow.first())
        assertEquals(82.5, preferences.bodyweightKg.first()!!, 0.001)
        // The weigh-in history travels too — it is what lets a block review say what
        // bodyweight did over its twelve weeks.
        assertEquals(listOf(20_000L), preferences.bodyweightLog.first().map { it.epochDay })
        assertTrue(preferences.onboardingComplete.first())
        assertEquals(setOf("ex-custom-1"), preferences.dismissedCollisionIds.first())
    }

    @Test
    fun aRestoreKeepsYouWhereYouWereInYourBlock() = runBlocking {
        // A block is a horizon and a review date. Losing it on a restore would put a lifter
        // back at week one of nothing with eleven weeks of the work already behind them.
        maintenance.seedCatalog()
        seedUserData()
        preferences.setTrainingBlock(TrainingBlock(startEpochDay = 20_318L, weeks = 12))

        val json = BackupJson.encode(local.createSnapshot())
        preferences.setTrainingBlock(TrainingBlock(startEpochDay = 20_500L, weeks = 8))

        restore(json)

        val restored = preferences.trainingBlock.first()!!
        assertEquals(20_318L, restored.startEpochDay)
        assertEquals(12, restored.weeks)
    }

    @Test
    fun finishedBlocksTravelWithTheBackup() = runBlocking {
        // Three years of finished blocks is a record of what you did. A new phone that lost it
        // would show a lifter with a full history their first ever block.
        maintenance.seedCatalog()
        seedUserData()
        preferences.beginBlock(TrainingBlock(startEpochDay = 20_000L, weeks = 12), 20_000L)
        // Begun on a day past its end, so the one it replaces counts as finished and is kept.
        preferences.beginBlock(TrainingBlock(startEpochDay = 20_084L, weeks = 12), 20_084L)
        assertEquals(listOf(20_000L), preferences.pastBlocks.first().map { it.startEpochDay })

        val json = BackupJson.encode(local.createSnapshot())
        preferences.setRestoredPreferences(
            unit = WeightUnit.KG,
            schedule = SchedulePreferences(),
            rest = RestTimerPreferences(),
            coach = CoachPreferences(),
            heatWindow = HeatWindow.CURRENT_WEEK,
            bodyweightKg = null,
            onboardingComplete = false,
            dismissedCollisionIds = emptySet(),
            block = null,
            pastBlocks = emptyList(),
            bodyweightLog = emptyList(),
            trainingAge = TrainingAge.NEW,
            preferredDays = emptySet(),
            trainingPlace = TrainingPlace.FULL_GYM,
            lighterWeekStartEpochDay = null,
        )
        assertTrue(preferences.pastBlocks.first().isEmpty())

        restore(json)

        assertEquals(listOf(20_000L), preferences.pastBlocks.first().map { it.startEpochDay })
    }

    @Test
    fun anUnfinishedBlockIsNotArchivedWhenItIsReplaced() = runBlocking {
        // Re-running setup half way through a block discards it. A block you abandoned is not
        // a result, and listing it beside blocks you finished would make the list meaningless.
        preferences.beginBlock(TrainingBlock(startEpochDay = 20_000L, weeks = 12), 20_000L)
        preferences.beginBlock(TrainingBlock(startEpochDay = 20_030L, weeks = 12), 20_030L)
        assertTrue(preferences.pastBlocks.first().isEmpty())
    }

    @Test
    fun restoringABackupWithNoBlockLeavesYouInNone() = runBlocking {
        // Null is a real answer: someone who built their routines by hand never started a
        // block, and an implied one would invent a milestone they never set.
        preferences.setTrainingBlock(TrainingBlock(startEpochDay = 20_500L, weeks = 8))
        maintenance.seedCatalog()

        restore(V1_FIXTURE)

        assertNull(preferences.trainingBlock.first())
    }

    @Test
    fun aLighterWeekMarkTravelsWithTheBackup() = runBlocking {
        maintenance.seedCatalog()
        seedUserData()
        preferences.setLighterWeekStartEpochDay(20_318L)

        val json = BackupJson.encode(local.createSnapshot())
        preferences.setLighterWeekStartEpochDay(null)
        assertEquals(null, preferences.lighterWeekStartEpochDay.first())

        restore(json)

        assertEquals(20_318L, preferences.lighterWeekStartEpochDay.first())
    }

    @Test
    fun setupAnswersThatUsedToDieAtAcceptTravelWithTheBackup() = runBlocking {
        maintenance.seedCatalog()
        seedUserData()
        preferences.setTrainingAge(TrainingAge.EXPERIENCED)
        preferences.setPreferredDays(setOf(Weekday.TUESDAY, Weekday.THURSDAY))
        preferences.setTrainingPlace(TrainingPlace.HOME_DUMBBELLS)

        val json = BackupJson.encode(local.createSnapshot())
        preferences.setTrainingAge(TrainingAge.NEW)
        preferences.setPreferredDays(emptySet())
        preferences.setTrainingPlace(TrainingPlace.FULL_GYM)

        restore(json)

        assertEquals(TrainingAge.EXPERIENCED, preferences.trainingAge.first())
        assertEquals(
            setOf(Weekday.TUESDAY, Weekday.THURSDAY),
            preferences.preferredDays.first(),
        )
        assertEquals(TrainingPlace.HOME_DUMBBELLS, preferences.trainingPlace.first())
    }

    @Test
    fun restoringAnOldBackupDoesNotReopenTheGuidedSetup() = runBlocking {
        // A v1 file predates the flag entirely, so it decodes to false. Writing that through
        // would drop someone with a year of history back at question one. Any document that
        // carries routines or sessions counts as set up regardless of what the flag says.
        preferences.setOnboardingComplete(false)
        maintenance.seedCatalog()

        restore(V1_FIXTURE)

        assertTrue(preferences.onboardingComplete.first())
    }

    /**
     * The tick toggle is this phone's, like the last preset: a document from
     * another phone, which has no field for it, must not switch it back on.
     */
    @Test
    fun aRestoreLeavesTheTickToggleAlone() = runBlocking {
        preferences.setRestTickEnabled(false)
        maintenance.seedCatalog()
        seedUserData()
        val json = BackupJson.encode(local.createSnapshot())
        restore(json)
        assertFalse(preferences.restTimerPreferences.first().tickEnabled)
        restore(V1_FIXTURE)
        assertFalse(preferences.restTimerPreferences.first().tickEnabled)
    }

    @Test
    fun hasLocalDataCountsScheduleSlots() = runBlocking {
        assertEquals(0, local.authoredInventory().scheduleSlots)

        database.routineDao().upsertRoutine(RoutineEntity("r1", "Push", "", STAMP, STAMP))
        database.scheduleDao().replaceAll(
            listOf(
                ScheduleSlotEntity(
                    id = "slot-1", position = 0, routineId = "r1", focusKind = null,
                    anchorDay = 0, createdAt = STAMP, updatedAt = STAMP,
                ),
            ),
        )
        // A pinned week is authored state. Without the slot count, someone whose only work so far
        // is a plan reads as empty and an empty-backup restore wipes it without asking.
        assertEquals(1, local.authoredInventory().scheduleSlots)
        assertTrue(local.hasLocalData())
    }

    private suspend fun restore(json: String) {
        val document = BackupJson.decode(json)
        maintenance.withMaintenanceLock {
            local.replaceWith(document)
            maintenance.reconcileCatalogLocked()
        }
    }

    private suspend fun seedUserData() {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = "ex-custom-1", name = "Sled Push", muscleGroup = "Legs",
                notes = "hill day", isCustom = true, nameKey = "sled push",
            ),
        )
        database.routineDao().upsertRoutine(RoutineEntity("r1", "Legs", "", STAMP, STAMP))
        database.workoutDao().upsertSession(
            WorkoutSessionEntity(
                id = "s1", routineId = "r1", routineName = "Legs", date = STAMP,
                notes = "good", durationMinutes = 45, startedAt = STAMP,
                finishedAt = STAMP + 2_700_000,
            ),
        )
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = "se1", sessionId = "s1", exerciseId = "ex-barbell-back-squat",
                sortOrder = 0, targetSets = 3, targetReps = 5, targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "set1", sessionId = "s1", exerciseId = "ex-barbell-back-squat",
                setNumber = 1, weightKg = 100.0, reps = 5, rpe = 8, isWarmup = false,
                completedAt = STAMP + 600_000,
            ),
        )
        database.scheduleDao().replaceAll(
            listOf(
                ScheduleSlotEntity(
                    id = "slot-1", position = 0, routineId = "r1", focusKind = null,
                    anchorDay = 0, createdAt = STAMP, updatedAt = STAMP,
                ),
                ScheduleSlotEntity(
                    id = "slot-2", position = 1, routineId = null, focusKind = "pull",
                    anchorDay = null, createdAt = STAMP, updatedAt = STAMP,
                ),
            ),
        )
        // Reconcile so the custom exercise gets its junction rows before the snapshot is taken.
        maintenance.withMaintenanceLock { maintenance.reconcileCatalogLocked() }
    }

    /** Every table that a restore is supposed to reproduce exactly, in a comparable shape. */
    private suspend fun tableFingerprint(): Map<String, List<String>> = mapOf(
        "exercises" to database.exerciseDao().getAll().map { it.toString() }.sorted(),
        "routines" to database.routineDao().getAllRoutines().map { it.toString() }.sorted(),
        "sessions" to database.workoutDao().getAllSessions().map { it.toString() }.sorted(),
        "sessionExercises" to database.workoutDao().getAllSessionExercises()
            .map { it.toString() }.sorted(),
        "sets" to database.workoutDao().getAllSets().map { it.toString() }.sorted(),
        "credits" to database.catalogDao().getAllCredits().map { it.toString() }.sorted(),
        "slots" to database.scheduleDao().getAll().map { it.toString() }.sorted(),
    )

    private companion object {
        const val STAMP = 1_700_000_000_000L

        /** A v1 file: no equipment, no junction, no slots. */
        val V1_FIXTURE = """
            {
              "version": 1,
              "app": "personal-trainer",
              "exportedAt": "2026-01-01T00:00:00Z",
              "preferences": {"weightUnit": "kg"},
              "exercises": [
                {"id": "ex-barbell-back-squat", "name": "Barbell Back Squat",
                 "muscleGroup": "Quads", "notes": "", "isCustom": false},
                {"id": "ex-custom-1", "name": "Sled Push", "muscleGroup": "Legs",
                 "notes": "hill day", "isCustom": true}
              ],
              "routines": [
                {"id": "r1", "name": "Legs", "notes": "", "createdAt": $STAMP, "updatedAt": $STAMP}
              ],
              "routineExercises": [],
              "sessions": [
                {"id": "s1", "routineId": "r1", "routineName": "Legs", "date": $STAMP,
                 "notes": "good", "durationMinutes": 45, "startedAt": $STAMP,
                 "finishedAt": ${STAMP + 2_700_000}}
              ],
              "sessionExercises": [],
              "setLogs": [
                {"id": "set1", "sessionId": "s1", "exerciseId": "ex-barbell-back-squat",
                 "setNumber": 1, "weightKg": 100.0, "reps": 5, "rpe": 8, "isWarmup": false,
                 "completedAt": ${STAMP + 600_000}}
              ]
            }
        """.trimIndent()
    }
}
