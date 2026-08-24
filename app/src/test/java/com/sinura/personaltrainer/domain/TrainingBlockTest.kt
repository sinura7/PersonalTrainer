package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sinura.personaltrainer.domain.Weekday
import java.time.LocalDate

class TrainingBlockTest {
    // 2026-08-19 is a Wednesday; the Monday of its week is 2026-08-17.
    private val wednesday = LocalDate.of(2026, 8, 19)
    private val monday = LocalDate.of(2026, 8, 17)

    @Test
    fun aBlockStartsAtTheTopOfTheWeekYouSetItUpIn() {
        // Not on the day itself: week one would be the four days left of a week already half
        // spent, and the block's weeks would stop lining up with every other week in the app.
        val block = TrainingBlock.startingIn(wednesday, Weekday.MONDAY)
        assertEquals(monday.toEpochDay(), block.startEpochDay)
        assertEquals(1, block.weekIndexOn(wednesday.toEpochDay()))
        assertEquals(1, block.weekIndexOn(monday.toEpochDay()))
    }

    @Test
    fun theWeekStartIsTheLiftersOwn() {
        val sundayBlock = TrainingBlock.startingIn(wednesday, Weekday.SUNDAY)
        assertEquals(LocalDate.of(2026, 8, 16).toEpochDay(), sundayBlock.startEpochDay)
    }

    @Test
    fun weeksCountFromOne() {
        val block = TrainingBlock.startingIn(monday, Weekday.MONDAY)
        assertEquals(1, block.weekIndexOn(monday.toEpochDay()))
        assertEquals(1, block.weekIndexOn(monday.plusDays(6).toEpochDay()))
        assertEquals(2, block.weekIndexOn(monday.plusDays(7).toEpochDay()))
        assertEquals(12, block.weekIndexOn(monday.plusWeeks(11).toEpochDay()))
    }

    @Test
    fun aDateBeforeTheBlockBelongsToNoWeekOfIt() {
        // History predating the block is still history. It is week zero, not week one.
        val block = TrainingBlock.startingIn(monday, Weekday.MONDAY)
        assertEquals(0, block.weekIndexOn(monday.minusDays(1).toEpochDay()))
    }

    @Test
    fun theBlockEndsAfterItsLastWeekNotDuringIt() {
        val block = TrainingBlock.startingIn(monday, Weekday.MONDAY)
        val lastDay = monday.plusWeeks(12).minusDays(1)
        assertFalse(block.isCompleteOn(lastDay.toEpochDay()))
        assertEquals(12, block.weekIndexOn(lastDay.toEpochDay()))
        assertTrue(block.isCompleteOn(monday.plusWeeks(12).toEpochDay()))
    }

    @Test
    fun aFinishedBlockNeverReadsAsWeekThirteenOfTwelve() {
        // The same arithmetic failure as "Set 6 of 5", and just as visible.
        val block = TrainingBlock.startingIn(monday, Weekday.MONDAY)
        assertEquals(13, block.weekIndexOn(monday.plusWeeks(12).toEpochDay()))
        assertEquals(12, block.displayWeekOn(monday.plusWeeks(12).toEpochDay()))
        assertEquals(12, block.displayWeekOn(monday.plusWeeks(40).toEpochDay()))
    }

    @Test
    fun progressIsMeasuredInWholeWeeks() {
        // Not days: the app plans in weeks, and a bar creeping forward every morning would
        // imply a precision the plan does not have.
        val block = TrainingBlock.startingIn(monday, Weekday.MONDAY)
        assertEquals(1f / 12f, block.progressOn(monday.toEpochDay()), 0.0001f)
        assertEquals(1f / 12f, block.progressOn(monday.plusDays(6).toEpochDay()), 0.0001f)
        assertEquals(2f / 12f, block.progressOn(monday.plusDays(7).toEpochDay()), 0.0001f)
        assertEquals(1f, block.progressOn(monday.plusWeeks(11).toEpochDay()), 0.0001f)
        assertEquals(1f, block.progressOn(monday.plusWeeks(50).toEpochDay()), 0.0001f)
    }

    @Test
    fun timeOffDoesNotPauseTheBlock() {
        // Anchored to a date, not counted up as sessions land. Twelve weeks is twelve weeks
        // whether or not you trained through them — a counter that waits for you cannot tell
        // you that you stopped.
        val block = TrainingBlock.startingIn(monday, Weekday.MONDAY)
        assertEquals(5, block.weekIndexOn(monday.plusWeeks(4).toEpochDay()))
    }

    @Test
    fun aBlockLengthIsClampedToSomethingTrainable() {
        assertEquals(TrainingBlock.MIN_WEEKS, TrainingBlock.startingIn(monday, Weekday.MONDAY, weeks = 1).weeks)
        assertEquals(TrainingBlock.MAX_WEEKS, TrainingBlock.startingIn(monday, Weekday.MONDAY, weeks = 99).weeks)
        assertEquals(12, TrainingBlock.startingIn(monday, Weekday.MONDAY).weeks)
    }
}
