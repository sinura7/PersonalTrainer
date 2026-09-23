package com.sinura.personaltrainer.ui.workout

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.sinura.personaltrainer.domain.EquipmentType
import com.sinura.personaltrainer.domain.IncrementTable
import com.sinura.personaltrainer.domain.LoadClass
import com.sinura.personaltrainer.domain.LoadType
import com.sinura.personaltrainer.domain.SetCopy
import com.sinura.personaltrainer.domain.WeightConverter
import com.sinura.personaltrainer.domain.WeightMeaning
import com.sinura.personaltrainer.domain.WeightUnit
import com.sinura.personaltrainer.ui.theme.Metrics
import com.sinura.personaltrainer.ui.theme.Pit
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Packet W1d: the entry's − / + plates and an outsized weight, drawn whole at large text.
 *
 * The plates. Each is a 48 dp target drawing a 36 dp circle (#359), and its glyph was text that
 * grew with the system font inside a slice of that circle 20 dp tall. At font 1.6 the + lost its
 * foot and the − sat on the slice's edge as "_"; at 2.0 the + was a stub and the − drew nothing.
 * These frames draw the plates as the phone does and read the pixels: each glyph must be inked
 * whole, inside its circle, from a text laid out whole, with the target and the circle unchanged.
 *
 * The weight. The numeral's size comes from a fixed `888.8` sample so it never jumps (ADR-027
 * decision 7), and it is one line that may draw past its box. A value wider than the sample
 * took the whole field, and the unit after it got no width: 99,999.99 kg at 360 dp and font 2.0
 * (the hosted "large" layout case), and a realistic 1102.5 lb at 412 dp and font 1.0. Here every
 * digit and the unit must be laid out whole, and the field must keep the sample's line so the
 * plates beneath do not move.
 *
 * Native graphics and the floor's own 16 dp gutter; frames to `app/build/screen-renders/w1d/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w360dp-h800dp-xhdpi")
class LargeTextEntryRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var weightKg by mutableStateOf(FLOOR_KG70)
    private var unit by mutableStateOf(FLOOR_UNIT)

    @Test
    fun thePlatesDrawTheirGlyphsWholeAtFont10() = platesDrawWhole(widthDp = 360, fontScale = 1f)

    @Test
    fun thePlatesDrawTheirGlyphsWholeAtFont16() = platesDrawWhole(widthDp = 360, fontScale = 1.6f)

    @Test
    fun thePlatesDrawTheirGlyphsWholeAtFont20() = platesDrawWhole(widthDp = 360, fontScale = 2f)

    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun thePlatesDrawTheirGlyphsWholeAt412Font20() = platesDrawWhole(widthDp = 412, fontScale = 2f)

    @Test
    fun anOutsizedWeightKeepsEveryDigitAndItsUnitAt360Font20() =
        keepsItsUnit(widthDp = 360, fontScale = 2f, shown = WeightUnit.KG, kg = 99_999.99)

    /** One digit more than the sample: the smallest value that has to step down on this phone. */
    @Test
    fun aWeightJustWiderThanTheSampleKeepsItsUnitAt360Font20() =
        keepsItsUnit(widthDp = 360, fontScale = 2f, shown = WeightUnit.KG, kg = 1_102.5)

    /** A leg press past half a ton, at default text on the most common width. */
    @Test
    @Config(qualifiers = "w412dp-h840dp-xhdpi")
    fun aRealLoadJustWiderThanTheSampleKeepsItsUnitAt412Font10() =
        keepsItsUnit(widthDp = 412, fontScale = 1f, shown = WeightUnit.LBS, kg = WeightConverter.lbsToKg(1_102.5))

    /**
     * The editor on every pixel width across the one where `1102.5 lb` starts to fit `numeralXl`
     * beside reps. Where the row is an odd number of pixels, Compose gives the
     * weight column the smaller half, a pixel less than the width the numeral's room is worked
     * out from, so at the very edge a value that "fits" by that sum would leave its unit a pixel
     * short. The room keeps one pixel back for it; here every width must lay out the digits and
     * the unit whole, and the sweep must see the value both at its sample's size and stepped down.
     */
    @Test
    @Config(qualifiers = "w560dp-h840dp-xhdpi")
    fun aValueAtTheEdgeOfItsRoomKeepsItsUnitOnEveryPixelWidth() {
        unit = WeightUnit.LBS
        weightKg = WeightConverter.lbsToKg(1_102.5)
        var widthPx by mutableStateOf(EDGE_SWEEP_FROM_PX)
        compose.showFloor(fontScale = 1f) {
            val density = LocalDensity.current
            Box(Modifier.width(with(density) { widthPx.toDp() }).background(Pit)) {
                WeightRepsEditor(
                    enabled = true, weightKg = weightKg, reps = 10, unit = unit,
                    loadClass = LoadClass.LOADED, loadType = LoadType.EXTERNAL, equipment = EquipmentType.MACHINE,
                    movementKey = null, plated = false, hold = false, holdSeconds = null, holdRunning = false,
                    holdRemainingSeconds = 0, onWeightKgChange = {}, onRepsChange = {}, onSecondsChange = {},
                )
            }
        }
        val value = SetCopy.weightEntryHero(WeightMeaning.LIFTED, weightKg, unit).value
        val sizes = mutableSetOf<Float>()
        for (px in EDGE_SWEEP_FROM_PX..EDGE_SWEEP_TO_PX) {
            widthPx = px
            compose.waitForIdle()
            listOf(value, unit.suffix).forEach { words ->
                val node = compose.onNode(hasText(words) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
                val layout = node.textLayout()
                assertTrue(
                    "\"$words\" must be laid out whole on a $px px row (needs ${layout.multiParagraph.intrinsics.maxIntrinsicWidth}, has ${layout.size.width})",
                    layout.fitsItsWidth(),
                )
                if (words == value) sizes += layout.layoutInput.style.fontSize.value
            }
        }
        assertTrue("the sweep must cross the edge: the value at its sample's size and stepped down, were $sizes", sizes.size >= 2)
    }

    private fun showEditor(fontScale: Float) {
        compose.showFloor(fontScale = fontScale) {
            // The floor's gutter: the editor is exactly as wide here as on the phone.
            Box(Modifier.fillMaxWidth().background(Pit).padding(horizontal = Metrics.gutter)) {
                WeightRepsEditor(
                    enabled = true,
                    weightKg = weightKg,
                    reps = 10,
                    unit = unit,
                    loadClass = LoadClass.LOADED,
                    loadType = LoadType.EXTERNAL,
                    equipment = EquipmentType.MACHINE,
                    movementKey = null,
                    plated = false,
                    hold = false,
                    holdSeconds = null,
                    holdRunning = false,
                    holdRemainingSeconds = 0,
                    onWeightKgChange = {},
                    onRepsChange = {},
                    onSecondsChange = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun plateWords(): List<String> {
        val step = IncrementTable.displayStep(LoadType.EXTERNAL, unit, EquipmentType.MACHINE)
            ?.let { WeightConverter.formatDisplayNumber(it) }
            ?: unit.stepLabel
        return listOf("Decrease weight by $step ${unit.suffix}", "Increase weight by $step ${unit.suffix}", "Decrease reps by 1", "Increase reps by 1")
    }

    private fun platesDrawWhole(widthDp: Int, fontScale: Float) {
        showEditor(fontScale)
        val frame = drawWindow()
        save(frame, "plates-${widthDp}dp-font$fontScale")
        plateWords().forEach { words ->
            val target = compose.onNodeWithContentDescription(words)
                .assertIsDisplayed()
                .assertHeightIsAtLeast(Metrics.touchMin)
                .assertWidthIsAtLeast(Metrics.touchMin)
                .fetchSemanticsNode()
            val density = target.layoutInfo.density
            val box = target.boundsInWindow
            val centre = box.center
            val px = { dips: Float -> dips * density.density }
            // The drawn circle, inset inside the target by the #359 amount.
            val radius = box.width / 2f - with(density) { Metrics.stepperPlateInset.toPx() }
            assertEquals("\"$words\": the plate is drawn 36 dp across inside its 48 dp target", radius * 2f, plateSpan(frame, box).toFloat(), 2f)
            val glyph = compose.onAllNodes(hasAnyAncestor(hasContentDescription(words)), useUnmergedTree = true)
                .fetchSemanticsNodes()
                .single { SemanticsProperties.Text in it.config }
            val label = glyph.config[SemanticsProperties.Text].joinToString { it.text }
            // The ink first: what the lifter sees.
            val ink = inkBox(frame, box)
            assertTrue("\"$words\": the $label is drawn at font $fontScale, no ink found", ink != null)
            ink!!
            // Cut by a box too short for it, the glyph's ink lands off the plate's centre: the −
            // on the box's floor as "_" at font 1.6, 8 dp low.
            assertTrue(
                "\"$words\": the $label is drawn at its plate's centre at font $fontScale, ink $ink around $centre",
                ink.centre().distanceTo(centre) <= px(CENTRE_TOLERANCE_DP),
            )
            if (label == "+") {
                assertEquals("\"$words\": the + is drawn with both arms whole at font $fontScale, ink $ink", ink.width, ink.height, 2f)
            } else {
                assertTrue(
                    "\"$words\": the − is drawn as a whole bar at font $fontScale, ink $ink",
                    ink.width >= px(8f) && ink.width >= ink.height * 3f,
                )
            }
            assertTrue("\"$words\": the $label's ink stays inside its circle, ink $ink", ink.corners().all { it.distanceTo(centre) <= radius + 1f })
            // Then the text it is drawn from: laid out whole in the circle, never cut by its own box.
            val layout = glyph.laidOutText()
            assertEquals("\"$words\": the $label is one line", 1, layout.lineCount)
            assertTrue(
                "\"$words\": the $label's line (${layout.multiParagraph.height} px) fits its box (${glyph.size.height} px) at font $fontScale",
                layout.multiParagraph.height <= glyph.size.height + 0.5f,
            )
            assertTrue("\"$words\": the $label is laid out whole across", layout.multiParagraph.intrinsics.maxIntrinsicWidth <= glyph.size.width + 0.5f)
            assertTrue(
                "\"$words\": the $label's box stays inside its circle",
                glyph.boundsInWindow.corners().all { it.distanceTo(centre) <= radius + 0.5f },
            )
        }
    }

    private fun keepsItsUnit(widthDp: Int, fontScale: Float, shown: WeightUnit, kg: Double) {
        unit = shown
        showEditor(fontScale)
        val plates = plateWords()
        // First a value the sample covers: where the field and its plates belong.
        val sampleField = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).fetchSemanticsNode().boundsInRoot
        val samplePlates = plates.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        weightKg = kg
        compose.waitForIdle()
        val hero = SetCopy.weightEntryHero(WeightMeaning.LIFTED, kg, shown)
        save(drawWindow(), "outsized-${widthDp}dp-font$fontScale-${hero.value}${shown.suffix}")
        val field = compose.onNodeWithTag(WorkoutTestTags.WEIGHT_STEPPER).fetchSemanticsNode().boundsInRoot
        listOf(hero.value, shown.suffix).forEach { words ->
            val node = compose.onNode(hasText(words) and hasAnyAncestor(hasTestTag(WorkoutTestTags.WEIGHT_STEPPER)), useUnmergedTree = true)
            val laidOut = node.fetchSemanticsNode()
            assertTrue("\"$words\" must be given room at font $fontScale on $widthDp dp, was ${laidOut.size}", laidOut.size.width > 0)
            assertTrue("\"$words\" must be laid out whole at font $fontScale on $widthDp dp", node.textLayout().fitsItsWidth())
            val bounds = laidOut.boundsInRoot
            assertTrue("\"$words\" must sit inside its field, was $bounds in $field", bounds.left >= field.left - 0.5f && bounds.right <= field.right + 0.5f)
        }
        assertEquals("the field keeps the sample's line, so nothing beneath moves", sampleField, field)
        assertEquals(
            "the plates are placed from the sample, and stay put for a value wider than it",
            samplePlates,
            plates.map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot },
        )
    }

    /** Robolectric never delivers the draw callback captureToImage waits on: draw the window ourselves. */
    private fun drawWindow(): Bitmap = compose.runOnIdle {
        val decor = compose.activity.window.decorView
        val out = Bitmap.createBitmap(decor.width.coerceAtLeast(1), decor.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(out))
        out
    }

    private fun save(frame: Bitmap, name: String) {
        val out = File("build/screen-renders/w1d").apply { mkdirs() }
        FileOutputStream(File(out, "$name.png")).use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(frame.width > 0 && frame.height > 0)
    }

    /** How wide the drawn plate is along the target's middle row: its fill, edge and glyph, not the floor behind. */
    private fun plateSpan(frame: Bitmap, box: Rect): Int {
        val y = box.center.y.toInt()
        return (box.left.toInt() until box.right.toInt()).count { x -> luminance(frame.getPixel(x, y)) > PLATE_LUMINANCE }
    }

    /** Where a glyph's ink lies, in window pixels, edges inclusive of the last inked pixel. */
    private data class Ink(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        fun corners(): List<Offset> = listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom))
        fun centre(): Offset = Offset((left + right) / 2f, (top + bottom) / 2f)
    }

    /** The bounds of the glyph's ink inside [box]: the near-white pixels, which only the glyph draws. */
    private fun inkBox(frame: Bitmap, box: Rect): Ink? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in box.top.toInt() until box.bottom.toInt()) {
            for (x in box.left.toInt() until box.right.toInt()) {
                if (luminance(frame.getPixel(x, y)) > INK_LUMINANCE) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (right < 0) null else Ink(left = left.toFloat(), top = top.toFloat(), right = right + 1f, bottom = bottom + 1f)
    }

    private fun luminance(argb: Int): Float =
        ((argb shr 16) and 0xff) * 0.299f + ((argb shr 8) and 0xff) * 0.587f + (argb and 0xff) * 0.114f

    private fun Rect.corners(): List<Offset> = listOf(topLeft, topRight, bottomLeft, bottomRight)

    private fun Offset.distanceTo(other: Offset): Float = hypot(x - other.x, y - other.y)

    /** The glyph's text as Compose laid it out. */
    private fun SemanticsNode.laidOutText(): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        assertTrue(config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts) == true)
        return layouts.single()
    }

    private companion object {
        /** The sweep across the edge, in whole pixels at xhdpi (380 to 520 dp): odd and even widths alike. */
        const val EDGE_SWEEP_FROM_PX = 760
        const val EDGE_SWEEP_TO_PX = 1_040

        /** How far a glyph's ink may sit from its plate's centre: a glyph's own optical offset, not a cut. */
        const val CENTRE_TOLERANCE_DP = 3f

        /** Between the floor (Pit, about 8) and the plate's fill (Surface3, about 32). */
        const val PLATE_LUMINANCE = 20f

        /** Well above the plate's edge (HairlineStrong over Surface3, about 63); the glyph is TextPrimary, about 245. */
        const val INK_LUMINANCE = 128f
    }
}
