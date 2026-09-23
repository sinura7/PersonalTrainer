package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogLoopScaleTest {
    @Test
    fun defaultAndLargeStaySideBySide() {
        assertFalse(LogLoopScale.stackEntryWells(1.0f))
        assertFalse(LogLoopScale.stackEntryWells(1.3f))
        assertFalse(LogLoopScale.stackEntryWells(1.59f))
    }

    @Test
    fun accessibilityTwoStacksTheWells() {
        assertTrue(LogLoopScale.stackEntryWells(1.6f))
        assertTrue(LogLoopScale.stackEntryWells(2.0f))
    }

    @Test
    fun homeHeadlineGainsALineWhenTheWellsStack() {
        assertEquals(2, LogLoopScale.headlineLines(1.0f))
        assertEquals(3, LogLoopScale.headlineLines(2.0f))
    }

    @Test
    fun tileNumeralShrinksOnceWellsStack() {
        assertEquals(36f, LogLoopScale.tileNumeral(1.0f).fontSize.value, 0.001f)
        assertEquals(36f / 1.6f, LogLoopScale.tileNumeral(1.6f).fontSize.value, 0.001f)
        assertEquals(18f, LogLoopScale.tileNumeral(2.0f).fontSize.value, 0.001f)
        assertEquals(
            InstrumentType.numeralLg.fontSize.value,
            LogLoopScale.tileNumeral(1.3f).fontSize.value,
            0.001f,
        )
    }

    @Test
    fun tilesStackAtAccessibilityScale() {
        assertFalse(LogLoopScale.stackTiles(1.0f))
        assertFalse(LogLoopScale.stackTiles(1.59f))
        assertTrue(LogLoopScale.stackTiles(1.6f))
        assertTrue(LogLoopScale.stackTiles(2.0f))
    }

    /** From the value's own size down to its label's, half a point at a time, largest first. */
    @Test
    fun aStatsValueStepsFromItsOwnSizeToItsLabelsHalfAPointAtATime() {
        val sizes = LogLoopScale.STAT_VALUE_SIZES.map { it.value }
        assertEquals(InstrumentType.numeralSm.fontSize.value, sizes.first(), 0.001f)
        assertEquals(InstrumentType.caption.fontSize.value, sizes.last(), 0.001f)
        assertTrue(LogLoopScale.STAT_VALUE_SIZES.all { it.isSp })
        sizes.zipWithNext { larger, smaller -> assertEquals(0.5f, larger - smaller, 0.001f) }
        assertEquals(InstrumentType.caption.fontSize, LogLoopScale.statValueFloor.fontSize)
    }

    /** A glyph's design size, read as dp: at font 2.0 it is drawn as at 1.0, whatever its line height's unit. */
    @Test
    fun aFixedGlyphIgnoresTheFontScaleAndKeepsARelativeLineHeight() {
        val big = Density(density = 2f, fontScale = 2f)
        val sp = LogLoopScale.fixedGlyph(InstrumentType.commit, big)
        assertEquals(InstrumentType.commit.fontSize.value / 2f, sp.fontSize.value, 0.001f)
        assertEquals(InstrumentType.commit.lineHeight.value / 2f, sp.lineHeight.value, 0.001f)
        val em = LogLoopScale.fixedGlyph(InstrumentType.commit.copy(lineHeight = 1.2.em), big)
        assertEquals(1.2.em, em.lineHeight)
        val unspecified = LogLoopScale.fixedGlyph(InstrumentType.commit.copy(lineHeight = TextUnit.Unspecified), big)
        assertEquals(TextUnit.Unspecified, unspecified.lineHeight)
        assertTrue(runCatching { LogLoopScale.fixedGlyph(InstrumentType.commit.copy(fontSize = 1.em), big) }.isFailure)
    }

    /** A field with no room at all still draws its value at a positive size, never zero or less. */
    @Test
    fun aNumeralWithNoRoomStillHasAPositiveSize() {
        val width = { text: String, style: TextStyle -> (text.length * style.fontSize.value).toInt() }
        listOf(0, -40).forEach { room ->
            val fitted = LogLoopScale.fittedNumeral("99999.99", InstrumentType.numeralXl, room, width)
            assertTrue("room $room gave ${fitted.fontSize}", fitted.fontSize.value > 0f && fitted.lineHeight.value > 0f)
        }
        assertEquals(InstrumentType.numeralXl, LogLoopScale.fittedNumeral("88", InstrumentType.numeralXl, roomPx = 10_000, widthPx = width))
        assertEquals(InstrumentType.numeralMd, LogLoopScale.fittedNumeral("99999.99", InstrumentType.numeralXl, roomPx = 200, widthPx = width))
    }
}
