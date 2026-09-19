package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptySceneTest {
    @Test
    fun teachingScenesCarryTheNextTap() {
        assertTrue(EmptyScene.RACK.teachesNextTap)
        assertTrue(EmptyScene.PLAN.teachesNextTap)
        assertTrue(EmptyScene.CATALOG.teachesNextTap)
        assertTrue(EmptyScene.LOG.teachesNextTap)
        assertTrue(EmptyScene.RETRY.teachesNextTap)
        assertFalse(EmptyScene.GONE.teachesNextTap)
        assertEquals(6, EmptyScene.entries.size)
    }
}
