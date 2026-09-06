package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.ui.library.LibraryTags
import com.sinura.personaltrainer.ui.navigation.LiveBarCopy
import com.sinura.personaltrainer.ui.navigation.LiveBarKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * F5: the gym floor says lift, and returning to a live session has one verb.
 */
class GymFloorCopyTest {
    @Test
    fun resumeLabelHasOneSource() {
        val labels = LiveBarKind.entries.map(LiveBarCopy::resumeLabel).toSet()
        assertEquals(setOf(LiveBarCopy.RESUME), labels)
        assertEquals("Go to session", LiveBarCopy.RESUME)
    }

    @Test
    fun userFacingCopyOutsideTheCatalogDoesNotSayExercise() {
        val spoken = listOf(
            AccessibilityMatrix.page("library").voltAction,
            LibraryTags.SEARCH_SPOKEN,
            ActivityDetailCopy.CARDIO_CAPTION,
            ActivityDetailCopy.MIXED_CAPTION,
            ActivityDetailCopy.STRENGTH_CAPTION,
            TrainingFocus.CARDIO.blurb,
            TrainingFocus.STRENGTH.blurb,
            TrainingFocus.BOTH.blurb,
            LiveBarCopy.RESUME,
        ) + LiveBarKind.entries.flatMap { kind ->
            listOf(
                LiveBarCopy.resumeLabel(kind),
                LiveBarCopy.discard(kind),
                LiveBarCopy.discardTitle(kind),
                LiveBarCopy.finish(kind),
            )
        }
        spoken.forEach { line ->
            assertFalse(line, EXERCISE_WORD.containsMatchIn(line))
        }
        assertEquals("Create lift", AccessibilityMatrix.page("library").voltAction)
        assertFalse(ActivityDetailCopy.CARDIO_CAPTION.contains("invented", ignoreCase = true))
        assertFalse(ActivityDetailCopy.MIXED_CAPTION.contains("invented", ignoreCase = true))
        libraryQuotedStrings().forEach { (file, text) ->
            assertFalse("$file: $text", EXERCISE_WORD.containsMatchIn(text))
        }
    }

    private fun libraryQuotedStrings(): List<Pair<String, String>> {
        val roots = listOf(
            File("app/src/main/java/com/sinura/personaltrainer/ui/library"),
            File("app/src/debug/java/com/sinura/personaltrainer/ui/library"),
            File("../app/src/main/java/com/sinura/personaltrainer/ui/library"),
            File("../app/src/debug/java/com/sinura/personaltrainer/ui/library"),
        ).filter { it.isDirectory }
        return roots.flatMap { dir ->
            dir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
                QUOTE.findAll(file.readText()).map { match ->
                    file.name to INTERPOLATION.replace(match.groupValues[1], " ")
                }
            }
        }
    }

    private companion object {
        val EXERCISE_WORD = Regex("""(?i)\bexercises?\b""")
        val QUOTE = Regex(""""((?:\\.|[^"\\])*)"""")
        val INTERPOLATION = Regex("""\$\{[^}]+\}""")
    }
}
