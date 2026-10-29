package com.sinura.personaltrainer.ui.workout

import com.sinura.personaltrainer.domain.EffortSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveWorkoutEffortUiTest {
    @Test
    fun prefillLastMapsToDottedChipValue() {
        val state = ActiveWorkoutUiState(effortValue = 8, effortSource = EffortSource.PREFILL_LAST)
        assertEquals(8, state.effortChipPrefilled)
        assertNull(state.effortChipSelected)
    }

    @Test
    fun userChoiceMapsToSelectedChipOnly() {
        val state = ActiveWorkoutUiState(effortValue = 9, effortSource = EffortSource.USER)
        assertEquals(9, state.effortChipSelected)
        assertNull(state.effortChipPrefilled)
    }
}
