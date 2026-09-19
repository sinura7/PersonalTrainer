package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.domain.EmptyScene
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * D-04: empty pictures are a scene vocabulary, not the Temper still, and
 * every EmptyState names which scene it is.
 */
class EmptyArtTest {
    @Test
    fun everySceneStaysInsideTheSquare() {
        val outside = ArrayList<String>()
        EmptyScene.entries.forEach { scene ->
            inkFor(scene).forEach { ink ->
                emptyInkPoints(ink).forEach { (x, y) ->
                    if (x !in 0f..1f) outside += "$scene x=$x"
                    if (y !in 0f..1f) outside += "$scene y=$y"
                }
            }
        }
        assertTrue(outside.joinToString(separator = "\n"), outside.isEmpty())
    }

    @Test
    fun teachingScenesCarryVoltAndGoneDoesNot() {
        EmptyScene.entries.forEach { scene ->
            val kinds = inkFor(scene).map { it.kind }.toSet()
            if (scene.teachesNextTap) {
                assertTrue("$scene has no Volt plus", EmptyInkKind.VOLT in kinds)
            } else {
                assertFalse("$scene painted a Volt plus", EmptyInkKind.VOLT in kinds)
            }
            assertTrue("$scene has no steel", EmptyInkKind.STEEL in kinds)
        }
    }

    @Test
    fun scenesAreDistinctDrawings() {
        val fingerprints = EmptyScene.entries.map { scene ->
            scene to inkFor(scene).joinToString { it.toString() }
        }
        val unique = fingerprints.map { it.second }.toSet()
        assertEquals(fingerprints.map { it.first }.toString(), fingerprints.size, unique.size)
        assertTrue(inkFor(EmptyScene.GONE).any { it is EmptyInk.RoundRect && it.dashed })
        assertTrue(inkFor(EmptyScene.RETRY).any { it is EmptyInk.Arc })
        assertTrue(inkFor(EmptyScene.RACK).count { it is EmptyInk.Line } >= 6)
    }

    @Test
    fun emptyStateDrawsTheSceneNotTheMark() {
        val empty = readOwned("ui/components/EmptyState.kt")
        assertTrue(empty.contains("scene: EmptyScene"))
        assertTrue(empty.contains("EmptyIllustration("))
        assertTrue(empty.contains("EmptyArtCompactSize"))
        assertFalse(empty.contains("TemperMark("))
    }

    @Test
    fun everyEmptyStateNamesItsScene() {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
            File("app/src/debug/java/com/sinura/personaltrainer"),
            File("../app/src/debug/java/com/sinura/personaltrainer"),
        )
        val owned = roots.filter { it.isDirectory }
        assertTrue("app sources missing", owned.isNotEmpty())
        val missing = ArrayList<String>()
        owned.forEach { root ->
            root.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val text = file.readText()
                    if (!text.contains("EmptyState(")) return@forEach
                    if (file.name == "EmptyState.kt") return@forEach
                    val calls = Regex("""EmptyState\(""").findAll(text).count()
                    val scenes = Regex("""scene\s*=\s*EmptyScene\.""").findAll(text).count()
                    if (calls != scenes) {
                        missing += "${file.name}: $calls EmptyState, $scenes scene="
                    }
                }
        }
        assertTrue(missing.joinToString(separator = "\n"), missing.isEmpty())
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }
}
