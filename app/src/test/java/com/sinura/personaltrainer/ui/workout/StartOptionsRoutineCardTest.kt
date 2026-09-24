package com.sinura.personaltrainer.ui.workout

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S-02: start-sheet routine cards picture the first three lifts and name the kit. They are not
 * a grouped text list.
 *
 * The card is rendered from a seeded routine in StartOptionsRoutineCardRenderTest (audit
 * T1c-2): its name, its planned sets, its kit mix and its lifts numbered in order, the first
 * three pictured and no more, and one tap that starts it. The ban stays here.
 */
class StartOptionsRoutineCardTest {
    @Test
    fun aRoutineIsACardNotAnInstrumentRow() {
        val routineRow = sourceBetween(
            ownedSource("ui/workout/StartOptionsSheet.kt"),
            "private fun RoutineRow",
            "\n@Composable\nprivate fun LogAndCardioActions",
        )
        assertFalse(routineRow.contains("InstrumentRow("))
    }
}
