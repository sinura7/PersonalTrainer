package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reminder tap received off-Home still switches to Home before
 * Start starts or a body tap opens the confirm.
 */
class ReminderHandoffTest {
    @Test
    fun startWhileAnotherTabIsForegroundLandsOnHome() {
        assertEquals(Route.Home.path, ReminderHandoff.homeTab("occ-1", null, sessionLive = false))
        assertEquals(Route.Home.path, ReminderHandoff.homeTab("occ-1", "occ-1", sessionLive = false))
        assertFalse(ReminderHandoff.heldForLiveSession("occ-1", null, sessionLive = false))
    }

    @Test
    fun bodyTapLandsOnHomeWithoutAStartId() {
        assertEquals(Route.Home.path, ReminderHandoff.homeTab(null, "occ-1", sessionLive = false))
        assertNull(ReminderHandoff.homeTab(null, null, sessionLive = false))
    }

    /** Audit UI-1: going to Home popped the live workout, for a start that would be refused. */
    @Test
    fun aTapWhileASessionIsLiveGoesNowhereAndIsHeld() {
        assertNull(ReminderHandoff.homeTab("occ-1", null, sessionLive = true))
        assertNull(ReminderHandoff.homeTab(null, "occ-1", sessionLive = true))
        assertTrue(ReminderHandoff.heldForLiveSession("occ-1", null, sessionLive = true))
        assertTrue(ReminderHandoff.heldForLiveSession(null, "occ-1", sessionLive = true))
    }

    @Test
    fun noTapIsNeverHeld() {
        assertFalse(ReminderHandoff.heldForLiveSession(null, null, sessionLive = true))
        assertNull(ReminderHandoff.homeTab(null, null, sessionLive = true))
    }

    /** On the Home tab, Home would start a held tap as well and draw its own dialog over it. */
    @Test
    fun homeIsHandedATapOnlyWhenNoSessionIsLive() {
        assertEquals("occ-1", ReminderHandoff.forHome("occ-1", sessionLive = false))
        assertNull(ReminderHandoff.forHome("occ-1", sessionLive = true))
    }
}
