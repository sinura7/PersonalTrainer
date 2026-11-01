package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** History's records link must not leak into a later plain visit to Body. */
class BodyRecordsFocusTest {
    @Test
    fun theFlagSurvivesWhileBodyShows() {
        assertTrue(BodyRecordsFocus.afterRouteChange(focus = true, route = Route.Progress.path))
    }

    @Test
    fun leavingBodyClearsTheFlag() {
        assertFalse(BodyRecordsFocus.afterRouteChange(focus = true, route = Route.History.path))
    }

    @Test
    fun anUnsetFlagStaysUnset() {
        assertFalse(BodyRecordsFocus.afterRouteChange(focus = false, route = Route.Progress.path))
    }
}
