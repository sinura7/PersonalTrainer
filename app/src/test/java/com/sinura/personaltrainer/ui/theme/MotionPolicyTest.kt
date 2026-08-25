package com.sinura.personaltrainer.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class MotionPolicyTest {
    @Test
    fun reducedMotionCollapsesDurationsToZero() {
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.BASE))
        assertEquals(0, Motion.durationMs(reduced = true, fullMs = Motion.DRAW))
        assertEquals(Motion.FAST, Motion.durationMs(reduced = false, fullMs = Motion.FAST))
        assertEquals(Motion.TAP, Motion.durationMs(reduced = false, fullMs = Motion.TAP))
    }
}
