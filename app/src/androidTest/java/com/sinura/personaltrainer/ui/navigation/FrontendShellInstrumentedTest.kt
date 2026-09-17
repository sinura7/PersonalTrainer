package com.sinura.personaltrainer.ui.navigation

import android.os.Build
import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.domain.LiveBarKind
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.ui.components.InstrumentRow
import com.sinura.personaltrainer.ui.components.ScreenHeader
import com.sinura.personaltrainer.ui.components.SecondaryGymButton
import com.sinura.personaltrainer.ui.theme.InstrumentType
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Bounds and real taps supplement references; a golden alone cannot prove reachability. */
@RunWith(Parameterized::class)
class FrontendShellInstrumentedTest(
    private val width: Int,
    private val height: Int,
    private val font: Float,
    private val rtl: Boolean,
    private val reduced: Boolean,
    private val scenario: String,
) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shellKeepsAllLabelsMetricsAndIndependentHitTargets() {
        var density = 0f
        var resumed = 0
        var lastClicked = 0
        val cardio = scenario == "cardio"
        val adverse = scenario != "normal"
        val title = if (adverse) "A valid saved routine with a deliberately long identity ".repeat(20)
            else "Lower A · squat and posterior chain"
        GoldenCapture.mountViewport(
            compose = compose, width = width.dp, height = height.dp,
            fontScale = font, reduceMotion = reduced,
        ) {
            val localDensity = LocalDensity.current
            SideEffect { density = localDensity.density }
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                var selected by remember { mutableStateOf(shippingTabs.first()) }
                Scaffold(
                    containerColor = Pit,
                    bottomBar = {
                        Column {
                            LiveSessionBar(
                                state = LiveSessionBarUiState(
                                    sessionId = "fixture", title = title,
                                    elapsedLabel = "12:34", workingSets = 8, totalSets = 10,
                                    restRemainingSeconds = 75, restRunning = !cardio, stale = adverse, staleHours = 25,
                                    kind = if (cardio) LiveBarKind.ACTIVITY else LiveBarKind.WORKOUT,
                                ),
                                applyNavInsets = false, onResume = { resumed++ }, onFinish = {}, onDiscard = {},
                                actionError = if (adverse) "Could not finish. Try again." else null,
                            )
                            InstrumentNavBar(tabs = shippingTabs, isSelected = { it == selected }, onSelect = { selected = it })
                        }
                    },
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("shell-list"),
                    ) {
                        item { ScreenHeader(title = "Today", titleStyle = InstrumentType.display) }
                        item {
                            Text(
                                "Your training, at a glance", modifier = Modifier.padding(Metrics.gutter),
                                style = InstrumentType.body, color = TextSecondary,
                            )
                        }
                        items(16) { index ->
                            InstrumentRow(title = "Exercise ${index + 1}", subtitle = "3 working sets · 8 reps")
                        }
                        item {
                            SecondaryGymButton(text = "Last reachable action", onClick = { lastClicked++ }, modifier = Modifier.testTag("shell-last"))
                        }
                    }
                }
            }
        }
        val root = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode().boundsInRoot
        assertEquals("actual logical width", width.toFloat(), root.width / density, 1f)
        assertEquals("actual logical height", height.toFloat(), root.height / density, 1f)
        println("FRONTEND_VIEWPORT shell ${width}x$height font=$font rtl=$rtl reduced=$reduced density=$density pixels=${root.width}x${root.height}")
        val bounds = shippingTabs.map { tab ->
            compose.onNodeWithTag("navigation-${tab.route.path}").assertIsDisplayed()
            val box = compose.onNodeWithTag("navigation-${tab.route.path}").fetchSemanticsNode().boundsInRoot
            assertTrue("navigation width floor: ${tab.label}", box.width / density >= 47.9f)
            assertTrue("navigation height floor: ${tab.label}", box.height / density >= 47.9f)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(tab.label.uppercase(), useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("label layout exists", layouts.isNotEmpty())
            assertFalse("full navigation label: ${tab.label}", layouts.any { it.hasVisualOverflow })
            box
        }
        bounds.forEachIndexed { index, rect ->
            bounds.drop(index + 1).forEach { assertFalse("navigation targets overlap", rect.overlaps(it)) }
        }
        if (width == 360 && font == 2f) {
            assertEquals(bounds[0].top, bounds[2].top, 1f)
            assertTrue("3 + 2 navigation", bounds[3].top >= bounds[0].bottom)
        }
        val resume = compose.onNodeWithTag(LiveSessionBarTestTags.ROOT).fetchSemanticsNode().boundsInRoot
        val overflow = compose.onNodeWithTag("live-session-actions").fetchSemanticsNode().boundsInRoot
        assertFalse("overflow independent of resume", resume.overlaps(overflow))
        compose.onNodeWithTag("live-session-elapsed", useUnmergedTree = true).assertIsDisplayed()
        if (!cardio) compose.onNodeWithTag("live-session-sets", useUnmergedTree = true).assertIsDisplayed()
        else compose.onNodeWithTag("live-session-sets", useUnmergedTree = true).assertDoesNotExist()
        for (tag in if (cardio) listOf("live-session-elapsed") else listOf("live-session-elapsed", "live-session-sets")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals("English metric order survives RTL layout", ResolvedTextDirection.Ltr, layouts.single().getParagraphDirection(0))
        }
        if (Build.VERSION.SDK_INT == 29) {
            GoldenImageAssert.assertMatches(
                "frontend-shell-${width}x$height-font${(font * 10).toInt()}-${if (rtl) "rtl" else "ltr"}-${if (reduced) "still" else "motion"}${if (adverse) "-$scenario" else ""}-api29",
                GoldenCapture.capture(compose),
            )
        }
        compose.onNodeWithTag("live-session-elapsed", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, resumed) }
        if (!cardio) compose.onNodeWithTag("live-session-sets", useUnmergedTree = true).performTouchInput { click() }
        val expectedResumes = if (cardio) 1 else 2
        compose.runOnIdle { assertEquals(expectedResumes, resumed) }
        compose.onNodeWithTag("live-session-actions").performClick()
        compose.onNodeWithText(if (cardio) "Finish session" else "Finish workout").assertIsDisplayed()
        compose.runOnIdle { assertEquals("overflow cannot resume", expectedResumes, resumed) }
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("shell-list").performScrollToNode(hasTestTag("shell-last"))
        compose.onNodeWithTag("shell-last").assertIsDisplayed()
        val last = compose.onNodeWithTag("shell-last").fetchSemanticsNode()
        val top = last.positionInRoot.y
        val bottom = top + last.size.height
        val list = compose.onNodeWithTag("shell-list").fetchSemanticsNode().boundsInRoot
        assertTrue("complete last action fits above chrome", bottom <= resume.top + 1 && top >= list.top - 1)
        compose.onNodeWithTag("shell-last").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, lastClicked) }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1} font{2} rtl{3} reduced{4} {5}")
        fun profiles(): List<Array<Any>> = buildList {
            for ((width, height) in listOf(360 to 640, 360 to 800, 412 to 840, 600 to 840, 640 to 360)) {
                for (font in listOf(1f, 1.6f, 2f)) add(arrayOf(width, height, font, false, false, "normal"))
            }
            add(arrayOf(360, 800, 1f, true, false, "normal"))
            add(arrayOf(360, 800, 2f, true, false, "normal"))
            add(arrayOf(360, 800, 1f, false, true, "normal"))
            add(arrayOf(360, 640, 2f, false, false, "long-error"))
            add(arrayOf(640, 360, 2f, false, false, "long-error"))
            add(arrayOf(640, 360, 2f, false, false, "cardio"))
        }
    }
}
