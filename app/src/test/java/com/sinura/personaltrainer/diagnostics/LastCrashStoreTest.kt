package com.sinura.personaltrainer.diagnostics

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LastCrashStoreTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        root = File(System.getProperty("java.io.tmpdir"), "last-crash-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        if (::root.isInitialized) root.deleteRecursively()
    }

    @Test
    fun roundTripsAnEventFromTheRedactor() {
        val error = IllegalStateException("boom")
        error.stackTrace = arrayOf(
            appFrame("com.sinura.personaltrainer.workout.FinishWorkout", "invoke", 42),
            StackTraceElement("android.app.ActivityThread", "handleMessage", "ActivityThread.java", 1),
            appFrame("com.sinura.personaltrainer.ui.settings.SettingsScreen", "Share", 318),
        )
        val event = DiagnosticRedaction.fromThrowable(
            error = error,
            tag = "PT/Uncaught",
            nowMs = 1_700_000_000_000L,
            id = "evt-1",
        )

        // The directory does not exist yet; save has to make it, the way a first crash would.
        val store = LastCrashStore(root)
        store.save(event)

        assertEquals(event, store.load())
        assertEquals(listOf("FinishWorkout.invoke:42", "SettingsScreen.Share:318"), store.load()?.frames)
    }

    @Test
    fun missingFileLoadsNull() {
        assertNull(LastCrashStore(root).load())
        root.mkdirs()
        assertNull(LastCrashStore(root).load())
    }

    @Test
    fun corruptFileLoadsNullAndDoesNotThrow() {
        root.mkdirs()
        val file = File(root, LastCrashStore.FILE_NAME)
        val store = LastCrashStore(root)

        file.writeText("not a crash at all\n ")
        assertNull(store.load())

        file.writeText("schema=${LastCrashStore.SCHEMA}\nid=evt\natMs=not-a-number\nkind=exception\n")
        assertNull(store.load())

        file.writeText("schema=${LastCrashStore.SCHEMA}\nid=evt\natMs=1\nkind=exception\nid=twice\n")
        assertNull("a repeated field is not a file this store wrote", store.load())

        file.writeText("schema=temper-crash-99\nid=evt\natMs=1\nkind=exception\n")
        assertNull("a future schema is not guessed at", store.load())

        file.writeText("x".repeat((LastCrashStore.MAX_FILE_BYTES + 1).toInt()))
        assertNull("an oversized file is not even read", store.load())
    }

    @Test
    fun saveOverwritesThePreviousCrash() {
        val store = LastCrashStore(root)
        store.save(event(id = "first", atMs = 1L))
        store.save(event(id = "second", atMs = 2L))
        assertEquals("second", store.load()?.id)
        assertEquals(2L, store.load()?.atMs)
    }

    @Test
    fun clearRemovesTheFileAndIsSafeWhenThereIsNone() {
        val store = LastCrashStore(root)
        store.clear()
        assertNull(store.load())

        store.save(event(id = "gone", atMs = 1L))
        store.clear()
        assertNull(store.load())
        assertFalse(File(root, LastCrashStore.FILE_NAME).exists())
    }

    @Test
    fun noTempFileIsLeftBehind() {
        val store = LastCrashStore(root)
        store.save(event(id = "a", atMs = 1L))
        store.save(event(id = "b", atMs = 2L))
        assertEquals(listOf(LastCrashStore.FILE_NAME), root.list()?.toList())
    }

    @Test
    fun oversizedFieldsAndFramesAreClippedSoTheFileStaysBounded() {
        val store = LastCrashStore(root)
        val wide = "w".repeat(5_000)
        store.save(
            DiagnosticEvent(
                id = wide,
                atMs = 7L,
                kind = "exception",
                exceptionClass = wide,
                tag = "PT/Tag\nid=smuggled",
                frames = List(40) { index -> "Frame.method:$index$wide" },
            ),
        )
        val file = File(root, LastCrashStore.FILE_NAME)
        assertTrue(file.length() <= LastCrashStore.MAX_FILE_BYTES)

        val loaded = store.load()
        assertEquals("w".repeat(LastCrashStore.MAX_FIELD), loaded?.id)
        assertEquals(LastCrashStore.MAX_FRAMES, loaded?.frames?.size)
        // The line break became a space, so the tag stayed one field and id stayed the id.
        assertEquals("PT/Tag id=smuggled", loaded?.tag)
    }

    @Test
    fun canaryStringsNeverReachTheFile() {
        // Everything the redactor is meant to drop, in every place a throwable can carry text:
        // its message, a cause's message, a foreign frame's class/method/file, and an app
        // frame's file name (the redactor renders only class and method).
        val payload = DiagnosticCanaries.ALL.joinToString(" ")
        val cause = RuntimeException("cause: $payload")
        val error = IllegalStateException(DiagnosticCanaries.MESSAGE + " " + payload, cause)
        error.stackTrace = arrayOf(
            StackTraceElement(
                "android.os.${DiagnosticCanaries.WORKOUT_NAME}",
                DiagnosticCanaries.NOTE,
                DiagnosticCanaries.PATH,
                5,
            ),
            StackTraceElement(
                "com.sinura.personaltrainer.workout.FinishWorkout",
                "invoke",
                DiagnosticCanaries.PATH,
                42,
            ),
        )
        val event = DiagnosticRedaction.fromThrowable(
            error = error,
            tag = "PT/Uncaught",
            nowMs = 1L,
            id = "evt-canary",
        )

        val store = LastCrashStore(root)
        store.save(event)
        val text = File(root, LastCrashStore.FILE_NAME).readText()

        assertTrue(text.contains("class=java.lang.IllegalStateException"))
        assertTrue(text.contains("frame=FinishWorkout.invoke:42"))
        assertFalse(DiagnosticRedaction.containsForbidden(text, DiagnosticCanaries.ALL))
        assertFalse(text.contains("boom") || text.contains("cause:"))
    }

    private fun appFrame(className: String, method: String, line: Int) =
        StackTraceElement(className, method, "Source.kt", line)

    private fun event(id: String, atMs: Long) = DiagnosticEvent(
        id = id,
        atMs = atMs,
        kind = "exception",
        exceptionClass = "java.lang.IllegalStateException",
        tag = "PT/Uncaught",
        frames = listOf("FinishWorkout.invoke:42"),
    )
}
