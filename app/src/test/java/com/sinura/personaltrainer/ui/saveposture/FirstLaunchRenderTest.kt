package com.sinura.personaltrainer.ui.saveposture

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.MastheadCopy
import com.sinura.personaltrainer.ui.home.HomeMasthead
import com.sinura.personaltrainer.ui.home.HomeTags
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the first-launch chooser and Home's date-aware masthead (audit Q1) on the JVM and
 * writes the frames to `app/build/screen-renders/q1/` for review (ADR-026 §8 render lane).
 *
 * Each frame also asserts the part that must stay reachable: every chooser button at every
 * size and text scale, and the route back to today.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w800dp-h915dp-xhdpi")
class FirstLaunchRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersTheChooserAtEveryPhoneSizeAndTextScale() {
        SIZES.forEach { (width, height) ->
            SCALES.forEach { scale ->
                render(
                    name = "chooser-${width}x$height-font$scale",
                    widthDp = width,
                    heightDp = height,
                    fontScale = scale,
                ) {
                    SavePostureChooser(
                        onChooseAccount = {},
                        onChooseLocal = {},
                        onSetUpDrive = {},
                        onBack = {},
                        syncPaused = true,
                    )
                }
                listOf(SavePostureTags.ACCOUNT, SavePostureTags.LOCAL, SavePostureTags.DRIVE).forEach { tag ->
                    compose.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                }
                capture(name = "chooser-${width}x$height-font$scale-end", widthDp = width, heightDp = height)
            }
        }
    }

    @Test
    fun rendersTheMastheadOnAnotherDay() {
        SCALES.forEach { scale ->
            render(name = "masthead-past-360-font$scale", widthDp = 360, heightDp = 200, fontScale = scale) {
                HomeMasthead(
                    epochDay = TUESDAY,
                    headline = MastheadCopy.TRAINING_COMPLETE,
                    onBackToToday = {},
                )
            }
            compose.onNodeWithTag(HomeTags.BACK_TO_TODAY).assertExists()
        }
    }

    private fun render(
        name: String,
        widthDp: Int,
        heightDp: Int,
        fontScale: Float,
        content: @Composable () -> Unit,
    ) {
        compose.activity.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density = base.density, fontScale = fontScale)) {
                PersonalTrainerTheme {
                    // Keyed so each frame starts fresh: otherwise the chooser keeps the previous
                    // frame's scroll position.
                    key(name) {
                        Box(modifier = Modifier.width(widthDp.dp).height(heightDp.dp).background(Pit)) { content() }
                    }
                }
            }
        }
        compose.waitForIdle()
        capture(name, widthDp, heightDp)
    }

    private fun capture(name: String, widthDp: Int, heightDp: Int) {
        // Robolectric never delivers the draw callback captureToImage waits on; draw the
        // window ourselves, then crop to the frame.
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val full = Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            decor.draw(Canvas(full))
            val px = decor.resources.displayMetrics.density
            Bitmap.createBitmap(
                full,
                0,
                0,
                (widthDp * px).toInt().coerceAtMost(full.width),
                (heightDp * px).toInt().coerceAtMost(full.height),
            )
        }
        val out = File("build/screen-renders/q1").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0 && bitmap.height > 0)
    }

    private companion object {
        val SIZES = listOf(360 to 640, 412 to 915, 800 to 360)
        val SCALES = listOf(1f, 1.6f, 2f)

        // Tuesday 15 September 2026.
        const val TUESDAY = 20_711L
    }
}
