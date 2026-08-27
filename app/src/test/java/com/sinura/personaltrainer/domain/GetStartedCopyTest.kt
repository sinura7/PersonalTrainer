package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetStartedCopyTest {
    @Test
    fun firstVisitNamesTheThreePaths() {
        assertEquals("Get started", GetStartedCopy.TITLE)
        assertEquals("Generate a schedule", GetStartedCopy.GENERATE)
        assertEquals("Build a week", GetStartedCopy.BUILD)
        assertEquals("Start a workout", GetStartedCopy.WORKOUT)
        assertTrue(GetStartedCopy.BODY.contains("generate"))
        assertTrue(GetStartedCopy.BODY.contains("Build"))
        assertTrue(GetStartedCopy.BODY.contains("workout"))
    }
}
