package com.sinura.personaltrainer.ui.preview

import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sinura.personaltrainer.domain.GoldenPageCatalog
import com.sinura.personaltrainer.testutil.GoldenCapture
import com.sinura.personaltrainer.testutil.GoldenImageAssert
import com.sinura.personaltrainer.ui.workout.WorkoutFrozenFrame
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frozen shipping-component integration frames, replacing the old live-clock
 * captures. Whole-route wiring and Room behavior have separate native checks.
 * No real workout data is read, written, or discarded by this suite.
 */
@RunWith(AndroidJUnit4::class)
class FloorGoldenTest {
    @get:Rule val compose = createComposeRule()
    @Before fun apiProfile() { assumeTrue(Build.VERSION.SDK_INT == 29) }
    @Test fun recordsWorking() = record("working")
    @Test fun recordsWarmup() = record("warmup")
    @Test fun recordsRest() = record("rest")
    @Test fun recordsHold() = record("hold")
    @Test fun recordsSuccess() = record("success")
    @Test fun recordsError() = record("error")
    @Test fun recordsCompletion() = record("completion")
    @Test fun recordsFont20() = record("font20")
    @Test fun recordsReducedMotion() = record("reduced-motion")

    private fun record(state: String) {
        GoldenCapture.mountViewport(
            compose = compose, width = 360.dp, height = 800.dp,
            fontScale = if (state == "font20") 2f else 1f,
            reduceMotion = true, statusBar = 24.dp, navigationBar = 24.dp,
        ) { WorkoutFrozenFrame(scenario = state) }
        SystemClock.sleep(750)
        val root = compose.onNodeWithTag(GoldenCapture.DefaultTag).fetchSemanticsNode()
        assertEquals(360f, root.boundsInRoot.width / root.layoutInfo.density.density, 1f)
        assertEquals(800f, root.boundsInRoot.height / root.layoutInfo.density.density, 1f)
        GoldenImageAssert.assertMatches(GoldenPageCatalog.floorAssetName(state), GoldenCapture.capture(compose))
    }
}
