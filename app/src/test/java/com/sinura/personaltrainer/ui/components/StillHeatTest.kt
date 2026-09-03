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
        val (srcOffset, srcSize) = stillSrc(width = 100, height = 100, dstAspect = FIGURE_ASPECT)
        assertEquals(52, srcSize.width)
        assertEquals(100, srcSize.height)
        assertEquals(24, srcOffset.x)
        assertEquals(0, srcOffset.y)
    }

    @Test
    fun aTallStillCropsVertically() {
        val (srcOffset, srcSize) = stillSrc(width = 52, height = 200, dstAspect = FIGURE_ASPECT)
        assertEquals(52, srcSize.width)
        assertEquals(100, srcSize.height)
        assertEquals(0, srcOffset.x)
        assertEquals(50, srcOffset.y)
    }

    @Test
    fun theShippedStandingCropKeepsThePerson() {
        // The 1024 unlit stills' figure lives at x 280–747 (768's 210–560
        // scaled). Center-crop to 0.52 must not clip the arms.
        val (srcOffset, srcSize) = stillSrc(width = 1024, height = 1024, dstAspect = FIGURE_ASPECT)
        assertTrue("crop starts too far right: ${srcOffset.x}", srcOffset.x <= 280)
        assertTrue(
            "crop ends too far left: ${srcOffset.x + srcSize.width}",
            srcOffset.x + srcSize.width >= 747,
        )
        assertEquals(0, srcOffset.y)
        assertEquals(1024, srcSize.height)
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
