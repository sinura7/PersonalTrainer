package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ActivityDetailCopyTest {
    @Test
    fun missingCopyMatchesSessionDetailAndCelebrationUsesDone() {
        assertEquals("Session not found", ActivityDetailCopy.MISSING_TITLE)
        assertEquals("Back", ActivityDetailCopy.missingAction(celebration = false))
        assertEquals("Done", ActivityDetailCopy.missingAction(celebration = true))
        assertEquals(ActivityDetailCopy.DONE, ActivityDetailCopy.VOLT)
    }

    @Test
    fun celebrationKickerNamesTheModality() {
        val run = cardioSession()
        val lift = strengthSession()
        assertEquals("Cardio complete", ActivityDetailCopy.kicker(true, run))
        assertEquals("Workout complete", ActivityDetailCopy.kicker(true, lift))
        assertEquals("Run, ride, or walk.", ActivityDetailCopy.kicker(false, run))
        assertEquals("Strength.", ActivityDetailCopy.kicker(false, lift))
    }

    @Test
    fun strengthSetLineUsesTheDisplayUnit() {
        val block = (strengthSession().blocks.first() as StrengthBlock)
        val set = block.sets.first()
        assertEquals("80 kg × 5", ActivityDetailCopy.setLine(block, set, WeightUnit.KG))
        assertEquals("176.5 lbs × 5", ActivityDetailCopy.setLine(block, set, WeightUnit.LBS))
        assertFalse(ActivityDetailCopy.setLine(block, set, WeightUnit.LBS).contains("weightKg"))
    }

    @Test
    fun noonStampIsNotAZeroMinuteWorkoutTile() {
        val noon = strengthSession()
        assertEquals(0, noon.cardioMinutes())
        assertEquals(noon.performedStart.instantMillis, noon.performedEnd?.instantMillis)
        assertEquals(0, ActivityDetailCopy.receiptDurationMinutes(noon, cardioMinutes = 0))
    }

    @Test
    fun cardioMinutesWinOverACollapsedPerformedSpan() {
        val run = cardioSession()
        val collapsed = run.copy(performedEnd = run.performedStart)
        assertEquals(40, ActivityDetailCopy.receiptDurationMinutes(collapsed, cardioMinutes = 40))
    }

    @Test
    fun performedSpanRoundsToMinutesWhenThereIsNoCardioClock() {
        val lift = strengthSession().copy(
            performedEnd = CapturedCivilTime(2_000L + 1_500_000L, "UTC", 0, 20_001L),
        )
        assertEquals(25, ActivityDetailCopy.receiptDurationMinutes(lift, cardioMinutes = 0))
    }

    @Test
    fun cardioSubtitleUsesGymNamesNotSchemaEnums() {
        val block = cardioSession().cardioBlocks.first()
        assertEquals("Run", CardioCopy.name(block.type))
        assertNotEquals("RUN", CardioCopy.name(block.type))
        assertEquals("40 min · 6.0 km", ActivityDetailCopy.cardioSubtitle(block))
    }

    private fun cardioSession() = ActivitySession(
        id = "a1",
        status = ActivityStatus.COMPLETED,
        origin = ActivityOrigin.LIVE,
        source = ActivitySource.TEMPER,
        title = "Easy run",
        notes = "",
        performedStart = CapturedCivilTime(1_000L, "UTC", 0, 20_000L),
        performedEnd = CapturedCivilTime(1_000L + 2_400_000L, "UTC", 0, 20_000L),
        templateId = null,
        occurrenceId = null,
        blocks = listOf(
            CardioBlock(
                id = "c1",
                sortOrder = 0,
                type = CardioType.RUN,
                indoor = false,
                elapsedSeconds = 2_400L,
                movingSeconds = 2_400L,
                distanceMeters = 6_000.0,
                elevationMeters = null,
                heartRateBpm = null,
                energyKj = null,
                rpe = null,
                routeRef = null,
            ),
        ),
        createdAtMs = 1L,
        updatedAtMs = 1L,
        revision = 1L,
    )

    private fun strengthSession() = ActivitySession(
        id = "a2",
        status = ActivityStatus.COMPLETED,
        origin = ActivityOrigin.BACKDATED,
        source = ActivitySource.TEMPER,
        title = "Squat day",
        notes = "",
        performedStart = CapturedCivilTime(2_000L, "UTC", 0, 20_001L),
        performedEnd = CapturedCivilTime(2_000L, "UTC", 0, 20_001L),
        templateId = null,
        occurrenceId = null,
        blocks = listOf(
            StrengthBlock(
                id = "s1",
                sortOrder = 0,
                exerciseId = "ex-1",
                exerciseName = "Squat",
                loadType = LoadType.EXTERNAL,
                equipment = EquipmentType.BARBELL,
                muscles = emptyList(),
                sets = listOf(
                    StrengthSet(
                        id = "set-1",
                        setNumber = 1,
                        weightKg = 80.0,
                        reps = 5,
                        rpe = null,
                        isWarmup = false,
                        completedAtMs = 2_000L,
                    ),
                ),
            ),
        ),
        createdAtMs = 1L,
        updatedAtMs = 1L,
        revision = 1L,
    )
}
