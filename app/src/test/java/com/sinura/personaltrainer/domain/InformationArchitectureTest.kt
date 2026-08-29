package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InformationArchitectureTest {
    @Test
    fun fourTabsAndPushedLibrary() {
        assertEquals(4, InformationArchitecture.tabs.size)
        assertEquals(
            listOf("Home", "Body", "Plan", "History"),
            InformationArchitecture.tabs.map { it.label },
        )
        assertTrue(InformationArchitecture.tabs.all { it.isTab })
        assertTrue(InformationArchitecture.pushedNeverTabs.none { it.isTab })
        assertTrue(InformationArchitecture.pushedNeverTabs.contains(LandingSurface.LIBRARY))
        assertFalse(InformationArchitecture.pushedNeverTabs.contains(LandingSurface.GOALS))
    }

    @Test
    fun eachCanonicalTaskHasANamedFirstClick() {
        CanonicalTask.entries.forEach { task ->
            val landing = InformationArchitecture.landing(task)
            assertEquals(task, landing.task)
            assertTrue("${task.id} first click must be a tab or a named push", landing.firstClick in LandingSurface.entries)
        }
        assertEquals(LandingSurface.HOME, InformationArchitecture.landing(CanonicalTask.T1).firstClick)
        assertEquals(LandingSurface.HOME, InformationArchitecture.landing(CanonicalTask.T2).firstClick)
        assertEquals(LandingSurface.PLAN, InformationArchitecture.landing(CanonicalTask.T3).firstClick)
        assertEquals(LandingSurface.HISTORY, InformationArchitecture.landing(CanonicalTask.T4).firstClick)
        assertTrue(
            InformationArchitecture.landing(CanonicalTask.T4).also.contains(LandingSurface.HOME),
        )
        assertEquals(LandingSurface.BODY, InformationArchitecture.landing(CanonicalTask.T5).firstClick)
        assertEquals(LandingSurface.HISTORY, InformationArchitecture.landing(CanonicalTask.T6).firstClick)
        assertEquals(LandingSurface.PLAN, InformationArchitecture.landing(CanonicalTask.T7).primary)
    }

    @Test
    fun reconsiderationGateStaysClosedWithoutComparativeEvidence() {
        assertFalse(
            InformationArchitecture.reconsiderationGateOpen(
                unassistedCompletionBelow80 = true,
                majorityFirstClickFailure = true,
                comparativePrototypeTested = false,
                alternativeHoldsOtherTasks = true,
            ),
        )
        assertTrue(
            InformationArchitecture.reconsiderationGateOpen(
                unassistedCompletionBelow80 = true,
                majorityFirstClickFailure = false,
                comparativePrototypeTested = true,
                alternativeHoldsOtherTasks = true,
            ),
        )
    }
}
