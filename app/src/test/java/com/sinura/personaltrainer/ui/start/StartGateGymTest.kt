package com.sinura.personaltrainer.ui.start

import com.sinura.personaltrainer.testutil.TestGymIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartGateGymTest {
  private val gate = RecordingStartGate()

    @Test
    fun strengthStartWithoutGymIsRefused() {
        val admitted = gate.requestStart(StartCommand.Free(gymId = null))
        assertFalse(admitted)
        assertTrue(gate.pendingGymCommand.value is StartCommand.Free)
    }

    @Test
    fun strengthStartWithGymIsAdmitted() {
        assertTrue(gate.requestStart(StartCommand.Free(gymId = TestGymIds.WESTON)))
    }

    @Test
    fun cardioStartSkipsGym() {
        assertTrue(gate.requestStart(StartCommand.Cardio(com.sinura.personaltrainer.domain.CardioType.RUN)))
    }
}
