package com.sinura.personaltrainer.ui.progress

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.BodyHeatCopy
import com.sinura.personaltrainer.domain.BodyHeatSnapshot
import com.sinura.personaltrainer.domain.CanonicalMuscle
import com.sinura.personaltrainer.domain.HeatWindow
import com.sinura.personaltrainer.domain.MuscleLoadSummary
import com.sinura.personaltrainer.ui.components.BodyView
import com.sinura.personaltrainer.ui.components.GymSectionHeader
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
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
 * P11 native frames: the figure, the no-colour line, and the first muscle row.
 * Written under app/build/screen-renders/p11/ (ADR-032 JVM render lane).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class BodyGeometryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun frontFigureShowsTheNoColourLineAtFont1AndFont2() {
        listOf(1f, 2f).forEach { scale ->
            render("body-front-360-font$scale", fontScale = scale, view = BodyView.FRONT)
            compose.onNodeWithText(BodyHeatCopy.WINDOW_CAPTION).assertIsDisplayed()
            compose.onNodeWithTag(BodyTags.VIEW_FRONT).assertIsDisplayed()
            compose.onNodeWithTag(BodyTags.muscle(CanonicalMuscle.CHEST)).assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun firstMuscleRowSitsAboveTheTabBarAt360x640() {
        compose.activity.setContent {
            PersonalTrainerTheme {
                Column(Modifier.width(360.dp).background(Pit)) {
                    BodyWindowPicker(window = HeatWindow.CURRENT_WEEK, onSelectWindow = {})
                    Column(
                        Modifier.padding(
                            start = Metrics.gutter,
                            end = Metrics.gutter,
                            top = Metrics.space2,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Metrics.cardGap),
                    ) {
                        BodyMapCard(
                            snapshot = SNAPSHOT,
                            view = BodyView.FRONT,
                            onViewChange = {},
                            selected = CanonicalMuscle.CHEST,
                            onSelect = {},
                            facts = "1 session this week · last finished yesterday",
                        )
                        GymSectionHeader("Muscles")
                        MuscleHeatRow(
                            load = CHEST,
                            window = HeatWindow.CURRENT_WEEK,
                            selected = true,
                            onClick = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
        val bounds = compose.onNodeWithTag(BodyTags.muscle(CanonicalMuscle.CHEST))
            .fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        val bottomDp = bounds.bottom / density
        assertTrue("first muscle row bottom ${bottomDp}dp exceeds 576", bottomDp <= 576f)
        val caption = compose.onNodeWithTag(BodyTags.WINDOW_CAPTION).fetchSemanticsNode().boundsInRoot
        val captionHeightDp = caption.height / density
        assertTrue("caption is taller than two lines at font 1.0: ${captionHeightDp}dp", captionHeightDp <= 36f)
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val full = Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            decor.draw(Canvas(full))
            full
        }
        val out = File("build/screen-renders/p11").apply { mkdirs() }
        FileOutputStream(File(out, "body-360x640-font1.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun backFigureUsesTheSameCaption() {
        render("body-back-360-font1", fontScale = 1f, view = BodyView.BACK)
        compose.onNodeWithTag(BodyTags.VIEW_BACK).assertIsDisplayed()
        compose.onNodeWithText(BodyHeatCopy.WINDOW_CAPTION).assertIsDisplayed()
    }

    private fun render(name: String, fontScale: Float, view: BodyView) {
        compose.activity.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = fontScale)) {
                PersonalTrainerTheme {
                    Box(
                        Modifier
                            .width(360.dp)
                            .height(800.dp)
                            .background(Pit),
                    ) {
                        Column {
                            BodyMapCard(
                                snapshot = SNAPSHOT,
                                view = view,
                                onViewChange = {},
                                selected = CanonicalMuscle.CHEST,
                                onSelect = {},
                                facts = "1 session this week · last finished yesterday",
                            )
                            MuscleHeatRow(
                                load = CHEST,
                                window = HeatWindow.CURRENT_WEEK,
                                selected = true,
                                onClick = {},
                            )
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.runOnIdle {
            val decor = compose.activity.window.decorView
            val full = Bitmap.createBitmap(
                decor.width.coerceAtLeast(1),
                decor.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
            decor.draw(Canvas(full))
            full
        }
        val out = File("build/screen-renders/p11").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(bitmap.width > 0)
    }

    private companion object {
        val CHEST = MuscleLoadSummary(
            muscle = CanonicalMuscle.CHEST,
            volumeKg = 1200.0,
            workingSets = 4,
            sessionCount = 1,
            lastTrainedAtMs = 1L,
            daysSinceLastTrained = 1,
            weeklySets = 4.0,
            heat = 0.4,
            exercises = emptyList(),
        )
        val SNAPSHOT = BodyHeatSnapshot(
            window = HeatWindow.CURRENT_WEEK,
            windowStartMs = 1L,
            generatedAtMs = 2L,
            loads = listOf(CHEST),
            hasAnyWorkingSets = true,
            hasWindowWorkingSets = true,
            windowSessions = 1,
            daysSinceLastFinished = 1,
        )
    }
}
