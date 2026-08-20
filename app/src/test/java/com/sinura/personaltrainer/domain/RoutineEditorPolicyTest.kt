package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineEditorPolicyTest {
    @Test
    fun newRouteHasNoIncomingId() {
        assertNull(RoutineEditorPolicy.incomingId("new"))
        assertNull(RoutineEditorPolicy.incomingId(""))
        assertNull(RoutineEditorPolicy.incomingId(null))
        assertEquals("abc", RoutineEditorPolicy.incomingId("abc"))
    }

    @Test
    fun discardsEmptyCreatedStubOnly() {
        assertTrue(RoutineEditorPolicy.shouldDiscardStub(createdThisSession = true, exerciseCount = 0))
        assertFalse(RoutineEditorPolicy.shouldDiscardStub(createdThisSession = true, exerciseCount = 1))
        assertFalse(RoutineEditorPolicy.shouldDiscardStub(createdThisSession = false, exerciseCount = 0))
    }
}