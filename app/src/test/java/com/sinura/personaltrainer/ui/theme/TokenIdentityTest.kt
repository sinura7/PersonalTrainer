package com.sinura.personaltrainer.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The tokens W2a put in place of hand-written values are those values, not look-alikes, so
 * each swap draws what the call site drew before.
 *
 * Every `CircleShape` and `RoundedCornerShape(percent = 50)` in the app became [Radius.full];
 * the icon vectors' 24 dp became [Metrics.icon]; the switch's 4 dp inset and a gallery bar's
 * 4 dp height became [Metrics.space1]. A token that drifts from these values changes those
 * screens, and this is where that shows first.
 */
class TokenIdentityTest {
    @Test
    fun theFullRadiusIsTheCircleAndTheHalfRoundedCorner() {
        assertSame("Radius.full is Compose's own CircleShape", CircleShape, Radius.full)
        assertEquals("a corner of half the side on every side is the same shape", RoundedCornerShape(percent = 50), Radius.full)
    }

    @Test
    fun theIconAndFirstSpacingTokensAreTheirOldValues() {
        assertEquals(24.dp, Metrics.icon)
        assertEquals(4.dp, Metrics.space1)
    }
}
