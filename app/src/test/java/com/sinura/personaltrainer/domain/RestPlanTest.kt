package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RestPlanTest {
    @Test
    fun precedenceTempoOverRoutineLastAndDefault() {
        val prefs = RestTimerPreferences(
            defaultRestSeconds = 90,
            lastPresetSeconds = 75,
        )
        assertEquals(
            RestPlanSource.TEMPO,
            RestPlanResolver.resolve(
                exerciseRestSeconds = 120,
                preferences = prefs,
                prescribedSeconds = 150,
                tempoRepsForLabel = 5,
            ).source,
        )
        assertEquals(
            "Tempo · heavy set",
            RestPlanResolver.resolve(
                exerciseRestSeconds = 120,
                preferences = prefs,
                prescribedSeconds = 150,
                tempoRepsForLabel = 5,
            ).sourceTag(),
        )
        assertEquals(
            RestPlanSource.ROUTINE,
            RestPlanResolver.resolve(
                exerciseRestSeconds = 120,
                preferences = prefs,
                prescribedSeconds = null,
            ).source,
        )
        assertEquals(
            RestPlanSource.LAST_PRESET,
            RestPlanResolver.resolve(
                exerciseRestSeconds = null,
                preferences = prefs,
                prescribedSeconds = null,
            ).source,
        )
        assertEquals(
            RestPlanSource.DEFAULT,
            RestPlanResolver.resolve(
                exerciseRestSeconds = null,
                preferences = prefs.copy(lastPresetSeconds = null),
                prescribedSeconds = null,
            ).source,
        )
    }
}
