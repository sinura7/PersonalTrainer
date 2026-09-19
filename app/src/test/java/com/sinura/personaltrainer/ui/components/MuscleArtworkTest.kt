package com.sinura.personaltrainer.ui.components

import com.sinura.personaltrainer.domain.CanonicalMuscle
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every mapped Body muscle has its own still — a real resource, not a
 * shared fallback, and not a missing id that would compile to 0.
 */
class MuscleArtworkTest {
    @Test
    fun everyMappedMuscleHasItsOwnStill() {
        val ids = CanonicalMuscle.bodyMapOrder.map { muscleArtwork(it) }
        assertEquals(CanonicalMuscle.mapped.size, ids.size)
        assertEquals(
            "two mapped muscles share a still",
            CanonicalMuscle.bodyMapOrder.size,
            ids.toSet().size,
        )
        CanonicalMuscle.bodyMapOrder.forEach { muscle ->
            val id = muscleArtwork(muscle)
            assertNotEquals("${muscle.name} still is missing", 0, id)
            assertTrue(
                "${muscle.name} reused the unlit figure",
                id != artworkFor(LiftPose.ANATOMY, thumbViewFor(muscle)),
            )
        }
    }

    @Test
    fun otherFallsBackToTheUnlitFrontFigure() {
        assertEquals(
            artworkFor(LiftPose.ANATOMY, BodyView.FRONT),
            muscleArtwork(CanonicalMuscle.OTHER),
        )
    }

    @Test
    fun muscleRowLeadsWithTheStillNotAHeatBar() {
        val bodyMap = readOwned("ui/progress/BodyMap.kt")
        assertTrue(bodyMap.contains("MuscleStill(muscle = load.muscle)"))
        assertTrue(!bodyMap.contains("HEAT_SWATCH"))
        val artwork = readOwned("ui/components/MuscleArtwork.kt")
        assertTrue(artwork.contains("fun MuscleStill("))
        assertTrue(artwork.contains("fun muscleArtwork("))
    }

    @Test
    fun mappedStillsExistOnDisk() {
        CanonicalMuscle.bodyMapOrder.forEach { muscle ->
            val file = stillFile("muscle_${muscle.name.lowercase()}.webp")
            assertTrue("${file.path} missing", file.isFile)
            assertTrue("${file.path} is empty", file.length() > 0)
        }
    }

    private fun readOwned(relative: String): String {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer"),
            File("../app/src/main/java/com/sinura/personaltrainer"),
        )
        return roots.map { File(it, relative) }.first { it.isFile }.readText()
    }

    private fun stillFile(name: String): File {
        val roots = listOf(
            File("app/src/main/res/drawable-nodpi"),
            File("../app/src/main/res/drawable-nodpi"),
        )
        return roots.map { File(it, name) }.first { it.isFile }
    }
}
