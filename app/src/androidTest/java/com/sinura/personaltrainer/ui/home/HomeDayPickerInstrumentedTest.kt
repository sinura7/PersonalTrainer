package com.sinura.personaltrainer.ui.home

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import com.sinura.personaltrainer.domain.WeekBoard
import com.sinura.personaltrainer.domain.DayFill
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.units.LocalTodayEpochDay
import java.io.FileInputStream
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.model.Statement

/** Real Android dialog windows, full day labels and equivalent 48dp selection actions. */
@RunWith(Parameterized::class)
class HomeDayPickerInstrumentedTest(
    private val width: Int, private val height: Int, private val font: Float, private val rtl: Boolean,
) {
    private val compose = createComposeRule()
    private val window = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                check(Build.HARDWARE in setOf("ranchu", "goldfish") && Build.FINGERPRINT.startsWith("Android/sdk_"))
                val size = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
                val density = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
                val previousFont = shell("settings get system font_scale").trim()
                try {
                    shell("wm density 160")
                    shell("wm size ${width}x$height")
                    shell("settings put system font_scale $font")
                    awaitSystemFont(font)
                    base.evaluate()
                } finally {
                    shell("wm size ${size ?: "reset"}")
                    shell("wm density ${density ?: "reset"}")
                    if (previousFont.toFloatOrNull() != null) shell("settings put system font_scale $previousFont")
                    else shell("settings delete system font_scale")
                    awaitSystemFont(previousFont.toFloatOrNull() ?: 1f)
                    assertEquals(size, Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1))
                    assertEquals(density, Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1))
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(window).around(compose)

    @Test fun everyDayIsReachableAndChoosingItUpdatesTheDateWithoutTruncation() {
        val monday = LocalDate.of(2026, 9, 28).toEpochDay()
        val today = monday + 4
        val selected = mutableLongStateOf(monday + 2)
        val open = mutableStateOf(false)
        val rest = WeekBoard.forWeek(monday, emptyList(), emptyList())
        val cells = listOf(
            rest[0].copy(fill = DayFill.NONE, caption = "Strength", plannedCount = 1),
            rest[1].copy(fill = DayFill.NONE, caption = "Cardio", plannedCount = 1, missedCount = 1),
            rest[2].copy(fill = DayFill.PARTIAL, caption = "2 activities", plannedCount = 2, resolvedCount = 1, completedCount = 1),
            rest[3].copy(fill = DayFill.ALL, caption = "Strength", plannedCount = 1, resolvedCount = 1, skippedCount = 1),
            rest[4].copy(fill = DayFill.ALL, caption = "Strength", plannedCount = 1, resolvedCount = 1, completedCount = 1),
            rest[5], rest[6],
        )
        GoldenCapture.mountDevice(compose = compose, fontScale = font) {
            CompositionLocalProvider(
                LocalTodayEpochDay provides today,
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                Surface(Modifier.fillMaxSize(), color = Pit) {
                  Column(Modifier.fillMaxSize().padding(Metrics.gutter)) {
                    HomeMasthead(
                        epochDay = selected.longValue, headline = "Your day.",
                        onBackToToday = if (selected.longValue == today) null else { { selected.longValue = today } },
                        onChooseDay = { open.value = true },
                    )
                    if (open.value) {
                        HomeDayPicker(
                            cells = cells, selected = selected.longValue, today = today,
                            enabled = true, canSelect = { true },
                            onSelect = { selected.longValue = it; open.value = false },
                            onDismiss = { open.value = false },
                        )
                    }
                  }
                }
            }
        }
        compose.waitUntil(15000) {
            val node = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode()
            kotlin.math.abs(node.boundsInRoot.width / node.layoutInfo.density.density - width) < 1f
        }
        assertCompleteDate()
        proveTarget(HomeDayTags.CHOOSE)
        proveTarget(HomeTags.BACK_TO_TODAY)
        compose.onNodeWithTag(HomeDayTags.CHOOSE).performClick()
        compose.onNodeWithTag(HomeDayTags.PICKER).assertIsDisplayed()
        val chosen = compose.onNodeWithTag(HomeDayTags.day(selected.longValue)).performScrollTo()
        chosen.assertIsDisplayed().assertIsSelected()
        for (cell in cells) {
            val row = compose.onNodeWithTag(HomeDayTags.day(cell.epochDay)).performScrollTo()
            row.assertIsDisplayed().assertIsEnabled()
            assertEquals("Native dialog font scale", font, row.fetchSemanticsNode().layoutInfo.density.fontScale, .01f)
            assertEquals(Role.RadioButton, row.fetchSemanticsNode().config[SemanticsProperties.Role])
            assertTrue(row.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
                .contains(WeekBoard.status(cell)))
            proveTarget(HomeDayTags.day(cell.epochDay))
        }
        val suffix = "${width}-${height}-font${(font * 10).toInt()}-${if (rtl) "rtl" else "ltr"}"
        NativeArtifacts.write("home-day-picker-$suffix", checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))
        compose.onNodeWithTag(HomeDayTags.day(monday + 6)).performScrollTo().performClick()
        compose.waitUntil(15000) { !open.value }
        assertEquals(monday + 6, selected.longValue)
        assertCompleteDate()
        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertIsDisplayed().performClick()
        assertEquals(today, selected.longValue)
        compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertDoesNotExist()
        NativeArtifacts.write("home-day-selected-$suffix", checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))
    }

    private fun assertCompleteDate() {
        val node = compose.onNodeWithTag(HomeTags.DATE, useUnmergedTree = true).assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        val layout = layouts.single()
        if (layout.hasVisualOverflow) {
            println("HOME_DATE_LAYOUT text=${layout.layoutInput.text.text} size=${layout.size} widthOverflow=${layout.didOverflowWidth} heightOverflow=${layout.didOverflowHeight} paragraphWidth=${layout.multiParagraph.width} paragraphHeight=${layout.multiParagraph.height} constraints=${layout.layoutInput.constraints} lines=${layout.lineCount}")
            NativeArtifacts.write("home-date-overflow-${width}-${height}-font${(font * 10).toInt()}",
                checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()))
        }
        // Paragraph width includes unused available space (304px versus a 216px date
        // in the diagnostic). Check actual line/glyph ink, not that layout allocation.
        for (line in 0 until layout.lineCount) {
            assertTrue("date line $line clipped", layout.getLineRight(line) <= layout.size.width + 1f)
            assertTrue("date line $line outside start", layout.getLineLeft(line) >= -1f)
        }
        assertFalse(layout.didOverflowHeight)
        for (index in layout.layoutInput.text.indices) {
            assertTrue("date glyph $index clipped", layout.getBoundingBox(index).right <= layout.size.width + 1f)
            assertTrue("date glyph $index outside start", layout.getBoundingBox(index).left >= -1f)
        }
        assertTrue((0 until layouts.single().lineCount).none { layouts.single().isLineEllipsized(it) })
    }

    private fun proveTarget(tag: String) {
        val node = compose.onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode()
        val minimum = with(node.layoutInfo.density) { 48.dp.roundToPx() }
        assertTrue("$tag height", node.boundsInRoot.height + 1 >= minimum)
        assertTrue("$tag width", node.boundsInRoot.width + 1 >= minimum)
    }

    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation
        .executeShellCommand(command).use { pipe -> FileInputStream(pipe.fileDescriptor).use { String(it.readBytes()) } }

    private fun awaitSystemFont(expected: Float) {
        val resources = ApplicationProvider.getApplicationContext<android.app.Application>().resources
        val deadline = SystemClock.elapsedRealtime() + 15000
        while (kotlin.math.abs(resources.configuration.fontScale - expected) > .01f && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(50)
        }
        assertEquals("Actual Android font scale", expected, resources.configuration.fontScale, .01f)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-rtl{3}")
        fun profiles(): List<Array<Any>> = buildList<Array<Any>> {
            for ((width, height) in listOf(360 to 640, 360 to 800, 412 to 840, 800 to 360, 600 to 960)) {
                for (font in listOf(1f, 1.6f, 2f)) add(arrayOf(width, height, font, false))
            }
            add(arrayOf(360, 800, 2f, true))
        }.filter { profile ->
            val selected = InstrumentationRegistry.getArguments().getString("homeDayProfile")
            selected == null || selected == "${profile[0]}-${profile[1]}-font${((profile[2] as Float) * 10).toInt()}"
        }
    }
}
