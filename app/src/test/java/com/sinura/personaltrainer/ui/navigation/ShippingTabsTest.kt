package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ShippingTabsTest {
    @Test
    fun settingsIsTheFifthTabAndLibraryIsNotATab() {
        assertEquals(
            listOf("Home", "Body", "Plan", "History", "Settings"),
            shippingTabs.map { it.label },
        )
        assertEquals(
            listOf(
                Route.Home.path,
                Route.Progress.path,
                Route.Routines.path,
                Route.History.path,
                Route.Settings.path,
            ),
            shippingTabs.map { it.route.path },
        )
        assertFalse(shippingTabs.any { it.route == Route.Library })
        assertFalse(shippingTabs.any { it.route == Route.Goals })
    }
}
