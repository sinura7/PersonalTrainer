package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.domain.HistoryCopy
import com.sinura.personaltrainer.domain.SetWork
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.PersonalTrainerTheme
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.Surface1
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A short cardio row has no thumbnail or multiline name to inflate its identity target. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h640dp-xhdpi", fontScale = 1f)
class HistorySessionRowTargetTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun shortNoArtworkIdentityAndMetricsAreSeparateCompleteTargetsForTheSameRecord() {
        val opened = mutableListOf<String>()
        compose.setContent {
            PersonalTrainerTheme {
                Box(Modifier.fillMaxSize().background(Pit)) {
                    SessionLogRow(
                        title = "Run", dateLabel = "2 January 2026", workingSets = 0,
                        work = SetWork.NONE, durationMinutes = 80,
                        onClick = { opened += "activity:original-id" }, unit = WeightUnit.KG,
                        modifier = Modifier.width(360.dp).background(Surface1),
                        reflowContent = true, durationLabel = HistoryCopy.activeDuration(80),
                    )
                }
            }
        }
        val directory = File("build/screen-renders/history-row-target/${UUID.randomUUID()}")
        check(directory.mkdirs())
        val bitmap = compose.runOnUiThread {
            val decor = compose.activity.window.decorView
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also { decor.draw(Canvas(it)) }
        }
        try {
            directory.resolve("short-cardio.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { bitmap.recycle() }
        directory.resolve("actual-targets.txt").writeText(
            listOf(SessionLogTags.ROW, SessionLogTags.METRICS).joinToString("\n") { tag ->
                val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
                "$tag size=${node.size} clip=${node.boundsInWindow} density=${node.layoutInfo.density} config=${node.config}"
            },
        )
        for (tag in listOf(SessionLogTags.ROW, SessionLogTags.METRICS)) {
            val action = compose.onNodeWithTag(tag).assertIsDisplayed()
            val node = action.fetchSemanticsNode()
            assertEquals(Role.Button, node.config[SemanticsProperties.Role])
            assertEquals(1f, node.layoutInfo.density.fontScale, 0f)
            assertTrue("$tag actual width is at least 48 dp", node.size.width / node.layoutInfo.density.density >= 48f)
            assertTrue("$tag actual height is at least 48 dp: ${node.size}", node.size.height / node.layoutInfo.density.density >= 48f)
            val spoken = node.config[SemanticsProperties.ContentDescription].single()
            assertTrue(spoken.startsWith("Run, 2 January 2026,"))
            assertTrue(spoken.endsWith("1 h 20 min"))
            action.performTouchInput { click(center) }
        }
        assertEquals(listOf("activity:original-id", "activity:original-id"), opened)
    }
}
