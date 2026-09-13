package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetStartedCopyTest {
    @Test
    fun emptyHomeHasNoBlockingSheet() {
        assertEquals("Start a workout", GetStartedCopy.WORKOUT)
        assertTrue(GetStartedCopy.EMPTY_CAPTION.contains("Start a workout"))
        assertFalse(GetStartedCopy.EMPTY_CAPTION.contains("Generate", ignoreCase = true))
        assertFalse(GetStartedCopy.EMPTY_CAPTION.contains("Get started", ignoreCase = true))
        val gone = listOf(
            java.io.File("app/src/main/java/com/sinura/personaltrainer/ui/home/GetStartedSheet.kt"),
            java.io.File("../app/src/main/java/com/sinura/personaltrainer/ui/home/GetStartedSheet.kt"),
        )
        assertFalse(gone.any { it.isFile })
    }
}
