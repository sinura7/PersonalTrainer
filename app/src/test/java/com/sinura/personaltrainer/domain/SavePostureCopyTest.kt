package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SavePostureCopyTest {
    @Test
    fun settingsSummariesMatchPosture() {
        assertEquals(
            SavePostureCopy.SETTINGS_SUMMARY_ACCOUNT,
            SavePostureCopy.settingsSummary(SavePosture.ACCOUNT),
        )
        assertEquals(
            SavePostureCopy.SETTINGS_SUMMARY_LOCAL,
            SavePostureCopy.settingsSummary(SavePosture.LOCAL),
        )
    }
}
