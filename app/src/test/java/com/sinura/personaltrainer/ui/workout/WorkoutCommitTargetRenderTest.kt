package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.sinura.personaltrainer.ui.theme.Pit
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Measures the REAL Compose WorkoutDock, PrimaryGymButton, fonts and theme.
 * The acceptance number is the independently specified ADR-027/owner-roadmap
 * 72 dp contract; it deliberately does not read any mutable Metrics token.
 *
 * This is a component render/semantics test, not the complete workout or
 * physical-phone touch evidence. Preserve red-run TSVs and PNGs before a fix.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi")
class WorkoutCommitTargetRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val font = mutableFloatStateOf(1f)
    private var state by mutableStateOf(scenarios().first().state)

    @Test fun portrait360_font10() = measure("360x640-font10", 1f)
    @Test fun portrait360_font16() = measure("360x640-font16", 1.6f)
    @Test fun portrait360_font20() = measure("360x640-font20", 2f)

    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font10() = measure("412x840-font10", 1f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font16() = measure("412x840-font16", 1.6f)
    @Test @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun portrait412_font20() = measure("412x840-font20", 2f)

    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font10() = measure("640x360-land-font10", 1f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font16() = measure("640x360-land-font16", 1.6f)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xhdpi")
    fun landscape_font20() = measure("640x360-land-font20", 2f)

    private fun measure(profile: String, fontScale: Float) {
        font.floatValue = fontScale
        compose.showFloor(fontScale = font) {
            Box(Modifier.fillMaxSize().background(Pit).testTag(FRAME)) {
                Box(Modifier.align(Alignment.BottomCenter)) {
                    WorkoutDock(state = state, events = floorDockEvents())
                }
            }
        }
        val directory = File("build/screen-renders/workout-commit72-investigation/$profile")
        assertTrue("Cannot create evidence directory $directory", directory.exists() || directory.mkdirs())
        val observations = mutableListOf<Observation>()
        for (scenario in scenarios()) {
            compose.runOnIdle { state = scenario.state }
            compose.waitForIdle()
            // Save the picture before any contract assertion, so a regression leaves evidence.
            capture(File(directory, "${scenario.name}.png"))
            val node = compose.onNodeWithTag(scenario.tag)
            val semantics = node.fetchSemanticsNode()
            val frame = compose.onNodeWithTag(FRAME).fetchSemanticsNode()
            val density = semantics.layoutInfo.density.density
            val bounds = semantics.boundsInRoot
            val frameBounds = frame.boundsInRoot
            val visibleHeightDp = bounds.height / density
            val layoutHeightDp = semantics.size.height / density
            val fullyInside = bounds.top >= frameBounds.top - 0.5f &&
                bounds.bottom <= frameBounds.bottom + 0.5f &&
                bounds.left >= frameBounds.left - 0.5f &&
                bounds.right <= frameBounds.right + 0.5f
            observations += Observation(
                scenario.name, visibleHeightDp, layoutHeightDp, bounds.width / density,
                frameBounds.width / density, frameBounds.height / density, fullyInside,
                semantics.config.getOrNull(SemanticsProperties.Disabled) == null,
            )
            node.assertIsDisplayed()
            if (scenario.state.primaryAction.enabled) node.assertIsEnabled() else node.assertIsNotEnabled()
        }
        File(directory, "measurements.tsv").writeText(
            "scenario\tvisible_height_dp\tlayout_height_dp\twidth_dp\tframe_width_dp\tframe_height_dp\tfully_inside\tenabled\n" +
                observations.joinToString(separator = "\n", postfix = "\n") { it.tsv() },
            Charsets.UTF_8,
        )
        val failures = observations.filter {
            it.visibleHeightDp + 0.01f < REQUIRED_HEIGHT_DP ||
                it.layoutHeightDp + 0.01f < REQUIRED_HEIGHT_DP || !it.fullyInside
        }
        assertTrue(
            "ADR-027 workout commit must render at least 72 dp and remain fully reachable; $profile: $failures",
            failures.isEmpty(),
        )
    }

    private fun capture(file: File) {
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                decor.draw(Canvas(it))
            }
        }
        try {
            FileOutputStream(file).use { assertTrue("PNG write failed: $file", bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    private data class Scenario(val name: String, val tag: String, val state: WorkoutDockState)
    private data class Observation(
        val name: String, val visibleHeightDp: Float, val layoutHeightDp: Float, val widthDp: Float,
        val frameWidthDp: Float, val frameHeightDp: Float, val fullyInside: Boolean, val enabled: Boolean,
    ) {
        fun tsv(): String = listOf(name, visibleHeightDp, layoutHeightDp, widthDp, frameWidthDp, frameHeightDp, fullyInside, enabled).joinToString("\t")
    }

    private companion object {
        const val FRAME = "workout-commit72-investigation-frame"
        const val REQUIRED_HEIGHT_DP = 72f

        fun scenarios(): List<Scenario> {
            val draft = ActiveExerciseDraft(weightKg = FLOOR_KG70, reps = 10, rpe = 8)
            val log = floorPrimaryAction(WorkoutPrimaryKind.LOG_SET, draft)
            val effortMissing = floorPrimaryAction(WorkoutPrimaryKind.LOG_SET, draft.copy(rpe = null), enabled = false)
            val saving = floorPrimaryAction(WorkoutPrimaryKind.SAVING, draft, enabled = false)
            val next = floorPrimaryAction(WorkoutPrimaryKind.NEXT_EXERCISE, nextName = "Leg Curl")
            val finish = floorPrimaryAction(WorkoutPrimaryKind.FINISH)
            return listOf(
                Scenario("ready", WorkoutTestTags.LOG_SET, floorDockState(log)),
                Scenario("effort-missing", WorkoutTestTags.LOG_SET, floorDockState(effortMissing).copy(effortMissingForCommit = true)),
                Scenario("saving", WorkoutTestTags.LOG_SET, floorDockState(saving).copy(logging = true, savePending = true)),
                // Production landscape deliberately draws only the short next verb, speaking the name.
                Scenario("next-one-line", WorkoutTestTags.NEXT, floorDockState(next, payload = null, spokenPayload = "Leg Curl")),
                Scenario("finish-one-line", WorkoutTestTags.DOCK_FINISH, floorDockState(finish)),
            )
        }
    }
}
