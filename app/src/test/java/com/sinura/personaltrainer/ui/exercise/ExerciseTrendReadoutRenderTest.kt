package com.sinura.personaltrainer.ui.exercise

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real native text and modal measurement, distinct from physical phone acceptance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class ExerciseTrendReadoutRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val frames = File("build/screen-renders/summary-history-trends/${UUID.randomUUID()}")
    private val first = LocalDate.of(2026, 8, 3).toEpochDay()

    @Test @Config(fontScale = 1f) fun small10() = journey("small10")
    @Test @Config(fontScale = 1.6f) fun small16() = journey("small16")
    @Test @Config(fontScale = 2f) fun small20() = journey("small20")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1f) fun phone10() = journey("phone10")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 1.6f) fun phone16() = journey("phone16")
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi", fontScale = 2f) fun phone20() = journey("phone20")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1f) fun landscape10() = journey("landscape10")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 1.6f) fun landscape16() = journey("landscape16")
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi", fontScale = 2f) fun landscape20() = journey("landscape20")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1f) fun adaptive10() = journey("adaptive10")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 1.6f) fun adaptive16() = journey("adaptive16")
    @Test @Config(qualifiers = "w600dp-h960dp-xhdpi", fontScale = 2f) fun adaptive20() = journey("adaptive20")
    @Test @Config(fontScale = 2f) fun rtlReducedMotion() = journey("rtl20", rtl = true)

    @Test fun zeroAndSingleResultsHaveLiteralReadoutsWithoutInventedChartSemantics() {
        compose.setContent {
            PersonalTrainerTheme {
                LazyColumn(modifier = Modifier.fillMaxSize().testTag(LIST), contentPadding = PaddingValues(Metrics.gutter)) {
                    item { ExerciseTrendCard(title = "Estimated 1RM", values = emptyList(), epochDays = emptyList(),
                        pointNoun = "session", hint = "Log a loaded set to estimate a 1RM.", measure = "kg",
                        line = true, grouped = false, tag = EMPTY) }
                    item { ExerciseTrendCard(title = "Weekly reps", values = listOf(12.0), epochDays = listOf(first),
                        pointNoun = "week", hint = "One more training week unlocks your trend.", measure = "reps",
                        line = false, grouped = true, tag = TAG) }
                }
            }
        }
        compose.onNodeWithText("No recorded estimate").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasAnyAncestor(hasTestTag(EMPTY))).fetchSemanticsNodes()
            .none { SemanticsProperties.ContentDescription in it.config })
        scroll("$TAG-values")
        compose.onNodeWithText("12 reps").assertIsDisplayed()
        capture("single")
        compose.onNodeWithTag("$TAG-values").performClick()
        await("$TAG-close")
        compose.onNode(hasText("12 reps") and hasAnyAncestor(hasTestTag("$TAG-data"))).assertIsDisplayed()
        compose.onNode(hasText("3 August 2026 – 9 August 2026") and
            hasAnyAncestor(hasTestTag("$TAG-data"))).assertIsDisplayed()
        compose.onNodeWithTag("$TAG-close").performClick()
    }

    private fun journey(profile: String, rtl: Boolean = false) {
        // The wide value plus delta must remain whole at large text; exact dates include years.
        val weeks = (0 until 20).map { first + it * 7L }
        val values = (0 until 20).map { if (it == 0) 0.0 else 10_000.0 + it * 100.0 }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                PersonalTrainerTheme(reduceMotion = rtl) {
                    LazyColumn(modifier = Modifier.fillMaxSize().background(Pit).testTag(LIST),
                        contentPadding = PaddingValues(Metrics.gutter)) {
                        item { ExerciseTrendCard(title = "Weekly reps", values = values, epochDays = weeks,
                            pointNoun = "week", hint = "One more training week unlocks your trend.", measure = "reps",
                            line = false, grouped = true, tag = TAG, currentWeek = true) }
                    }
                }
            }
        }
        scroll("$TAG-values")
        capture("$profile-card")
        wholeTexts(TAG)
        compose.onNodeWithText("Current week so far").assertIsDisplayed()
        val chart = compose.onAllNodes(hasAnyAncestor(hasTestTag(TAG)), useUnmergedTree = true)
            .fetchSemanticsNodes().single { SemanticsProperties.ContentDescription in it.config }
        assertTrue(chart.config[SemanticsProperties.ContentDescription].single().contains("latest 11,900 reps"))
        assertFalse(chart.config[SemanticsProperties.ContentDescription].single().contains("kg"))
        compose.onNodeWithTag("$TAG-values").assertIsDisplayed().performClick()
        await("$TAG-close")
        compose.onNodeWithTag("$TAG-close").assertIsDisplayed()
        compose.onNodeWithTag("$TAG-data-list").performScrollToNode(hasTestTag("$TAG-point-19"))
        wholeTexts("$TAG-point-19")
        capture("$profile-values")
        compose.onNodeWithTag("$TAG-data-list").performScrollToNode(hasTestTag("$TAG-point-0"))
        compose.onNodeWithText("0 reps").assertIsDisplayed()
        wholeTexts("$TAG-point-0")
        compose.onNodeWithTag("$TAG-close").assertIsDisplayed().performClick()
        compose.onNodeWithTag("$TAG-data").assertDoesNotExist()
    }

    private fun scroll(tag: String) = compose.onNodeWithTag(LIST).performScrollToNode(hasTestTag(tag))
    private fun await(tag: String) = compose.waitUntil(20_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }
    private fun wholeTexts(tag: String) {
        compose.onAllNodes(hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { SemanticsProperties.Text in it.config }.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
                val layout = layouts.single()
                val text = layout.layoutInput.text.text
                for (line in 0 until layout.lineCount) assertFalse("Ellipsized $text", layout.isLineEllipsized(line))
                // Centered Text can measure its semantics box to the longest line
                // while retaining a paragraph laid out in the wider parent constraint.
                // Glyph boxes belong to MultiParagraph, whose width can exceed the final
                // intrinsic TextLayoutResult.size. Check its bounds AND the ink
                // span against the measured text box instead of mixing coordinates.
                for (line in 0 until layout.lineCount) {
                    assertTrue("Line wider than text box: $text",
                        layout.getLineRight(line) - layout.getLineLeft(line) <= node.size.width + 2f)
                }
                text.indices.filter { !text[it].isWhitespace() }.forEach { index ->
                    val glyph = layout.getBoundingBox(index)
                    assertTrue("Clipped glyph $index in $text: $glyph / ${node.size} / paragraph ${layout.multiParagraph.width}",
                        glyph.left >= -1f && glyph.right <= layout.multiParagraph.width + 1f &&
                            glyph.top >= -1f && glyph.bottom <= node.size.height + 1f)
                }
            }
    }
    private fun capture(name: String) {
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                .also { bitmap ->
                    val canvas = Canvas(bitmap)
                    decor.draw(canvas)
                    for (window in WindowInspector.getGlobalWindowViews().filter { it.isShown && it !== decor }) {
                        val location = IntArray(2)
                        window.getLocationOnScreen(location)
                        val saved = canvas.save()
                        canvas.translate(location[0].toFloat(), location[1].toFloat())
                        window.draw(canvas)
                        canvas.restoreToCount(saved)
                    }
                }
        }
        frames.mkdirs()
        FileOutputStream(File(frames, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private companion object {
        const val LIST = "trend-test-list"
        const val TAG = "trend-test-weekly"
        const val EMPTY = "trend-test-empty"
    }
}
