package com.sinura.personaltrainer.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertFalse(shippingTabs.any { it.route == Route.PlanDay })
        assertEquals(
            listOf("Home", "Body", "Plan", "History", "Settings"),
            shippingTabs.map { it.icon.name },
        )
    }

    @Test
    fun thePermissionWalkMayShowOnEveryTabButSettings() {
        // The frame after a rotation on Settings has no route yet; it is not Home.
        assertFalse(routeAllowsPermissionWalk(route = null))
        listOf(Route.Home, Route.Progress, Route.Routines, Route.History).forEach { tab ->
            assertTrue(tab.path, routeAllowsPermissionWalk(route = tab.path))
        }
        assertFalse(routeAllowsPermissionWalk(route = Route.Settings.path))
        // Pushed screens wait too: the workout floor, Library, a plan day.
        assertFalse(routeAllowsPermissionWalk(route = Route.Library.path))
        assertFalse(routeAllowsPermissionWalk(route = Route.PlanDay.path))
    }
}
