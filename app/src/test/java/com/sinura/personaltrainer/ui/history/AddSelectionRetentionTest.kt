package com.sinura.personaltrainer.ui.history

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddSelectionRetentionTest {
    @Test fun aFailedOrLoadingReadDoesNotDropTheOpenAdd() {
        assertTrue(keepAddSelection("hold", null, "session", liftPresent = false, loading = true, failed = false, savePending = false))
        assertTrue(keepAddSelection("hold", "session", "session", liftPresent = true, loading = false, failed = true, savePending = false))
        assertTrue(keepAddSelection("hold", null, "session", liftPresent = false, loading = false, failed = false, savePending = false))
    }

    @Test fun aSuccessfulSessionWithoutTheLiftDropsTheOpenAdd() {
        assertFalse(keepAddSelection("hold", "session", "session", liftPresent = false, loading = false, failed = false, savePending = false))
        assertFalse(keepAddSelection("hold", "other", "session", liftPresent = true, loading = false, failed = false, savePending = false))
    }

    @Test fun theSameLiftOnTheSameSessionKeepsTheOpenAdd() {
        assertTrue(keepAddSelection("hold", "session", "session", liftPresent = true, loading = false, failed = false, savePending = false))
        assertTrue(keepAddSelection(null, "session", "session", liftPresent = false, loading = false, failed = false, savePending = false))
    }
}
