package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A reminder tap received off-Home still switches to Home before
 * Start starts or a body tap opens the confirm.
 */
class ReminderHandoffTest {
    @Test
    fun startWhileAnotherTabIsForegroundLandsOnHome() {
        assertEquals(Route.Home.path, ReminderHandoff.homeTab("occ-1", null))
        assertEquals(Route.Home.path, ReminderHandoff.homeTab("occ-1", "occ-1"))
    }

    @Test
    fun bodyTapLandsOnHomeWithoutAStartId() {
        assertEquals(Route.Home.path, ReminderHandoff.homeTab(null, "occ-1"))
        assertNull(ReminderHandoff.homeTab(null, null))
    }
}
