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
 * `PersonalTrainerApp` cannot be started in a JVM test — its `onCreate` wires services, alarms
 * and sync — so its order is held here, as a static guard. It is a tripwire, not a proof: it
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

    private fun appSource(): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, "PersonalTrainerApp.kt") }.first { it.isFile }.readText()
    }
}
