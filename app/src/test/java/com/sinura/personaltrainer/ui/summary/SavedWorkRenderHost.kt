package com.sinura.personaltrainer.ui.summary

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect as SavedWorkRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.sinura.personaltrainer.ui.theme.Pit
import com.sinura.personaltrainer.ui.workout.awaitThat
import com.sinura.personaltrainer.ui.workout.count
import com.sinura.personaltrainer.ui.workout.isNear
import com.sinura.personaltrainer.ui.workout.settle
import com.sinura.personaltrainer.ui.workout.silentSquaresUnder
import com.sinura.personaltrainer.ui.workout.textLayout
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.robolectric.shadows.ShadowDialog

/** Actual native windows, text ink and bounded scrolls shared only by saved-work receipts. */
internal class SavedWorkRenderHost(
    private val compose: AndroidComposeTestRule<*, out ComponentActivity>,
    private val contentTag: () -> String?,
    private val facts: () -> String,
) {
    private val runId = UUID.randomUUID().toString()
    var profile = "saved-work"
    var font = 1f

    fun evidence(name: String, block: () -> Unit) {
        profile = name
        try { block() } catch (failure: Throwable) {
            runCatching { capture("failed", failure.stackTraceToString()) }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    fun tag(value: String, unmerged: Boolean = false): SemanticsNodeInteraction =
        find(hasTestTag(value), value, unmerged)

    fun words(value: String, under: String? = null): SemanticsNodeInteraction =
        find(hasText(value) and if (under == null) SemanticsMatcher("any parent") { true }
            else hasAnyAncestor(hasTestTag(under)), value, unmerged = true)

    private fun find(matcher: SemanticsMatcher, description: String, unmerged: Boolean): SemanticsNodeInteraction {
        drain()
        val target = compose.onNode(matcher, useUnmergedTree = unmerged)
        fun present() = compose.onAllNodes(matcher, useUnmergedTree = unmerged).fetchSemanticsNodes().isNotEmpty()
        if (present()) return target
        val listTag = checkNotNull(contentTag()) { "$profile has no scroll surface for $description" }
        val list = compose.onNode(hasTestTag(listTag)).fetchSemanticsNode()
        list.config.getOrNull(SemanticsActions.ScrollToIndex)?.action?.let { action ->
            compose.runOnUiThread { action(0) }
            drain()
        }
        repeat(30) {
            if (present()) return target
            val current = compose.onNode(hasTestTag(listTag)).fetchSemanticsNode()
            val range = checkNotNull(current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange))
            assertTrue("$description appears before the actual content end", range.value() < range.maxValue())
            val viewport = current.boundsInWindow.intersect(window())
            assertTrue("actual chrome leaves a usable viewport for $description", viewport.height > 1f)
            compose.runOnUiThread { checkNotNull(current.config[SemanticsActions.ScrollBy].action)(0f, viewport.height * .75f) }
            drain()
        }
        throw AssertionError("$profile cannot discover $description after 30 bounded real scrolls")
    }

    fun reach(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        repeat(20) {
            val node = interaction.fetchSemanticsNode()
            val full = full(node)
            val clipped = node.boundsInWindow.intersect(window())
            if (clipped.width >= node.size.width - 1f && clipped.height >= node.size.height - 1f) {
                return interaction.assertIsDisplayed()
            }
            var ancestor = node.parent
            var moved = false
            while (ancestor != null && !moved) {
                val current = ancestor
                val action = current.config.getOrNull(SemanticsActions.ScrollBy)?.action
                val viewport = current.boundsInWindow.intersect(window())
                if (action != null && viewport.width > 1f && viewport.height > 1f) {
                    for ((horizontal, range) in listOf(
                        true to current.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange),
                        false to current.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange),
                    )) {
                        if (range == null) continue
                        val low = if (horizontal) full.left else full.top
                        val high = if (horizontal) full.right else full.bottom
                        val start = if (horizontal) viewport.left else viewport.top
                        val end = if (horizontal) viewport.right else viewport.bottom
                        val delta = when { low < start - 1f -> low - start; high > end + 1f -> high - end; else -> 0f }
                        val reverse = range.reverseScrolling xor (horizontal && node.layoutInfo.layoutDirection == LayoutDirection.Rtl)
                        val amount = if (reverse) -delta else delta
                        if (amount < -1f && range.value() > 0f || amount > 1f && range.value() < range.maxValue()) {
                            moved = compose.runOnUiThread { action(if (horizontal) amount else 0f, if (horizontal) 0f else amount) }
                            if (moved) { drain(); break }
                        }
                    }
                }
                ancestor = current.parent
            }
            if (!moved) throw AssertionError("$profile cannot fully reveal $full clipped to $clipped: ${node.config}")
        }
        throw AssertionError("$profile real scroll ancestors cannot fully reveal the target")
    }

    fun action(interaction: SemanticsNodeInteraction): SemanticsNodeInteraction {
        reach(interaction).assertIsEnabled()
        val node = interaction.fetchSemanticsNode()
        val density = node.layoutInfo.density
        assertEquals("actual font scale reaches the action", font, density.fontScale, .001f)
        assertTrue("whole target width is at least 48 dp", node.size.width / density.density >= 47.5f)
        assertTrue("whole target height is at least 48 dp", node.size.height / density.density >= 47.5f)
        assertTrue("the action offers a click", node.config.getOrNull(SemanticsActions.OnClick)?.action != null)
        return interaction
    }

    fun touch(interaction: SemanticsNodeInteraction) {
        action(interaction).performTouchInput { click(center) }
        drain()
    }

    fun readable(interaction: SemanticsNodeInteraction, expected: String) {
        reach(interaction)
        val node = interaction.fetchSemanticsNode()
        assertTrue("$expected has an actual measured text area", node.size.width > 0 && node.size.height > 0)
        val reported = interaction.textLayout()
        val input = reported.layoutInput
        fun colorAt(offset: Int): Color {
            var color = input.style.color
            input.text.spanStyles.forEach { span ->
                if (offset >= span.start && offset < span.end && span.item.color != Color.Unspecified) {
                    color = span.item.color
                }
            }
            return color
        }
        // Compose 1.11 can rebuild Text semantics at the parent width. Re-measure its
        // actual fonts/input at its measured node width, retaining every reported line.
        val measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0).measure(
            text = input.text, style = input.style, overflow = input.overflow,
            softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
            constraints = input.constraints.copy(minWidth = 0, maxWidth = node.size.width),
        )
        assertEquals("actual rendered words", expected, measured.layoutInput.text.text)
        assertEquals("actual-width paragraph keeps the reported line count", reported.lineCount, measured.lineCount)
        assertEquals("last character is laid out", expected.length, measured.getLineEnd(measured.lineCount - 1, visibleEnd = true))
        val frame = draw()
        try {
            val visible = node.boundsInWindow.intersect(window())
            val origin = node.positionInWindow
            assertTrue("whole text height fits its actual clip: $expected", measured.size.height <= visible.height + 1f)
            repeat(measured.lineCount) { line ->
                assertEquals("reported line start", reported.getLineStart(line), measured.getLineStart(line))
                assertEquals("reported line end", reported.getLineEnd(line), measured.getLineEnd(line))
                assertFalse("$expected has no ellipsis on line $line", measured.isLineEllipsized(line))
                val glyphs = SavedWorkRect(origin.x + measured.getLineLeft(line), origin.y + measured.getLineTop(line),
                    origin.x + measured.getLineRight(line), origin.y + measured.getLineBottom(line))
                assertTrue("full line fits actual clip: $expected / $glyphs / $visible",
                    glyphs.left >= visible.left - 1f && glyphs.right <= visible.right + 1f &&
                        glyphs.top >= visible.top - 1f && glyphs.bottom <= visible.bottom + 1f)
                val colors = (measured.getLineStart(line) until measured.getLineEnd(line, visibleEnd = true))
                    .filter { !expected[it].isWhitespace() }.map(::colorAt).distinct()
                assertTrue("native text ink exists in each actual span color on every line: $expected",
                    colors.isNotEmpty() && colors.all { color -> frame.count(glyphs.intersect(visible), color) > 0 })
            }
            val final = measured.getBoundingBox(expected.lastIndex)
            val finalGlyph = SavedWorkRect(origin.x + final.left, origin.y + final.top, origin.x + final.right, origin.y + final.bottom)
            // An annotated unit can have a smaller font and a different color from the
            // numeral. Its actual glyph box and resolved span color remain mandatory.
            assertTrue("native final glyph is visible: $expected", frame.count(finalGlyph.intersect(visible), colorAt(expected.lastIndex)) > 0)
        } finally { frame.recycle() }
    }

    /** Mathematical operand order is visual even when its enclosing card flows RTL. */
    fun numericOrder(interaction: SemanticsNodeInteraction, expected: String, orderedOffsets: List<Int>) {
        readable(interaction, expected)
        val node = interaction.fetchSemanticsNode()
        val input = interaction.textLayout().layoutInput
        val measured = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection, cacheSize = 0).measure(
            text = input.text, style = input.style, overflow = input.overflow,
            softWrap = input.softWrap, maxLines = input.maxLines, placeholders = input.placeholders,
            constraints = input.constraints.copy(minWidth = 0, maxWidth = node.size.width),
        )
        assertTrue("numeric visual-order probe includes both operands", orderedOffsets.size >= 2)
        assertTrue("numeric visual-order probe names actual characters", orderedOffsets.all { it in expected.indices })
        assertTrue("first and last probed characters are numbers",
            expected[orderedOffsets.first()].isDigit() && expected[orderedOffsets.last()].isDigit())
        val line = measured.getLineForOffset(orderedOffsets.first())
        assertTrue("planned mathematical operands share their actual line",
            orderedOffsets.all { measured.getLineForOffset(it) == line })
        val origin = node.positionInWindow
        val glyphs = orderedOffsets.map { offset ->
            val glyph = measured.getBoundingBox(offset)
            SavedWorkRect(origin.x + glyph.left, origin.y + glyph.top, origin.x + glyph.right, origin.y + glyph.bottom)
        }
        val frame = draw()
        try {
            val visible = node.boundsInWindow.intersect(window())
            glyphs.forEachIndexed { index, glyph ->
                assertTrue("ordered mathematical glyph fits the actual clip: $expected / $glyph / $visible",
                    glyph.left >= visible.left - 1f && glyph.right <= visible.right + 1f &&
                        glyph.top >= visible.top - 1f && glyph.bottom <= visible.bottom + 1f)
                var color = input.style.color
                input.text.spanStyles.forEach { span ->
                    val offset = orderedOffsets[index]
                    if (offset >= span.start && offset < span.end && span.item.color != Color.Unspecified) color = span.item.color
                }
                assertTrue("native ink occupies each ordered mathematical glyph: ${expected[orderedOffsets[index]]}",
                    frame.count(glyph.intersect(visible), color) > 0)
            }
        } finally { frame.recycle() }
        glyphs.zipWithNext().forEach { (first, next) ->
            assertTrue("planned mathematical characters draw in operand order: $expected / $glyphs",
                first.right <= next.left + 1f)
        }
    }

    /** Interior native pixels exclude the thumb's outline and equipment badge. */
    fun still(under: String, size: Dp): List<Int> {
        tag(under)
        val images = compose.silentSquaresUnder(hasTestTag(under), size)
        assertEquals("one actual decorative exercise image beside the full name", 1, images.size)
        val imageId = images.single().id
        val image = compose.onNode(SemanticsMatcher("the actual decorative exercise image") { it.id == imageId }, useUnmergedTree = true)
        // A complete identity header can be taller than a small window. Artwork is
        // a measured 56 dp square; reveal that square through its real ancestors.
        reach(image)
        val node = image.fetchSemanticsNode()
        assertTrue("art has no second spoken name", node.config.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty())
        assertTrue("art has no separate click", node.config.getOrNull(SemanticsActions.OnClick) == null)
        val bounds = full(node)
        val sample: () -> List<Int> = {
            val frame = draw()
            try {
                (0 until 32).flatMap { y -> (0 until 32).map { x ->
                    val px = (bounds.left + bounds.width * (.12f + x * .022f)).toInt()
                    val py = (bounds.top + bounds.height * (.12f + y * .022f)).toInt()
                    frame.getPixel(px.coerceIn(0, frame.width - 1), py.coerceIn(0, frame.height - 1))
                } }
            } finally { frame.recycle() }
        }
        var pixels = emptyList<Int>()
        compose.awaitThat("actual native artwork is loaded", { pixels.count { !isNear(it, Pit) } }) {
            pixels = sample()
            pixels.count { !isNear(it, Pit) } > 25
        }
        return pixels
    }

    fun sameArt(actual: List<Int>, expected: List<Int>, unrelated: List<Int>) {
        assertEquals("art samples have the same shape", actual.size, expected.size)
        val matching = actual.indices.count { isNear(actual[it], Color(expected[it])) }
        val wrong = actual.indices.count { !isNear(actual[it], Color(unrelated[it])) }
        assertTrue("native receipt art matches its independent exercise identity: $matching/${actual.size}", matching >= actual.size * .91f)
        assertTrue("comparison distinguishes a different exercise's actual art: $wrong/${actual.size}", wrong >= actual.size * .04f)
    }

    fun capture(stage: String, failure: String? = null) {
        if (failure == null) drain()
        val frame = draw()
        try {
            val directory = File("build/screen-renders/summary-saved-work/$runId/$profile")
            check(directory.isDirectory || directory.mkdirs())
            val png = File(directory, "$stage.png")
            png.outputStream().use { check(frame.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            assertTrue("fresh actual native frame saved", png.length() > 1_000)
            val nodes = compose.onAllNodes(SemanticsMatcher("all actual semantics") { true }, useUnmergedTree = true)
                .fetchSemanticsNodes().joinToString("\n") {
                    "${it.config} size=${it.size} origin=${it.positionInWindow} clip=${it.boundsInWindow}"
                }
            File(directory, "$stage.txt").writeText(
                "viewport=${frame.width}x${frame.height}\nfont=$font\n${facts()}\n" +
                    (failure?.let { "failure=$it\n" } ?: "") + nodes,
            )
        } finally { frame.recycle() }
    }

    fun drain() {
        repeat(3) {
            compose.settle()
            val pending = compose.runOnUiThread {
                val roots = mutableListOf<View>()
                fun visit(view: View) {
                    if (view.javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") roots += view
                    if (view is ViewGroup) repeat(view.childCount) { visit(view.getChildAt(it)) }
                }
                visit(decor())
                check(roots.isNotEmpty())
                roots.forEach { root -> root.javaClass.methods.single { it.name == "measureAndLayoutForTest" && it.parameterCount == 0 }.invoke(root) }
                roots.any { root -> root.javaClass.methods.single { it.name == "getHasPendingMeasureOrLayout" && it.parameterCount == 0 }.invoke(root) as Boolean }
            }
            if (!pending) return
        }
        throw AssertionError("$profile actual Compose roots still need layout after 60 explicit frames")
    }

    private fun full(node: SemanticsNode): SavedWorkRect {
        val origin = node.positionInWindow
        return SavedWorkRect(origin.x, origin.y, origin.x + node.size.width, origin.y + node.size.height)
    }

    private fun decor(): View = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView
        ?: compose.activity.window.decorView

    private fun window(): SavedWorkRect = compose.runOnUiThread {
        val view = decor()
        SavedWorkRect(0f, 0f, view.width.toFloat(), view.height.toFloat())
    }

    private fun draw(): Bitmap = compose.runOnUiThread {
        val view = decor()
        check(view.width > 1 && view.height > 1)
        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }
}
