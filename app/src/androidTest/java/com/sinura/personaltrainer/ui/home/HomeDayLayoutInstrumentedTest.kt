package com.sinura.personaltrainer.ui.home

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.WeekStripTags
import com.sinura.personaltrainer.ui.navigation.InstrumentNavBar
import com.sinura.personaltrainer.ui.navigation.LiveSessionBar
import com.sinura.personaltrainer.ui.navigation.LiveSessionBarUiState
import com.sinura.personaltrainer.ui.navigation.shippingTabs
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.MultipleFailureException

/** Real HomeScreen/ViewModel on private Room and pinned insights, with shipping app chrome. */
@RunWith(Parameterized::class)
class HomeDayLayoutInstrumentedTest(
    private val width: Int,
    private val height: Int,
    private val font: Float,
    private val scenario: String,
) {
    @get:Rule val compose = createComposeRule()
    private val fixture = HomeDayFixture(scenario)
    private val visualFailures = mutableListOf<Throwable>()
    @After fun cleanup() {
        try {
            fixture.close()
        } finally {
            // Keep every state artifact from a profile, then fail the test.
            // Missing references and mismatches must never turn into a skip/pass.
            MultipleFailureException.assertEmpty(visualFailures)
        }
    }

    @Test fun selectedDateRecordsAndTargetsStayTruthfulAndReachable() {
        fixture.seed()
        var opened = ""
        GoldenCapture.mountViewport(
            compose = compose, width = width.dp, height = height.dp, fontScale = font,
            reduceMotion = true, statusBar = 24.dp, navigationBar = 24.dp,
        ) {
            // Isolate update-check presentation from the real application singleton.
            val context = LocalContext.current
            val testContext = remember(context) {
                object : ContextWrapper(context) {
                    override fun getApplicationContext(): Context = this
                }
            }
            CompositionLocalProvider(
                LocalContext provides testContext,
                LocalTodayEpochDay provides HomeDayFixture.TODAY,
                LocalLayoutDirection provides if (scenario == "rtl") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Scaffold(bottomBar = {
                    Column {
                        if (scenario == "live") LiveSessionBar(
                            state = LiveSessionBarUiState(
                                sessionId = "live", title = "Live workout", elapsedLabel = "12:34",
                                workingSets = 3, totalSets = 12,
                                restRemainingSeconds = 0, restRunning = false,
                                stale = false, staleHours = 0,
                            ),
                            onResume = { opened = "live" }, onFinish = {}, onDiscard = {}, applyNavInsets = false,
                        )
                        InstrumentNavBar(tabs = shippingTabs, isSelected = { it == shippingTabs.first() }, onSelect = {})
                    }
                }) { padding ->
                    Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
                        HomeScreen(
                            onResumeWorkout = { opened = it }, onOpenPlan = {},
                            onOpenSession = { opened = "workout:$it" },
                            onOpenActivity = { opened = "activity:$it" },
                            viewModel = fixture.vm,
                        )
                    }
                }
            }
        }
        compose.waitUntil(15_000) { !fixture.vm.uiState.value.isLoading }
        GoldenCapture.awaitViewport(compose, width, height)
        compose.onNodeWithTag(HomeTags.CONTENT).performScrollToIndex(0)
        compose.onNodeWithTag(WeekStripTags.cell(HomeDayFixture.TODAY)).assertIsSelected()
        capture("today")
        if (scenario == "cross-date" || scenario == "missing-link") {
            val tag = if (scenario == "cross-date") HomeTags.record("historical") else HomeTags.agendaRow("planned")
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToNode(hasTestTag(tag))
            compose.onNodeWithText(
                if (scenario == "cross-date") "Recorded on 4 Oct · Lower A" else "Saved session unavailable.",
            ).assertIsDisplayed()
            capture("linked-record")
        }

        // Traverse both ends. The actual selectable bounds, not expanded hit areas,
        // must meet 48 dp even when all seven days cannot fit together.
        val first = fixture.vm.uiState.value.weekStartEpochDay
        for (day in listOf(first, first + 6)) {
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToIndex(0)
            val cell = compose.onNodeWithTag(WeekStripTags.cell(day)).performScrollTo().assertIsDisplayed()
            val node = cell.fetchSemanticsNode()
            val density = node.layoutInfo.density.density
            assertTrue("day width", node.boundsInRoot.width / density >= 47.5f)
            assertTrue("day height", node.boundsInRoot.height / density >= 47.5f)
            cell.performClick().assertIsSelected()
        }
        if (scenario != "empty") {
            val past = HomeDayFixture.TODAY - 2
            compose.onNodeWithTag(WeekStripTags.cell(past)).performScrollTo().performClick()
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToIndex(0)
            compose.onNodeWithText("TRAINING COMPLETE").assertIsDisplayed()
            compose.onNodeWithText("TRAINED TODAY").assertDoesNotExist()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("TRAINING COMPLETE")
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            capture("historical")
            assertTrue(layouts.isNotEmpty())
            // Compose can retain the paragraph's maximum width while measuring
            // Text to its content. Check visible lines and characters directly.
            assertFalse("headline stays whole", layouts.any { layout ->
                layout.didOverflowHeight || (0 until layout.lineCount).any {
                    layout.isLineEllipsized(it) ||
                        layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f
                } || layout.getLineEnd(layout.lineCount - 1, visibleEnd = true) != "TRAINING COMPLETE".length
            })
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToNode(hasTestTag(HomeTags.record("historical")))
            compose.onNodeWithTag(HomeTags.record("historical")).performClick()
            compose.runOnIdle { assertEquals("workout:historical", opened) }
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToNode(hasTestTag(HomeTags.record("cardio")))
            compose.onNodeWithTag(HomeTags.record("cardio")).performClick()
            compose.runOnIdle { assertEquals("activity:cardio", opened) }
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToIndex(0)
            compose.onNodeWithTag(HomeTags.TODAY).performClick()
            compose.onNodeWithTag(WeekStripTags.cell(HomeDayFixture.TODAY)).assertIsSelected()
        }
        if (scenario == "live") compose.onNodeWithTag(HomeTags.START).assertDoesNotExist()
        else {
            compose.onNodeWithTag(HomeTags.CONTENT).performScrollToNode(hasTestTag(HomeTags.START))
            compose.onNodeWithTag(HomeTags.START).assertIsDisplayed()
        }
    }

    private fun capture(state: String) {
        compose.waitForIdle()
        val name = "frontend-home-${width}x$height-font${(font * 10).toInt()}-$scenario-$state-api${Build.VERSION.SDK_INT}"
        val image = GoldenCapture.capture(compose)
        if (Build.VERSION.SDK_INT == 29) {
            try {
                GoldenImageAssert.assertMatches(name, image)
            } catch (failure: AssertionError) {
                visualFailures.add(failure)
            }
        } else NativeArtifacts.write(name, image.asAndroidBitmap())
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-{3}")
        fun profiles(): List<Array<Any>> = buildList {
            for ((w, h) in listOf(360 to 640, 360 to 800, 412 to 840, 600 to 840, 640 to 360)) {
                for (scale in listOf(1f, 1.6f, 2f)) add(arrayOf(w, h, scale, "normal"))
            }
            for (state in listOf("empty", "live", "long", "rtl", "cross-date", "missing-link")) {
                add(arrayOf(360, 800, 1f, state))
                add(arrayOf(360, 640, 2f, state))
            }
        }
    }
}
