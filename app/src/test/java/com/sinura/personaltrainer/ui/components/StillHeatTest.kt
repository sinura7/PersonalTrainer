package com.sinura.personaltrainer.ui.components

import androidx.compose.ui.graphics.Color
import com.sinura.personaltrainer.ui.theme.Heat1
import com.sinura.personaltrainer.ui.theme.Heat3
import com.sinura.personaltrainer.ui.theme.HeatEmpty
import com.sinura.personaltrainer.ui.theme.Steel
import com.sinura.personaltrainer.ui.theme.SteelDim
import com.sinura.personaltrainer.ui.theme.heatColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Body live heat sits on the unlit still. Rest is the photograph; only
 * trained plates take a wash. The square still center-crops into the
 * tall figure box so tap targets and the person stay the same aspect.
 */
class StillHeatTest {
    @Test
    fun restAndStructureAreNotAWash() {
        assertTrue(isRestFill(HeatEmpty))
        assertTrue(isRestFill(SteelDim))
        assertTrue(isRestFill(Steel))
        assertTrue(isRestFill(heatColor(0f)))
        assertTrue(isRestFill(Color.Transparent))
        assertFalse(isRestFill(Heat1))
        assertFalse(isRestFill(Heat3))
        assertFalse(isRestFill(heatColor(0.22f)))
        assertFalse(isRestFill(heatColor(0.95f)))
    }

    @Test
    fun aSquareStillCropsToTheFigureBox() {
        val (offset, size) = stillSrc(width = 100, height = 100, dstAspect = FIGURE_ASPECT)
        assertEquals(52, size.width)
        assertEquals(100, size.height)
        assertEquals(24, offset.x)
        assertEquals(0, offset.y)
    }

    @Test
    fun aTallStillCropsVertically() {
        val (offset, size) = stillSrc(width = 52, height = 200, dstAspect = FIGURE_ASPECT)
        assertEquals(52, size.width)
        assertEquals(100, size.height)
        assertEquals(0, offset.x)
        assertEquals(50, offset.y)
    }

    @Test
    fun theShippedStandingCropKeepsThePerson() {
        // The 768 stills' figure lives at x 210–560. Center-crop to 0.52
        // must not clip the arms.
        val (offset, size) = stillSrc(width = 768, height = 768, dstAspect = FIGURE_ASPECT)
        assertTrue("crop starts too far right: ${offset.x}", offset.x <= 210)
        assertTrue(
            "crop ends too far left: ${offset.x + size.width}",
            offset.x + size.width >= 560,
        )
        assertEquals(0, offset.y)
        assertEquals(768, size.height)
    }

    @Test
    fun theWebpPitIsNearBlackAndThePlatesAreNot() {
        assertTrue(isPitSample(0xFF000000.toInt()))
        assertTrue(isPitSample(0xFF060606.toInt()))
        assertTrue(isPitSample(0xFF0A0A0A.toInt()))
        assertFalse(isPitSample(0xFF0B0B0B.toInt()))
        // Head ~58, pec ~94 on the unlit still.
        assertFalse(isPitSample(0xFF3A3A3A.toInt()))
        assertFalse(isPitSample(0xFF5E5E5E.toInt()))
    }
}
