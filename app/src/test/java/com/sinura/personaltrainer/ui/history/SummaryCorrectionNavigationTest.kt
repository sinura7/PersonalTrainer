package com.sinura.personaltrainer.ui.history

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.PowerManager
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.SavePosture
import com.sinura.personaltrainer.domain.SetLog
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.domain.WorkoutSession
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import com.sinura.personaltrainer.testutil.openFinishFromOverflow
import com.sinura.personaltrainer.testutil.waitUntilFinishEnabledInOverflow
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarTestTags
import com.sinura.personaltrainer.ui.navigation.Route
import com.sinura.personaltrainer.ui.summary.SummaryTags
import com.sinura.personaltrainer.ui.components.EndWorkoutTags
import com.sinura.personaltrainer.ui.workout.WorkoutTestTags
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Real MainActivity/AppNav/default SavedState factories and Room, using synthetic local work.
 * Returning to a retained Summary must reread an acknowledged Detail correction. A new
 * Activity from a Bundle separately proves reconstruction, without claiming OS process death.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class SummaryCorrectionNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: PersonalTrainerApp
    private var controller: ActivityController<MainActivity>? = null
    private val artifactDirectory = File("build/screen-renders/summary-correction-nav/${UUID.randomUUID()}")

    @Before
    fun setUp() {
        forgetFirstApplication()
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(app.packageName, true)
        app.markColdStartIntroShown()
        runBlocking(context = Dispatchers.IO) {
            withTimeout(timeMillis = WAIT_MS) {
                app.container.preferencesRepository.setOnboardingComplete(complete = true)
                app.container.preferencesRepository.setLaunchPermissionsAsked(asked = true)
                app.container.preferencesRepository.setSavePosture(posture = SavePosture.LOCAL)
                app.container.preferencesRepository.setWeightUnit(unit = WeightUnit.KG)
            }
        }
    }

    @After
    fun tearDown() {
        app.container.restTimerController.stop()
        controller?.pause()?.stop()?.destroy()
        compose.waitForIdle()
        forgetFirstApplication()
    }

    @Test
    fun savedCorrectionRefreshesTheRetainedSummaryBeforeDoneReturnsHome() {
        exerciseJourney(reconstructDetail = false)
    }

    @Test
    fun savedCorrectionSurvivesANewActivityWhileDetailIsOpen() {
        exerciseJourney(reconstructDetail = true)
    }

    private fun exerciseJourney(reconstructDetail: Boolean) {
        val originalSet = seedLiveWorkout()
        val intent = Intent(app, MainActivity::class.java)
        controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()

        awaitTag(tag = LiveSessionBarTestTags.ROOT)
        compose.onNodeWithTag(testTag = LiveSessionBarTestTags.ROOT).performClick()
        compose.waitUntilFinishEnabledInOverflow()
        compose.openFinishFromOverflow()
        compose.waitUntil(timeoutMillis = WAIT_MS, condition = {
            compose.onAllNodes(hasTestTag(WorkoutTestTags.FINISH_SHEET)).fetchSemanticsNodes().isNotEmpty()
        })
        compose.onNodeWithTag(WorkoutTestTags.FINISH_SHEET).assertIsDisplayed()
        compose.onNodeWithTag(EndWorkoutTags.SAVE).assertIsEnabled().performClick()

        awaitTotalVolume(kilograms = 500)
        val finished = storedSession(sessionId = originalSet.sessionId)
        assertTrue("the finish dialog durably saved this exact session", finished.isFinished)
        assertEquals("finishing conserves the original set", originalSet, finished.sets.single())
        capture(name = "summary-before-correction-500kg")

        compose.onNodeWithTag(testTag = SummaryTags.OPEN_SESSION).assertIsEnabled().performClick()
        awaitTag(tag = SessionDetailTestTags.CONTENT)
        val rowTag = FilledLiftCardTags.setRow(setId = originalSet.id)
        compose.onNodeWithTag(testTag = SessionDetailTestTags.CONTENT)
            .performScrollToNode(matcher = hasTestTag(testTag = rowTag))
        val edit = hasTestTag(testTag = SessionDetailTestTags.EDIT_SET) and
            hasAnyAncestor(matcher = hasTestTag(testTag = rowTag))
        compose.onNode(matcher = edit).performScrollTo().assertIsEnabled().performClick()
        awaitTag(tag = SetEditTestTags.CONTENT)
        val sheet = hasAnyAncestor(matcher = hasTestTag(testTag = SetEditTestTags.CONTENT)) and
            hasAnyAncestor(matcher = isDialog())
        compose.onNode(matcher = hasTestTag(testTag = "Type a weight") and sheet)
            .assertContentDescriptionEquals("Weight 100 kg")
        compose.onNode(matcher = hasTestTag(testTag = "Type a rep count") and sheet)
            .assertContentDescriptionEquals("reps 5")
        compose.onNode(matcher = hasText(text = "+1") and sheet)
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNode(matcher = hasTestTag(testTag = "Type a rep count") and sheet)
            .assertContentDescriptionEquals("reps 6")
        compose.onNode(matcher = hasText(text = "Save") and sheet)
            .performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        // Pump the paused Main looper until the writer has acknowledged its IO fence and save.
        // A blocking Room read here would otherwise prevent that Main continuation from running.
        awaitAbsent(tag = SetEditTestTags.CONTENT)

        val expectedSet = originalSet.copy(reps = 6)
        val corrected = runBlocking(context = Dispatchers.IO) {
            withTimeout(timeMillis = WAIT_MS) {
                app.container.workoutRepository.observeSession(id = originalSet.sessionId)
                    .first(predicate = { it?.sets?.singleOrNull() == expectedSet })!!
            }
        }
        assertEquals("correction conserves ID, completion stamp, ordinal and all other set facts",
            expectedSet, corrected.sets.single())
        assertEquals("correction preserves the finished session and its planned work",
            finished.copy(sets = listOf(expectedSet)), corrected)

        if (reconstructDetail) {
            val oldActivity = controller!!.get()
            val oldOwner = oldActivity.viewModelStore
            val saved = Bundle()
            controller!!.pause().saveInstanceState(saved).stop().destroy()
            compose.waitForIdle()
            assertTrue("the original activity was destroyed", oldActivity.isDestroyed)
            controller = Robolectric.buildActivity(MainActivity::class.java, intent)
                .create(saved).start().resume().visible()
            assertNotSame("reconstruction uses a new Activity", oldActivity, controller!!.get())
            assertNotSame("reconstruction uses a new ViewModel owner", oldOwner, controller!!.get().viewModelStore)
            awaitTag(tag = SessionDetailTestTags.CONTENT)
            compose.onNodeWithTag(testTag = SessionDetailTestTags.CONTENT)
                .performScrollToNode(matcher = hasTestTag(testTag = rowTag))
            compose.onNode(matcher = hasText(text = "100 kg × 6") and
                hasAnyAncestor(matcher = hasTestTag(testTag = rowTag)), useUnmergedTree = true)
                .assertIsDisplayed()
        }

        compose.onNodeWithTag(testTag = SessionDetailTestTags.BACK).assertIsEnabled().performClick()
        awaitTotalVolume(kilograms = 600)
        compose.onNode(matcher = hasContentDescription(value = "kg volume, 500 kg")).assertDoesNotExist()
        capture(name = "summary-after-correction-600kg")
        compose.onNodeWithTag(testTag = SummaryTags.DONE).assertIsEnabled().performClick()
        awaitTag(tag = HOME_TAB)
        awaitAbsent(tag = SummaryTags.CONTENT)
        awaitAbsent(tag = LiveSessionBarTestTags.ROOT)
        compose.onNodeWithTag(testTag = HOME_TAB).assertIsSelected()
        assertEquals("Done leaves the corrected saved workout intact", corrected,
            storedSession(sessionId = originalSet.sessionId))
        assertNull("Done leaves no live workout", runBlocking(context = Dispatchers.IO) {
            withTimeout(timeMillis = WAIT_MS) { app.container.workoutRepository.getInProgress() }
        })
    }

    private fun seedLiveWorkout(): SetLog = runBlocking(context = Dispatchers.IO) {
        withTimeout(timeMillis = WAIT_MS) {
            // This owned fixture needs the catalog before recording its synthetic routine.
            // Application startup also performs imports and planner work before its seed.
            app.container.dbMaintenance.seedCatalog()
            val catalogId = DefaultExercises.catalog().first { it.name == "Barbell Back Squat" }.id
            val exercise = app.container.exerciseRepository.observeById(id = catalogId)
                .first(predicate = { it != null })!!
            val routine = app.container.routineRepository.create(name = "Synthetic summary correction")
            app.container.routineRepository.addExercise(
                routineId = routine.id, exercise = exercise, targetSets = 3, targetReps = 5,
                targetWeightKg = 100.0, restSeconds = 120,
            )
            val planned = checkNotNull(app.container.routineRepository.getById(id = routine.id))
            val session = app.container.workoutRepository.startRoutine(routine = planned)
            app.container.workoutRepository.logSet(
                sessionId = session.id, exerciseId = exercise.id, weightKg = 100.0,
                reps = 5, rpe = 8, isWarmup = false,
            )
            val saved = checkNotNull(app.container.workoutRepository.getSession(id = session.id))
            assertEquals("the real routine supplied three planned sets", 3, saved.exercises.single().targetSets)
            assertEquals("only the one performed set is recorded", 1, saved.sets.size)
            saved.sets.single()
        }
    }

    private fun storedSession(sessionId: String): WorkoutSession = runBlocking(context = Dispatchers.IO) {
        withTimeout(timeMillis = WAIT_MS) {
            checkNotNull(app.container.workoutRepository.getSession(id = sessionId))
        }
    }

    private fun awaitTotalVolume(kilograms: Int) {
        awaitTag(tag = SummaryTags.CONTENT)
        val spoken = "kg volume, $kilograms kg"
        val readout = hasContentDescription(value = spoken) and
            hasAnyAncestor(matcher = hasTestTag(testTag = SummaryTags.CONTENT))
        compose.waitUntil(timeoutMillis = WAIT_MS, condition = {
            compose.onAllNodes(matcher = readout).fetchSemanticsNodes().isNotEmpty()
        })
        compose.onNodeWithTag(testTag = SummaryTags.CONTENT).performScrollToNode(matcher = readout)
        compose.onNode(matcher = readout).assertContentDescriptionEquals(spoken).assertIsDisplayed()
    }

    private fun awaitTag(tag: String, enabled: Boolean = false) {
        val matcher = if (enabled) hasTestTag(testTag = tag) and isEnabled() else hasTestTag(testTag = tag)
        compose.waitUntil(timeoutMillis = WAIT_MS, condition = {
            compose.onAllNodes(matcher = matcher).fetchSemanticsNodes().isNotEmpty()
        })
    }

    private fun awaitAbsent(tag: String) {
        compose.waitUntil(timeoutMillis = WAIT_MS, condition = {
            compose.onAllNodes(matcher = hasTestTag(testTag = tag)).fetchSemanticsNodes().isEmpty()
        })
    }

    private fun capture(name: String) {
        val bitmap = compose.runOnIdle {
            val decor = controller!!.get().window.decorView
            assertTrue("the real Activity has a measured native window", decor.width > 0 && decor.height > 0)
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also {
                decor.draw(Canvas(it))
            }
        }
        try {
            assertTrue("the frame archive can be created", artifactDirectory.isDirectory || artifactDirectory.mkdirs())
            FileOutputStream(File(artifactDirectory, "$name.png")).use {
                assertTrue("the native frame was encoded", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        val HOME_TAB = "navigation-${Route.Home.path}"
    }
}
