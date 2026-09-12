package com.sinura.personaltrainer.data.local

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.data.repository.DbMaintenance
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * An Obtainium update is a new process on the same files. History lives on
 * [TemperDatabase] (`temper.db`), not the legacy [TrainerDatabase].
 *
 * The previous proof opened `personal_trainer.db`. A wipe of the live file
 * would not have failed it. This opens the production builder, writes a
 * finished session History actually lists, closes it, and reopens the
 * same file. A version-code-only drop must not change [FoundationGeneration].
 */
@RunWith(RobolectricTestRunner::class)
class UpgradeInPlaceTest {
    private lateinit var context: IsolatedUpgradeContext

    @Before
    fun setUp() {
        context = IsolatedUpgradeContext(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        context.deleteDatabase(FoundationGeneration.DATABASE_FILE)
        File(context.filesDir, SENTINEL_FILE).delete()
        context.root.deleteRecursively()
    }

    @Test
    fun finishedHistoryAndLiveSessionSurviveProductionReopen() = runBlocking {
        File(context.filesDir, SENTINEL_FILE).writeText(SENTINEL_BODY)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_KEY, SENTINEL_BODY)
            .commit()

        openDb().use { first ->
            seedHistory(first)
            first.workoutDao().upsertSession(inProgressSession())
            assertEquals(FINISHED_ID, first.workoutDao().getSessionRow(FINISHED_ID)?.id)
            assertEquals(LIVE_ID, first.workoutDao().getInProgressSession()?.id)
            assertEquals(1, first.workoutDao().sessionSummaries().size)
            assertEquals(1, first.workoutDao().sessionStills().size)
        }

        assertEquals(SENTINEL_BODY, File(context.filesDir, SENTINEL_FILE).readText())
        assertEquals(
            SENTINEL_BODY,
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREFS_KEY, null),
        )

        openDb().use { second ->
            val finished = checkNotNull(second.workoutDao().getSessionRow(FINISHED_ID))
            assertEquals("Upgrade proof", finished.routineName)
            assertEquals("must survive reopen", finished.notes)
            assertEquals(STAMP + 2_400_000, finished.finishedAt)

            val summaries = second.workoutDao().sessionSummaries()
            assertEquals(1, summaries.size)
            assertEquals(FINISHED_ID, summaries.single().id)
            assertEquals(1, summaries.single().workingSets)
            assertEquals(1, second.workoutDao().sessionStills().size)

            val live = checkNotNull(second.workoutDao().getInProgressSession())
            assertEquals(LIVE_ID, live.id)
            assertEquals(null, live.finishedAt)

            DbMaintenance(second).seedCatalog()
            assertEquals(FINISHED_ID, second.workoutDao().getSessionRow(FINISHED_ID)?.id)
            assertEquals(1, second.workoutDao().sessionSummaries().size)
        }
    }

    @Test
    fun versionCodeOnlyUpdateMustNotRenameTheDatabase() {
        val gradle = source("app/build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationId = \"com.sinura.personaltrainer\""))
        assertTrue(gradle.contains("applicationIdSuffix = \".debug\""))
        assertTrue(gradle.contains("output.versionCode.set(debugLiveCode)"))
        assertTrue(gradle.contains("output.versionName.set(\"\$appVersionName+debug.\$debugLiveCode\")"))
        assertTrue(!gradle.contains("applicationIdSuffix = \".debug.\$debugLiveCode\""))
        assertTrue(!gradle.contains("applicationIdSuffix = \".debug.\" + debugLiveCode"))
        assertEquals("temper.db", FoundationGeneration.DATABASE_FILE)
        assertEquals(4, FoundationGeneration.VERSION)
        assertTrue(FoundationGeneration.FROZEN)
        assertTrue(!source("app/src/main/java/com/sinura/personaltrainer/data/local/TemperDatabase.kt")
            .readText().contains(".fallbackToDestructiveMigration"))
    }

    private fun openDb(): TemperDatabase = TemperDatabase.create(context)

    private suspend fun seedHistory(database: TemperDatabase) {
        database.exerciseDao().insert(
            ExerciseEntity(
                id = SQUAT_ID,
                name = "Barbell Back Squat",
                muscleGroup = "Quads",
                notes = "",
                isCustom = false,
                nameKey = "barbell back squat",
            ),
        )
        database.workoutDao().upsertSession(sentinelSession())
        database.workoutDao().upsertSessionExercise(
            SessionExerciseEntity(
                id = "upgrade-item",
                sessionId = FINISHED_ID,
                exerciseId = SQUAT_ID,
                sortOrder = 0,
                targetSets = 3,
                targetReps = 5,
                targetWeightKg = 100.0,
                restSeconds = 90,
            ),
        )
        database.workoutDao().insertSet(
            SetLogEntity(
                id = "upgrade-set",
                sessionId = FINISHED_ID,
                exerciseId = SQUAT_ID,
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                rpe = 8,
                isWarmup = false,
                completedAt = STAMP + 600_000,
            ),
        )
    }

    private fun sentinelSession() = WorkoutSessionEntity(
        id = FINISHED_ID,
        routineId = null,
        routineName = "Upgrade proof",
        date = STAMP,
        notes = "must survive reopen",
        durationMinutes = 40,
        startedAt = STAMP,
        finishedAt = STAMP + 2_400_000,
    )

    private fun inProgressSession() = WorkoutSessionEntity(
        id = LIVE_ID,
        routineId = null,
        routineName = "Still lifting",
        date = STAMP + 86_400_000,
        notes = "",
        durationMinutes = 0,
        startedAt = STAMP + 86_400_000,
        finishedAt = null,
    )

    private companion object {
        const val FINISHED_ID = "upgrade-inplace-finished"
        const val LIVE_ID = "upgrade-inplace-live"
        const val SQUAT_ID = "ex-barbell-back-squat"
        const val SENTINEL_FILE = "upgrade-inplace-sentinel.txt"
        const val SENTINEL_BODY = "temper-upgrade-inplace"
        const val PREFS = "upgrade_inplace_marker"
        const val PREFS_KEY = "body"
        const val STAMP = 1_755_000_000_000L

        fun source(relative: String): File {
            val candidates = listOf(
                File(relative),
                File("../$relative"),
                File(relative.removePrefix("app/")),
            )
            return candidates.first { it.isFile }
        }
    }
}

/**
 * Own files and database paths. Room's production builder uses
 * [Context.getDatabasePath]; the wrapper must not leak into the shared
 * Robolectric application or a leftover WAL from another class lands here.
 */
internal class IsolatedUpgradeContext(base: Context) : ContextWrapper(base) {
    val root = File(base.cacheDir, "upgrade-inplace-${System.nanoTime()}").also { it.mkdirs() }
    private val databases = File(root, "databases").also { it.mkdirs() }
    private val files = File(root, "files").also { it.mkdirs() }

    override fun getApplicationContext(): Context = this

    override fun getFilesDir(): File = files

    override fun getDatabasePath(name: String): File = File(databases, name)

    override fun deleteDatabase(name: String): Boolean {
        var deleted = true
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val file = File(databases, name + suffix)
            if (file.exists() && !file.delete()) deleted = false
        }
        return deleted
    }
}

private inline fun TemperDatabase.use(block: (TemperDatabase) -> Unit) {
    try {
        block(this)
    } finally {
        close()
    }
}
