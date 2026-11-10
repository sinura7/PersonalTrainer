package com.sinura.personaltrainer.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGymEditTest {
    @Test
    fun gymTestTagsAreStable() {
        assertEquals("session-detail-gym", SessionDetailTestTags.GYM_ROW)
        assertEquals("session-detail-gym-change", SessionDetailTestTags.GYM_CHANGE)
    }
}
