package com.sinura.personaltrainer.data.local

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.backup.AuthoredInventory
import com.sinura.personaltrainer.data.backup.BackupDocument
import com.sinura.personaltrainer.data.backup.BackupException
import com.sinura.personaltrainer.data.backup.BackupExercise
import com.sinura.personaltrainer.data.backup.BackupJson
import com.sinura.personaltrainer.data.backup.BackupPreferences
import com.sinura.personaltrainer.data.local.entity.ActivityTemplateEntity
import com.sinura.personaltrainer.data.local.entity.MeasurableGoalEntity
import com.sinura.personaltrainer.data.local.entity.ScheduleRuleEntity
import com.sinura.personaltrainer.testutil.TestSetInput
import com.sinura.personaltrainer.testutil.seedTestWorkout
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The restore guard (ADR-009 §11) refuses a file with no authored data when the phone has
 * some. It counted sessions, sets, routines, custom lifts, the legacy schedule, weigh-ins,
 * blocks and activities, but not goals, cardio templates or the weekly plan: a phone holding
 * only those read as empty, so a catalog-only file wiped them without the refusal, a backup
 * holding only those was refused as "no data", and the confirm box left them out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class RestoreGuardCountsPlanTest {
    private lateinit var deps: FakeAppDependencies

    @Before
    fun setUp() {
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        deps.close()
    }

    @Test
    fun aPhoneHoldingOnlyAGoalRefusesACatalogOnlyFile() = runBlocking {
        deps.database.goalDao().upsert(goal())
        assertCatalogOnlyRefused()
        assertEquals(1, deps.backupService.authoredInventory().goals)
    }

    @Test
    fun aPhoneHoldingOnlyACardioTemplateRefusesACatalogOnlyFile() = runBlocking {
        deps.database.activityDao().upsertTemplate(template())
        assertCatalogOnlyRefused()
        assertEquals(1, deps.backupService.authoredInventory().activityTemplates)
    }

    @Test
    fun aPhoneHoldingOnlyAWeeklyPlanRefusesACatalogOnlyFile() = runBlocking {
        deps.database.plannerDao().upsertRule(rule())
        assertCatalogOnlyRefused()
        assertEquals(1, deps.backupService.authoredInventory().planRules)
    }

    @Test
    fun aBackupHoldingOnlyGoalsTemplatesAndAPlanIsNotRefusedAsEmpty() = runBlocking {
        deps.database.goalDao().upsert(goal())
        deps.database.activityDao().upsertTemplate(template())
        deps.database.plannerDao().upsertRule(rule())
        val planOnly = deps.backupService.exportJson()
        deps.close()
        deps = FakeAppDependencies(ApplicationProvider.getApplicationContext())
        seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))

        val plan = deps.backupService.prepareRestore(planOnly, sourceName = "plan.json")

        assertEquals(1, plan.incoming.goals)
        assertEquals(1, plan.incoming.activityTemplates)
        assertEquals(1, plan.incoming.planRules)
        val body = AuthoredInventory.confirmBody("plan.json", plan.incoming, plan.local)
        assertTrue(body, body.contains("1 goal,"))
        assertTrue(body, body.contains("1 cardio template,"))
        assertTrue(body, body.contains("1 planned weekly session"))
    }

    @Test
    fun aRestoreStillWritesItsVerifiedSafetyCopyWhenThePhoneHoldsAPlan() = runBlocking {
        // The safety copy is checked against the phone's own counts before anything is
        // replaced; the three new counts must read the same on both sides or every restore
        // would be refused.
        deps.database.goalDao().upsert(goal())
        deps.database.activityDao().upsertTemplate(template())
        deps.database.plannerDao().upsertRule(rule())
        seedTestWorkout(deps, finish = true, loggedSets = listOf(TestSetInput(100.0, 5)))
        val plan = deps.backupService.prepareRestore(deps.backupService.exportJson(), sourceName = "same.json")

        val result = deps.backupService.commitRestore(plan)

        assertNotNull(result.safetySnapshotId)
        val after = deps.backupService.authoredInventory()
        assertEquals(1, after.goals)
        assertEquals(1, after.activityTemplates)
        assertEquals(1, after.planRules)
    }

    private suspend fun assertCatalogOnlyRefused() {
        try {
            deps.backupService.prepareRestore(catalogOnlyJson(), sourceName = "catalog.json")
            fail("a catalog-only file must not replace the phone's authored data")
        } catch (thrown: BackupException) {
            assertEquals(AuthoredInventory.EMPTY_INCOMING_REFUSED, thrown.message)
        }
    }

    private fun goal() = MeasurableGoalEntity(
        id = "goal-1",
        kind = "SESSION_COUNT",
        targetValue = 3.0,
        exerciseId = null,
        exerciseName = null,
        period = "WEEK",
        instantMs = STAMP,
        zoneId = "UTC",
        offsetSeconds = 0,
        localEpochDay = STAMP / 86_400_000L,
        paused = false,
        createdAtMs = STAMP,
        updatedAtMs = STAMP,
    )

    private fun template() = ActivityTemplateEntity(
        id = "template-1",
        title = "Easy run",
        notes = "",
        updatedAtMs = STAMP,
    )

    private fun rule() = ScheduleRuleEntity(
        id = "rule-1",
        weekday = 2,
        hour = 7,
        minute = 0,
        modality = "STRENGTH",
        zonePolicy = "DEVICE",
        fixedZoneId = null,
        routineId = null,
        templateId = null,
        focusKind = null,
        reminderOffsetMinutes = 0,
        enabled = 1,
        createdAtMs = STAMP,
        updatedAtMs = STAMP,
    )

    private companion object {
        const val STAMP = 1_700_000_000_000L
    }
}

private fun catalogOnlyJson(): String = BackupJson.encode(
    BackupDocument(
        exportedAt = "2026-08-24T10:00:00Z",
        preferences = BackupPreferences(weightUnit = "kg"),
        exercises = listOf(
            BackupExercise(
                id = "ex-squat",
                name = "Barbell Back Squat",
                muscleGroup = "Quads",
                notes = "",
                isCustom = false,
                equipment = "BARBELL",
                loadType = "EXTERNAL",
            ),
        ),
        routines = emptyList(),
        routineExercises = emptyList(),
        sessions = emptyList(),
        sessionExercises = emptyList(),
        setLogs = emptyList(),
    ),
)
