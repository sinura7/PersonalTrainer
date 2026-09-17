package com.sinura.personaltrainer.ui.workout

import android.os.Build
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.RestFloorContext
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.testutil.NativeArtifacts
import com.sinura.personaltrainer.ui.components.ScreenHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The shipping rest presentation, with an immutable clock and no background ticker. */
@RunWith(Parameterized::class)
class RestCompletionLayoutInstrumentedTest(
    private val width: Int, private val height: Int, private val font: Float, private val scenario: String,
) {
    @get:Rule val compose = createComposeRule()

    @Test fun clockAndActionsFitAndRemainDistinct() {
        var adjusted = 0
        var skips = 0
        var starts = 0
        var returns = 0
        val running = scenario == "running" || scenario == "denied"
        val complete = scenario == "complete"
        GoldenCapture.mountViewport(
            compose = compose, width = width.dp, height = height.dp, fontScale = font,
            reduceMotion = true, statusBar = 24.dp, navigationBar = 24.dp,
        ) {
            Scaffold(bottomBar = { Column {} }) { systemPadding ->
            Box(Modifier.padding(systemPadding).consumeWindowInsets(systemPadding)) {
            Scaffold(topBar = { ScreenHeader(title = "", onBack = { returns++ }, backTag = RestFloorTags.CLOSE, backDescription = "Close rest") }) { padding ->
                RestFloorBody(
                    rest = RestTimerUiState(remainingSeconds = 46, totalSeconds = 90, running = running,
                        completedTimerId = if (complete) "completed" else null),
                    floor = RestFloorContext(
                        exerciseName = "Barbell Back Squat",
                        lastSetLine = "Last set · 60 kg × 8",
                        sessionTargetLine = "Next set · 60 kg × 8 · RPE 8",
                    ),
                    onSkip = { skips++ }, onAdjust = { adjusted += it }, onSelectPreset = {},
                    onCustom = { true }, onStart = { starts++ }, onAcknowledgeBattery = {},
                    onBackToBar = { returns++ }, notificationsEnabled = scenario != "denied",
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }
            }
            }
        }
        val root = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode()
        val actionTag = if (running) RestFloorTags.SKIP else if (complete) RestFloorTags.BACK_TO_BAR else RestFloorTags.START
        val bounds = compose.onNodeWithTag(actionTag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(width.toFloat(), root.boundsInRoot.width / root.layoutInfo.density.density, 1f)
        assertEquals(height.toFloat(), root.boundsInRoot.height / root.layoutInfo.density.density, 1f)
        assertTrue(bounds.bottom <= root.boundsInRoot.bottom + 1)
        assertTrue(bounds.height / root.layoutInfo.density.density >= 47.5f)
        compose.onNodeWithTag(RestFloorTags.CLOCK).assertIsDisplayed()
        if (font >= 1.6f || height <= 640) assertTrue(compose.onAllNodesWithTag(RestFloorTags.RING).fetchSemanticsNodes().isEmpty())
        val name = "frontend-rest-${width}x$height-font${(font * 10).toInt()}-$scenario-api${Build.VERSION.SDK_INT}"
        val image = GoldenCapture.capture(compose)
        if (Build.VERSION.SDK_INT == 29) GoldenImageAssert.assertMatches(name, image)
        else NativeArtifacts.write(name, image.asAndroidBitmap())
        if (running) {
            compose.onNodeWithTag(RestFloorTags.MINUS).assertIsDisplayed().performClick()
            assertEquals(-15, adjusted)
            compose.onNodeWithTag(RestFloorTags.PLUS).assertIsDisplayed().performClick()
            assertEquals(0, adjusted)
        }
        compose.onNodeWithTag(actionTag).performClick()
        assertEquals(if (running) 1 else 0, skips)
        assertEquals(if (!running && !complete) 1 else 0, starts)
        assertEquals(if (complete) 1 else 0, returns)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}-font{2}-{3}")
        fun profiles(): List<Array<Any>> = buildList {
            for ((w, h) in listOf(360 to 640, 360 to 800, 412 to 840, 600 to 840, 640 to 360)) {
                for (scale in listOf(1f, 1.6f, 2f)) add(arrayOf(w, h, scale, "running"))
            }
            for (scenario in listOf("idle", "complete", "denied")) {
                add(arrayOf(360, 800, 1f, scenario))
                add(arrayOf(360, 640, 2f, scenario))
            }
        }
    }
}
