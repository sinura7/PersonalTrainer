package com.sinura.personaltrainer.ui.history

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.PersonalTrainerApp
import com.sinura.personaltrainer.domain.ActivityDraft
import com.sinura.personaltrainer.domain.ActivityOrigin
import com.sinura.personaltrainer.domain.ActivityStatus
import com.sinura.personaltrainer.domain.ActivityWrite
import com.sinura.personaltrainer.domain.CardioBlock
import com.sinura.personaltrainer.domain.CardioType
import com.sinura.personaltrainer.domain.CivilDate
import com.sinura.personaltrainer.domain.CivilYearMonth
import com.sinura.personaltrainer.domain.HistoryKind
import com.sinura.personaltrainer.domain.HistoryPeriodRange
import com.sinura.personaltrainer.testutil.forgetFirstApplication
import com.sinura.personaltrainer.ui.navigation.Route
import com.sinura.personaltrainer.ui.units.DateCopy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
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
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Shipping MainActivity/AppNav/default SavedState factory in Robolectric's isolated app storage.
 * A new Activity rebuilt from a Bundle proves owner-state reconstruction, not OS process death.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = PersonalTrainerApp::class, qualifiers = "w360dp-h800dp-xhdpi")
class HistoryPeriodNavigationTest {
    @get:Rule val compose = createEmptyComposeRule()

    private lateinit var app: PersonalTrainerApp
    private var controller: ActivityController<MainActivity>? = null

    @Before
    fun setUp() {
        forgetFirstApplication()
        app = ApplicationProvider.getApplicationContext()
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(app.packageName, true)
        app.markColdStartIntroShown()
        runBlocking {
            app.container.preferencesRepository.setOnboardingComplete(true)
            app.container.preferencesRepository.setLaunchPermissionsAsked(true)
        }
    }

    @After
    fun tearDown() {
        controller?.pause()?.stop()?.destroy()
        compose.waitForIdle()
        forgetFirstApplication()
    }

    @Test
    fun historicalPeriodSurvivesTabReturnAndANewActivityUsingTheShippingSavedStateFactory() {
        val today = app.container.time.captureNow().localDate
        val month = CivilYearMonth.from(today)
        val previous = month.minusMonths(1)
        val priorId = insertWalk(previous.atDay(12), "Synthetic previous month walk")
        val currentId = insertWalk(today, "Synthetic current month walk")
        val original = runBlocking { app.container.activityRepository.all() }
        assertEquals(setOf(priorId, currentId), original.map { it.id }.toSet())

        val intent = Intent(app, MainActivity::class.java)
        controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        awaitTag(HISTORY_TAB)
        compose.onNodeWithTag(HISTORY_TAB).performClick()
        val currentRange = HistoryPeriodRange(month.atDay(1).epochDay, today.epochDay + 1)
        awaitRange(currentRange)

        scrollTo(HistoryTags.PREVIOUS)
        compose.onNodeWithTag(HistoryTags.PREVIOUS).performClick()
        val priorRange = HistoryPeriodRange(previous.atDay(1).epochDay, month.atDay(1).epochDay)
        awaitRange(priorRange)
        assertRow(priorId)

        compose.onNodeWithTag(HOME_TAB).performClick()
        awaitTag(HISTORY_TAB)
        compose.onNodeWithTag(HISTORY_TAB).performClick()
        awaitRange(priorRange)
        assertRow(priorId)

        val oldActivity = controller!!.get()
        val oldOwner = oldActivity.viewModelStore
        val saved = Bundle()
        controller!!.pause().saveInstanceState(saved).stop().destroy()
        compose.waitForIdle()
        assertTrue(oldActivity.isDestroyed)
        controller = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create(saved).start().resume().visible()
        assertNotSame(oldActivity, controller!!.get())
        assertNotSame(oldOwner, controller!!.get().viewModelStore)
        awaitRange(priorRange)
        assertRow(priorId)

        scrollTo(HistoryTags.CURRENT)
        compose.onNodeWithTag(HistoryTags.CURRENT).performClick()
        awaitRange(currentRange)
        assertRow(currentId)
        assertEquals("exploration and restoration do not rewrite the saved activities", original,
            runBlocking { app.container.activityRepository.all() })
    }

    private fun insertWalk(date: CivilDate, title: String): String = runBlocking {
        val time = app.container.time
        val at = time.startOfDayMillis(date, time.defaultZoneId()) + 43_200_000L
        val result = app.container.confirmActivity(ActivityDraft(
            status = ActivityStatus.COMPLETED, origin = ActivityOrigin.BACKDATED, title = title,
            performedStart = time.capture(at), performedEnd = time.capture(at + 600_000L),
            blocks = listOf(CardioBlock(
                id = "block-$title", sortOrder = 0, type = CardioType.WALK, indoor = false,
                elapsedSeconds = 600, movingSeconds = null, distanceMeters = 1000.0,
                elevationMeters = null, heartRateBpm = null, energyKj = null, rpe = null, routeRef = null,
            )),
        ), time.captureNow())
        assertTrue(result.toString(), result is ActivityWrite.Accepted)
        (result as ActivityWrite.Accepted).session.id
    }

    private fun assertRow(id: String) {
        val tag = HistoryTags.row(HistoryKind.ACTIVITY, id)
        scrollTo(tag)
        compose.onNodeWithTag(tag).assertExists()
    }

    private fun awaitRange(range: HistoryPeriodRange) {
        awaitTag(HistoryTags.LIST)
        // Each range change is async; await the actual visible readout rather than a retained VM.
        compose.waitUntil(WAIT_MS) {
            runCatching {
                scrollTo(HistoryTags.RANGE_TITLE)
                compose.onNodeWithTag(HistoryTags.RANGE_TITLE).assertTextEquals(DateCopy.periodRange(range))
            }.isSuccess
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag(HistoryTags.LIST).performScrollToNode(hasTestTag(tag))
    }

    private fun awaitTag(tag: String) {
        compose.waitUntil(WAIT_MS) { compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val WAIT_MS = 20_000L
        val HISTORY_TAB = "navigation-${Route.History.path}"
        val HOME_TAB = "navigation-${Route.Home.path}"
    }
}
