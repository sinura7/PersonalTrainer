package com.sinura.personaltrainer.data.local

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pre-migration copies are only copies of the old file if they run before Room exists.
 *
 * `AppContainer`'s constructor builds [TemperDatabase], and Room migrates on the first open, so a
 * copy taken after `AppContainer(this)` could be a copy of the already-migrated file.
 * `PersonalTrainerApp` can be started under Robolectric, but not made to fail its copy inside
 * `onCreate`, which is the case that matters, so its order is held here, as a static guard. It is a tripwire, not a proof: it
 * catches the two calls being reordered or the copy call being dropped, not every way the
 * database could be opened earlier.
 */
class PreMigrationStartupOrderTest {

    @Test
    fun theCopiesRunBeforeTheDatabaseIsBuilt() {
        val onCreate = appSource().substringAfter("override fun onCreate()")
        val copies = onCreate.indexOf("PreMigrationSnapshot.ensure(this)")
        val container = onCreate.indexOf("container = AppContainer(this)")

        assertTrue("onCreate must call PreMigrationSnapshot.ensure(this)", copies >= 0)
        assertTrue("onCreate must build the container", container >= 0)
        assertTrue("the copies must be taken before AppContainer builds Room", copies < container)
        assertEquals("one entry point, called once", 1, Regex("PreMigrationSnapshot\\.ensure\\(").findAll(onCreate).count())
    }

    /**
     * Audit AR-2: a copy that fails while being written is logged with
     * [com.sinura.personaltrainer.logging.AppLog.e] and its exception, and an error reaches the
     * diagnostic ring only once `installDiagnosticCapture()` has set `AppLog.onError`. It ran a
     * line after the copy, so that failure reached logcat only, which needs a computer to read.
     */
    @Test
    fun diagnosticsAreCapturedBeforeTheCopiesRun() {
        val onCreate = appSource()
            .substringAfter("override fun onCreate()")
            .substringBefore("private fun installDiagnosticCapture")
        val calls = Regex("""^\s*installDiagnosticCapture\(\)\s*$""", RegexOption.MULTILINE)
            .findAll(onCreate)
            .toList()
        val copies = onCreate.indexOf("PreMigrationSnapshot.ensure(this)")

        assertEquals("onCreate installs the diagnostic capture once, on its own line", 1, calls.size)
        assertTrue("the capture must be installed before the copies run", calls.single().range.first < copies)
    }

    private fun appSource(): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, "PersonalTrainerApp.kt") }.first { it.isFile }.readText()
    }
}
