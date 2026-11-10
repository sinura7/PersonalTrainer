package com.sinura.personaltrainer.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshTokenTest {
    @Test
    fun refreshTypeRolesMatchBlueprint() {
        assertEquals(40.sp, InstrumentType.displayHero.fontSize)
        assertEquals(44.sp, InstrumentType.displayHero.lineHeight)
        assertEquals(9.sp, InstrumentType.roleTag.fontSize)
        assertEquals(13.sp, InstrumentType.numeralPill.fontSize)
    }

    @Test
    fun refreshColourTokensMatchBlueprint() {
        assertEquals(Color(0x61C2FF44), VoltEdge)
        assertEquals(Color(0x1AC2FF44), VoltGlowCenter)
        assertEquals(Color(0x9E000000), Scrim)
        assertEquals(TextPrimary, RoleMainInk)
        assertEquals(RestCyan, RoleWarmupInk)
        assertEquals(Color(0xFFC2FF44), Volt)
        assertTrue(ContrastPolicy.meetsAA(PitOnVoltSubtle, Volt))
    }

    @Test
    fun refreshMetricsMatchBlueprintAndRestBarUnchanged() {
        assertEquals(48.dp, Metrics.touchMin)
        assertEquals(56.dp, Metrics.restBar)
        assertEquals(72.dp, Metrics.commit)
        assertEquals(3.dp, Metrics.restBarAccent)
        assertEquals(18.dp, Metrics.heroCardPadding)
        assertEquals(44.dp, Metrics.gymPlateBadge)
        assertEquals(96.dp, Metrics.gymPlateMinHeight)
        assertEquals(8.dp, Metrics.gymPlateGap)
        assertEquals(72.dp, Metrics.statStripMin)
        assertEquals(19.dp, Metrics.roleTagHeight)
    }

    @Test
    fun plateCheckMotionMatchesFast() {
        assertEquals(Motion.FAST, Motion.PLATE_CHECK_MS)
    }
}
