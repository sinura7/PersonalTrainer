package com.sinura.personaltrainer.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionDetailCopyTest {
    @Test
    fun deleteDialogNamesTheSession() {
        assertEquals("Delete Upper strength?", sessionDeleteTitle("Upper strength"))
        assertEquals("Delete this session?", sessionDeleteTitle(null))
        assertEquals("Delete this session?", sessionDeleteTitle("  "))
    }
}
