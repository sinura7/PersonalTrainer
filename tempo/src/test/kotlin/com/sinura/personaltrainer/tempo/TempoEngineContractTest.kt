package com.sinura.personaltrainer.tempo

import com.sinura.personaltrainer.domain.CoachPreferences
import com.sinura.personaltrainer.domain.coach.TempoCoach
import org.junit.Assert.assertEquals
import org.junit.Test

class TempoEngineContractTest {
    @Test
    fun recommendDelegatesToTempoCoach() {
        val result = TempoEngine.recommend(
            microRec = null,
            prefs = CoachPreferences.DEFAULT,
            addASet = null,
            extraSetRec = null,
        )
        assertEquals(
            TempoCoach.resolve(null, CoachPreferences.DEFAULT, null, null),
            result,
        )
    }
}
