package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBarCopyTest {
    @Test
    fun workingSetSaysLogSetWithTheDraft() {
        assertEquals(
            "Log set · 100 kg × 5",
            LogBarCopy.commit(
                editing = false,
                next = false,
                warmup = false,
                draftLabel = "100 kg × 5",
            ),
        )
        assertEquals("Log set", LogBarCopy.LOG_SET)
    }

    @Test
    fun warmupSaysLogWarmUpWithTheDraft() {
        assertEquals(
            "Log warm-up · 60 kg × 8",
            LogBarCopy.commit(
                editing = false,
                next = false,
                warmup = true,
                draftLabel = "60 kg × 8",
            ),
        )
        assertEquals("Log warm-up", LogBarCopy.LOG_WARMUP)
        assertFalse(
            LogBarCopy.commit(
                editing = false,
                next = false,
                warmup = true,
                draftLabel = "60 kg × 8",
            ).startsWith(LogBarCopy.LOG_SET),
        )
    }

    @Test
    fun editingAWarmupSaysSaveWarmUp() {
        assertEquals(
            "Save warm-up · 40 kg × 10",
            LogBarCopy.commit(
                editing = true,
                next = false,
                warmup = true,
                draftLabel = "40 kg × 10",
            ),
        )
    }

    @Test
    fun editingAWorkingSetSaysSaveSet() {
        assertEquals(
            "Save set · 100 kg × 5",
            LogBarCopy.commit(
                editing = true,
                next = false,
                warmup = false,
                draftLabel = "100 kg × 5",
            ),
        )
    }

    @Test
    fun nextWinsOverWarmupUnlessEditing() {
        assertEquals(
            "Next",
            LogBarCopy.commit(
                editing = false,
                next = true,
                warmup = true,
                draftLabel = "100 kg × 5",
            ),
        )
        assertEquals(
            "Save warm-up · 100 kg × 5",
            LogBarCopy.commit(
                editing = true,
                next = true,
                warmup = true,
                draftLabel = "100 kg × 5",
            ),
        )
    }

    @Test
    fun aBlankDraftIsJustTheVerb() {
        assertEquals(
            "Log set",
            LogBarCopy.commit(
                editing = false,
                next = false,
                warmup = false,
                draftLabel = "  ",
            ),
        )
        assertEquals(
            "Log warm-up",
            LogBarCopy.commit(
                editing = false,
                next = false,
                warmup = true,
                draftLabel = "",
            ),
        )
    }

    @Test
    fun warmupAndWorkingVerbsAreDistinct() {
        val working = LogBarCopy.commit(
            editing = false,
            next = false,
            warmup = false,
            draftLabel = "100 kg × 5",
        )
        val warmup = LogBarCopy.commit(
            editing = false,
            next = false,
            warmup = true,
            draftLabel = "100 kg × 5",
        )
        assertTrue(working, working.contains(LogBarCopy.LOG_SET))
        assertTrue(warmup, warmup.contains(LogBarCopy.LOG_WARMUP))
        assertFalse(working, working.contains(LogBarCopy.LOG_WARMUP))
        assertFalse(warmup, warmup.startsWith(LogBarCopy.LOG_SET))
    }
}
