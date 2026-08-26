package com.sinura.personaltrainer.ui.activity

import com.sinura.personaltrainer.domain.ComposerCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivityComposerChromeTest {
    @Test
    fun saveAndAddTagsStayStable() {
        assertEquals("activity-composer-save", ComposerTags.SAVE)
        assertEquals("activity-composer-cancel", ComposerTags.CANCEL)
        assertEquals("activity-composer-add-set", ComposerTags.ADD_SET)
        assertEquals("activity-composer-add-cardio", ComposerTags.ADD_CARDIO)
        assertEquals("activity-composer-choose-lift", ComposerTags.CHOOSE_LIFT)
        assertEquals("activity-composer-remove-strength", ComposerTags.REMOVE_STRENGTH)
        assertEquals("activity-composer-remove-cardio", ComposerTags.REMOVE_CARDIO)
    }

    @Test
    fun addActsAreNotTheVolt() {
        assertEquals(ComposerCopy.SAVE, ComposerCopy.VOLT)
        assertNotEquals(ComposerTags.SAVE, ComposerTags.ADD_SET)
        assertNotEquals(ComposerTags.SAVE, ComposerTags.ADD_CARDIO)
        assertNotEquals(ComposerTags.SAVE, ComposerTags.CHOOSE_LIFT)
        assertNotEquals(ComposerTags.REMOVE_STRENGTH, ComposerTags.SAVE)
    }
}
