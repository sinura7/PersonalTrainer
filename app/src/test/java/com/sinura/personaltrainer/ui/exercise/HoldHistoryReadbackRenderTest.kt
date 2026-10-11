package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.FakeAppDependencies
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SessionExerciseEntity
import com.sinura.personaltrainer.data.local.entity.SetLogEntity
import com.sinura.personaltrainer.data.local.entity.WorkoutSessionEntity
import com.sinura.personaltrainer.domain.HoldWork
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryScreen
import com.sinura.personaltrainer.ui.summary.WorkoutSummaryViewModel
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.units.LocalWeightUnit
import com.sinura.personaltrainer.ui.workout.awaitThat
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Literal hold values travel through real Room projections into both changed native screens. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class HoldHistoryReadbackRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var deps: FakeAppDependencies
    private val models = mutableListOf<ViewModel>()
    private val frames = File("build/screen-renders/summary-history-holds/${UUID.randomUUID()}")

    @Before fun setUp() { deps = FakeAppDependencies(context = ApplicationProvider.getApplicationContext()) }
    @After fun tearDown() {
        models.forEach { it.viewModelScope.cancel() }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        deps.close()
    }

    @Test fun shortLiteralHoldReadsAsOneSecond() = show(seconds = 1)
    @Test @Config(fontScale = 2f) fun aLongLiteralHoldRemainsReadableAtLargeText() = show(seconds = 1801)

    @Test fun narrowProjectionUsesTheEstablishedPositiveTimestampFallback() = runBlocking {
        withTimeout(timeMillis = WAIT_MS) {
            seed(seconds = 4, date = 0L, finished = STAMP + 3_600_000L)
            val dao = deps.database.workoutDao()
            val original = dao.getAllSessions().single()
            val recorded = dao.getAllSets()
            val planned = dao.getAllSessionExercises()
            val row = dao.observeFinishedWorkingSets(exerciseId = LIFT).first().single()
            assertEquals(STAMP + 3_600_000L, row.sessionDate)
            assertEquals(4, row.durationSeconds)
            assertNull(row.sessionLocalEpochDay)
            // REPLACE deletes the old parent and cascades its children. These are timestamp
            // variants of the same saved workout, so UPDATE preserves the measurement graph.
            dao.updateSession(session = original.copy(date = -1L, finishedAt = 0L))
            val older = dao.observeFinishedWorkingSets(exerciseId = LIFT).first().single()
            assertEquals(STAMP, older.sessionDate)
            dao.updateSession(session = original.copy(date = STAMP - 86_400_000L))
            val dated = dao.observeFinishedWorkingSets(exerciseId = LIFT).first().single()
            assertEquals(STAMP - 86_400_000L, dated.sessionDate)
            assertEquals("timestamp variants retain the original saved set", recorded, dao.getAllSets())
            assertEquals("timestamp variants retain the original prescription", planned, dao.getAllSessionExercises())
        }
    }

    private fun show(seconds: Int) {
        runBlocking { withTimeout(timeMillis = WAIT_MS) { seed(seconds = seconds) } }
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Summary launches its read in the constructor. Create both models on the activity's
        // UI thread, where the shipping constructor and Main.immediate normally run.
        val summary = compose.runOnUiThread {
            WorkoutSummaryViewModel(application = app,
                savedStateHandle = SavedStateHandle(mapOf("sessionId" to SESSION)), container = deps).also(models::add)
        }
        val detail = compose.runOnUiThread {
            ExerciseDetailViewModel(application = app,
                savedStateHandle = SavedStateHandle(mapOf("exerciseId" to LIFT)), container = deps).also(models::add)
        }
        val exerciseOpen = mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalWeightUnit provides WeightUnit.KG) {
                PersonalTrainerTheme(reduceMotion = true) {
                    if (exerciseOpen.value) ExerciseDetailScreen(onBack = {}, onOpenSession = {},
                        onOpenActivity = {}, viewModel = detail)
                    else WorkoutSummaryScreen(onDone = {}, onOpenSession = {}, viewModel = summary)
                }
            }
        }
        awaitRead(
            what = "the real Room Summary finishes its constructor read",
            now = { summary.uiState.value },
            condition = {
                // Room and compute stay on real threads. Polling only uiState does not drain
                // their return to Robolectric's paused Android main looper.
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                !summary.uiState.value.isLoading
            },
        )
        assertFalse("the synthetic finished Summary is readable", summary.uiState.value.failed)
        assertFalse("the seeded session exists", summary.uiState.value.missing)
        assertTrue("the finished Room row earns the saved claim", summary.uiState.value.savedConfirmed)
        val text = "Recorded hold ${HoldWork.formatRange(seconds)}"
        awaitRead(
            what = "the loaded Summary reaches the actual native composition",
            now = { summary.uiState.value },
            condition = { compose.onAllNodes(matcher = hasTestTag(testTag = SummaryTags.CONTENT))
                .fetchSemanticsNodes().isNotEmpty() },
        )
        compose.onNodeWithTag(testTag = SummaryTags.CONTENT).performScrollToNode(matcher = hasText(text = text))
        compose.onNodeWithText(text).assertIsDisplayed()
        wholeValue(text)
        assertEquals(seconds, summary.uiState.value.summary.highlights.single().topSet?.durationSeconds)
        capture("summary-$seconds")
        compose.runOnIdle { exerciseOpen.value = true }
        awaitRead(
            what = "the actual Exercise screen collects this hold's Room history",
            now = { detail.uiState.value },
            condition = {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                detail.uiState.value.history.hasHistory
            },
        )
        compose.onNode(matcher = hasScrollAction()).performScrollToNode(matcher = hasText(text = text))
        compose.onNodeWithText(text).assertIsDisplayed()
        wholeValue(text)
        assertEquals(seconds, detail.uiState.value.history.sessions.single().topSet?.durationSeconds)
        assertNull(detail.uiState.value.history.sessions.single().estimatedOneRepMaxKg)
        compose.onNodeWithTag(testTag = ExerciseDetailTags.WEEKLY).assertDoesNotExist()
        compose.onNodeWithTag(testTag = ExerciseDetailTags.ESTIMATES).assertDoesNotExist()
        compose.onNodeWithText("0 reps").assertDoesNotExist()
        capture("exercise-history-$seconds")
        assertEquals(seconds, runBlocking { deps.database.workoutDao().getAllSets().single().durationSeconds })
    }

    private fun awaitRead(what: String, now: () -> Any?, condition: () -> Boolean) {
        compose.awaitThat(what = what, now = now, timeoutMillis = WAIT_MS, condition = condition)
    }

    private fun wholeValue(text: String) {
        val node = compose.onNodeWithText(text = text, useUnmergedTree = true).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        val layout = layouts.single()
        for (line in 0 until layout.lineCount) assertFalse("Hidden saved value", layout.isLineEllipsized(line))
        text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
            val glyph = layout.getBoundingBox(index)
            assertTrue("Saved glyph $index in $text: $glyph / ${node.size}",
                glyph.left >= -1f && glyph.right <= node.size.width + 1f &&
                    glyph.top >= -1f && glyph.bottom <= node.size.height + 1f)
        }
    }

    private suspend fun seed(seconds: Int, date: Long = STAMP, finished: Long = STAMP + 60_000L) {
        deps.database.exerciseDao().insertAll(listOf(ExerciseEntity(
            id = LIFT, name = "Static hold", muscleGroup = "Core", notes = "", isCustom = true,
            loadType = LoadType.BODYWEIGHT.name, movementKey = "hold", nameKey = "static hold")))
        val dao = deps.database.workoutDao()
        dao.upsertSession(WorkoutSessionEntity(id = SESSION, routineId = null, routineName = "Synthetic hold",
            date = date, notes = "", durationMinutes = 1, startedAt = STAMP, finishedAt = finished))
        dao.insertSessionExercises(listOf(SessionExerciseEntity(id = "planned", sessionId = SESSION,
            exerciseId = LIFT, sortOrder = 0, targetSets = 1, targetReps = 0, targetWeightKg = null,
            restSeconds = 90, targetSeconds = 30)))
        dao.insertSet(SetLogEntity(id = "held", sessionId = SESSION, exerciseId = LIFT, setNumber = 1,
            weightKg = 0.0, reps = 0, rpe = null, isWarmup = false, completedAt = STAMP + 30_000L,
            durationSeconds = seconds))
    }
    private fun capture(name: String) {
        val bitmap = compose.runOnIdle {
            val view = compose.activity.window.decorView
            Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                .also { view.draw(Canvas(it)) }
        }
        frames.mkdirs()
        FileOutputStream(File(frames, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private companion object {
        const val WAIT_MS = 20_000L
        const val LIFT = "synthetic-hold"
        const val SESSION = "synthetic-hold-session"
        const val STAMP = 1_786_396_800_000L
    }
}
