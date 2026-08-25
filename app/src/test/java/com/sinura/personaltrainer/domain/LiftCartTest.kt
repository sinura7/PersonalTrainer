package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiftCartTest {
    @Test
    fun toggleAppendsThenRemovesWithoutShufflingTheRest() {
        val afterSquat = LiftCart.toggle(emptyList(), "squat")
        val afterRow = LiftCart.toggle(afterSquat, "row")
        val afterBench = LiftCart.toggle(afterRow, "bench")
        assertEquals(listOf("squat", "row", "bench"), afterBench)
        assertEquals(listOf("squat", "bench"), LiftCart.toggle(afterBench, "row"))
        assertEquals(afterRow, LiftCart.toggle(afterBench, "bench"))
    }

    @Test
    fun cartNumberIsOneBasedTapOrder() {
        val order = listOf("squat", "row", "bench")
        assertEquals(1, LiftCart.cartNumber(order, "squat"))
        assertEquals(2, LiftCart.cartNumber(order, "row"))
        assertEquals(3, LiftCart.cartNumber(order, "bench"))
        assertNull(LiftCart.cartNumber(order, "curl"))
        assertNull(LiftCart.cartNumber(emptyList(), "squat"))
    }
}
