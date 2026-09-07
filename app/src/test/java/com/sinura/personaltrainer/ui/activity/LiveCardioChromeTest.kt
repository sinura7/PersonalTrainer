package com.sinura.personaltrainer.ui.activity

import com.sinura.personaltrainer.ui.components.LeaveCardioTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LiveCardioChromeTest {
    @Test
    fun finishAndDoneTagsStayStable() {
        assertEquals("live-cardio-finish", CardioTags.FINISH)
        assertEquals("live-cardio-elapsed", CardioTags.ELAPSED)
        assertEquals("live-cardio-leave", CardioTags.LEAVE)
        assertEquals("live-cardio-discard", CardioTags.DISCARD)
        assertEquals("activity-detail-done", ActivityDetailTags.DONE)
        assertEquals("activity-detail-back", ActivityDetailTags.BACK)
    }

    @Test
    fun retryTagsNameTheReadFaultAction() {
        assertEquals("live-cardio-retry", CardioTags.RETRY)
        assertEquals("activity-detail-retry", ActivityDetailTags.RETRY)
        assertNotEquals(CardioTags.RETRY, ActivityDetailTags.RETRY)
    }

    @Test
    fun leaveDialogTagsNameStackedButtons() {
        assertEquals("leave-cardio-leave-running", LeaveCardioTags.LEAVE_RUNNING)
        assertEquals("leave-cardio-stay", LeaveCardioTags.STAY)
        assertEquals("leave-cardio-discard", LeaveCardioTags.DISCARD)
        assertNotEquals(LeaveCardioTags.LEAVE_RUNNING, CardioTags.FINISH)
        assertNotEquals(LeaveCardioTags.DISCARD, CardioTags.DISCARD)
    }
}
