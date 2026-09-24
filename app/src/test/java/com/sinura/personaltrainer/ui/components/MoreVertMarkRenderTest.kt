package com.sinura.personaltrainer.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.theme.TextDisabled
import com.sinura.personaltrainer.ui.theme.TextSecondary
import com.sinura.personaltrainer.ui.theme.TextTertiary
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app's own ⋮, [OutlinedMarks.MoreVert], draws Material's `Icons.Outlined.MoreVert` pixel
 * for pixel, at every size and tint the five overflow buttons that swapped to it in W2a use: the
 * icon's own 24 dp in TextSecondary, TextTertiary and TextDisabled, and the live session bar's
 * 20 dp. The swap takes a stock icon off the design-token count and changes nothing drawn.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class MoreVertMarkRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Case(val size: Dp?, val tint: Color)

    private val cases = listOf(
        Case(size = null, tint = TextSecondary),
        Case(size = null, tint = TextTertiary),
        Case(size = null, tint = TextDisabled),
        Case(size = 20.dp, tint = TextSecondary),
    )

    @Test
    fun theAppsOwnOverflowMarkDrawsMaterialsPixelForPixel() {
        compose.setContent {
            Column(modifier = Modifier.background(Pit)) {
                cases.forEachIndexed { index, case ->
                    val sized = if (case.size != null) Modifier.size(case.size) else Modifier
                    Row {
                        Icon(Icons.Outlined.MoreVert, contentDescription = null, tint = case.tint, modifier = sized.testTag("stock-$index"))
                        Icon(OutlinedMarks.MoreVert, contentDescription = null, tint = case.tint, modifier = sized.testTag("own-$index"))
                    }
                }
            }
        }
        val frame = drawWindow()
        cases.forEachIndexed { index, case ->
            val stock = pixelsOf(frame, "stock-$index")
            val own = pixelsOf(frame, "own-$index")
            assertTrue("case $index draws the mark at all", stock.distinct().size > 2)
            assertTrue("case $index draws it in its tint", stock.any { it == case.tint.toArgb() })
            assertEquals("case $index is the same size", stock.size, own.size)
            assertArrayEquals("case $index is the same mark, pixel for pixel", stock, own)
        }
    }

    private fun pixelsOf(frame: Bitmap, tag: String): IntArray {
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val left = bounds.left.toInt()
        val top = bounds.top.toInt()
        val width = bounds.width.toInt()
        val height = bounds.height.toInt()
        val out = IntArray(width * height)
        frame.getPixels(out, 0, width, left, top, width, height)
        return out
    }

    private fun drawWindow(): Bitmap = compose.runOnIdle {
        val decor = compose.activity.window.decorView
        val frame = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(frame))
        frame
    }
}
