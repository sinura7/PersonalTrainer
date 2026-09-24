package com.sinura.personaltrainer.ui.workout

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.domain.RestHonestyCopy
import com.sinura.personaltrainer.domain.RestNotificationCopy
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.RestCyan
import com.sinura.personaltrainer.ui.theme.TextSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The rest page in the room it has: on a tall page the ring takes about half the height, on
 * a page under 560 dp or at large text the ring gives way to the clock alone, and the page's
 * context scrolls rather than pushing its actions off screen. At rest the ring is empty;
 * running, it drains in cyan. The page says when rest alerts are off, with the fix one tap
 * away, and when a rest may run late.
 *
 * These were lines of RestTimerScreen.kt read as text (`val showRing = !largeText &&
 * maxHeight >= 560.dp`, `verticalScroll(rememberScrollState())`, `ringSize: Dp =
 * REST_RING_SIZE`, `remainingSeconds = if (rest.running) safeRemaining else 0`,
 * `RestHonestyCopy.pick(`, `notificationsEnabled = notificationsEnabled`, `exactBestEffort =
 * rest.exactAlarmBestEffort`, `RestHonestyRow(`), the rest page's half of what
 * LandscapeChromeTest and RestIdlePresentationTest used to read.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class RestPageFitRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var pageHeight by mutableStateOf(800)
    private var rest by mutableStateOf(RestTimerUiState(remainingSeconds = 92, totalSeconds = 120, running = false))
    private var notificationFixes = 0

    @Test
    fun aTallPageGivesTheRingAboutHalfItsHeight() {
        showPage()
        // 800 dp tall: 48 % would be 384 dp, and the ring stops at 320.
        assertEquals(320f, ringWidthDp(), 1f)
        pageHeight = 620
        compose.waitForIdle()
        assertEquals(620 * 0.48f, ringWidthDp(), 1f)
    }

    @Test
    fun aShortPageShowsTheClockWithoutTheRing() {
        pageHeight = 500
        showPage()
        compose.onNodeWithTag(RestFloorTags.RING).assertDoesNotExist()
        compose.onNodeWithTag(RestFloorTags.CLOCK, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theRingNeedsFiveHundredSixtyDpOfPage() {
        // One dp short of the room the ring needs: the clock stands alone.
        pageHeight = 559
        showPage()
        compose.onNodeWithTag(RestFloorTags.RING).assertDoesNotExist()
        compose.onNodeWithTag(RestFloorTags.CLOCK, useUnmergedTree = true).assertIsDisplayed()
        // At exactly that room the ring comes back, at 48 % of the page.
        pageHeight = 560
        compose.waitForIdle()
        assertEquals(560 * 0.48f, ringWidthDp(), 1f)
    }

    @Test
    fun atLargeTextThePageDropsTheRingAndScrollsToItsLastLine() {
        pageHeight = 640
        showPage(fontScale = 2f, notificationsEnabled = false)
        compose.onNodeWithTag(RestFloorTags.RING).assertDoesNotExist()
        compose.onNodeWithTag(RestFloorTags.CLOCK, useUnmergedTree = true).assertIsDisplayed()
        // The context scrolls, so its last line is reachable and the actions stay put.
        compose.onNode(hasTestTag(NOTIFICATION_FIX) and hasAnyAncestor(hasScrollAction())).assertExists()
        compose.onNodeWithTag(NOTIFICATION_FIX).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag(RestFloorTags.START).assertIsDisplayed()
    }

    @Test
    fun atRestTheRingIsEmptyAndRunningItDrainsInCyan() {
        showPage()
        val idle = ringSamples()
        assertEquals("an idle ring shows none of the time the store still holds", 0, idle.count { isNear(it, TextSecondary) })
        assertEquals(0, idle.count { isNear(it, RestCyan) })
        rest = RestTimerUiState(remainingSeconds = 60, totalSeconds = 120, running = true)
        compose.waitForIdle()
        val running = ringSamples()
        val cyan = running.count { isNear(it, RestCyan) }
        assertTrue("half the rest left drains half the ring, was $cyan of ${running.size}", cyan in (running.size * 35 / 100)..(running.size * 65 / 100))
    }

    @Test
    fun withRestAlertsOffThePageSaysSoAndOffersTheFix() {
        showPage(notificationsEnabled = false)
        compose.onNodeWithTag(NOTIFICATION_FIX).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(RestNotificationCopy.RECOVERY_TITLE, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(NOTIFICATION_FIX).performClick()
        assertEquals(1, notificationFixes)
    }

    @Test
    fun aRestThatMayRunLateSaysSoOnThePage() {
        rest = RestTimerUiState(remainingSeconds = 92, totalSeconds = 120, running = true, exactAlarmBestEffort = true)
        showPage()
        compose.onNodeWithTag(HONESTY).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(RestHonestyCopy.EXACT_DENIED).assertIsDisplayed()
    }

    private fun showPage(fontScale: Float = 1f, notificationsEnabled: Boolean = true) {
        val openNotifications: () -> Unit = { notificationFixes += 1 }
        compose.showFloor(fontScale = fontScale) {
            Box(modifier = Modifier.width(360.dp).height(pageHeight.dp).background(Pit)) {
                RestFloorBody(
                    rest = rest,
                    floor = RestFloorContext(exerciseName = "Leg extension", lastSetLine = null, sessionTargetLine = null),
                    onSkip = {},
                    onAdjust = {},
                    onSelectPreset = {},
                    onCustom = { true },
                    onStart = {},
                    onAcknowledgeBattery = {},
                    onBackToBar = {},
                    notificationsEnabled = notificationsEnabled,
                    onOpenNotifications = openNotifications,
                )
            }
        }
    }

    private fun ringWidthDp(): Float {
        val ring = compose.onNodeWithTag(RestFloorTags.RING).assertIsDisplayed().windowBounds()
        return ring.width / compose.density.density
    }

    /** The ring's stroke (10 dp), read along its middle all the way round; never the background. */
    private fun ringSamples(): List<Int> {
        val ring = compose.onNodeWithTag(RestFloorTags.RING).windowBounds()
        val frame = compose.drawWindow()
        val background = frame.getPixel((ring.left + 2f).toInt(), (ring.top + 2f).toInt())
        val samples = frame.around(ring.center.x, ring.center.y, ring.width / 2f - 5f * compose.density.density)
        assertTrue("the samples lie on the drawn ring", samples.count { it != background } >= samples.size * 90 / 100)
        return samples
    }

    private companion object {
        const val NOTIFICATION_FIX = "workout-notif-recovery"
        const val HONESTY = "workout-rest-honesty"
    }
}
