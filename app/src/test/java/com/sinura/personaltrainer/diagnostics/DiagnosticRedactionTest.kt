package com.sinura.personaltrainer.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticRedactionTest {
    @Test
    fun bundleKeepsClassAndAppFramesAndDropsCanaries() {
        val error = IllegalStateException(DiagnosticCanaries.MESSAGE)
        error.stackTrace = arrayOf(
            StackTraceElement(
                "com.sinura.personaltrainer.workout.FinishWorkout",
                "invoke",
                "FinishWorkout.kt",
                42,
            ),
            StackTraceElement(
                "android.app.ActivityThread",
                "handleMessage",
                "ActivityThread.java",
                1,
            ),
        )
        val event = DiagnosticRedaction.fromThrowable(
            error = error,
            tag = "PT/App",
            nowMs = 1_700_000_000_000L,
            id = "evt-1",
        )
        assertEquals("java.lang.IllegalStateException", event.exceptionClass)
        assertEquals(listOf("FinishWorkout.invoke:42"), event.frames)
        assertEquals("PT/App", event.tag)

        val bundle = DiagnosticRedaction.renderBundle(
            events = listOf(event),
            metadata = mapOf(
                "appVersion" to "1.0.0",
                "dbSchema" to "4",
                "note" to "none",
            ),
        )
        assertTrue(bundle.contains("schema=${DiagnosticRedaction.SCHEMA}"))
        assertTrue(bundle.contains("class=java.lang.IllegalStateException"))
        assertTrue(bundle.contains("frame FinishWorkout.invoke:42"))
        assertFalse(bundle.contains("ActivityThread"))
        assertFalse(
            DiagnosticRedaction.containsForbidden(bundle, DiagnosticCanaries.ALL),
        )
    }

    @Test
    fun ringCapsAndDropsOldest() {
        val store = DiagnosticStore(capacity = 2)
        store.record(event("a"))
        store.record(event("b"))
        store.record(event("c"))
        assertEquals(listOf("b", "c"), store.snapshot().map { it.id })
        store.clear()
        assertTrue(store.snapshot().isEmpty())
    }

    @Test
    fun foreignTagIsDroppedFromTheBundle() {
        val event = DiagnosticRedaction.fromThrowable(
            error = RuntimeException("x"),
            tag = DiagnosticCanaries.EMAIL,
            nowMs = 1L,
            id = "evt-2",
        )
        assertEquals(null, event.tag)
        val bundle = DiagnosticRedaction.renderBundle(listOf(event), emptyMap())
        assertFalse(bundle.contains(DiagnosticCanaries.EMAIL))
    }

    private fun event(id: String) = DiagnosticEvent(
        id = id,
        atMs = 1L,
        kind = "exception",
        exceptionClass = "java.lang.IllegalStateException",
        tag = "PT/App",
        frames = emptyList(),
    )
}
