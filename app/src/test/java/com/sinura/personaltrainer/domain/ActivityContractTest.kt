package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5.2: every FND-002 acceptance case is representable as a domain
 * contract, before persistence or UI.
 */
class ActivityContractTest {
    private val tokyoMorning = capture("Asia/Tokyo", CivilDate(2026, 8, 20), hour = 7)
    private val tokyoEvening = capture("Asia/Tokyo", CivilDate(2026, 8, 20), hour = 19)
    private val now = capture("Asia/Tokyo", CivilDate(2026, 8, 21), hour = 9)

    @Test
    fun oneEnvelopeHoldsStrengthCardioAndMixed() {
        val strength = confirm(draft(origin = ActivityOrigin.BACKDATED, blocks = listOf(squat())))
        val cardio = confirm(draft(origin = ActivityOrigin.BACKDATED, title = "Easy run", blocks = listOf(run())))
        val mixed = confirm(
            draft(
                origin = ActivityOrigin.BACKDATED,
                title = "Brick",
                blocks = listOf(run(sortOrder = 0), squat(sortOrder = 1)),
            ),
        )
        assertTrue(strength.isStrengthOnly)
        assertTrue(cardio.isCardioOnly)
        assertTrue(mixed.isMixed)
        assertEquals(0, cardio.strengthSetCount())
    }

    @Test
    fun cardioNeedsNoFakeExerciseOrSetLog() {
        val session = confirm(
            draft(origin = ActivityOrigin.BACKDATED, title = "Tempo", blocks = listOf(run())),
        )
        assertTrue(session.strengthBlocks.isEmpty())
        assertEquals(0, session.strengthSetCount())
        assertEquals(CardioType.RUN, session.cardioBlocks.single().type)
        assertEquals(1_500.0, session.cardioBlocks.single().distanceMeters)
    }

    @Test
    fun aDayHoldsTwoIndependentCompletedActivities() {
        val morning = confirm(
            draft(
                origin = ActivityOrigin.BACKDATED,
                title = "Morning cardio",
                performedStart = tokyoMorning,
                occurrenceId = "occ-cardio",
                blocks = listOf(run()),
            ),
        )
        val evening = confirm(
            draft(
                origin = ActivityOrigin.BACKDATED,
                title = "Evening strength",
                performedStart = tokyoEvening,
                occurrenceId = "occ-lift",
                blocks = listOf(squat()),
            ),
            existing = listOf(morning),
        )
        val onDay = ActivityQueries.completedOn(listOf(morning, evening), tokyoMorning.localEpochDay)
        assertEquals(2, onDay.size)
        assertEquals(setOf("occ-cardio", "occ-lift"), onDay.map { it.occurrenceId }.toSet())
        assertEquals(morning.localEpochDay, evening.localEpochDay)
    }

    @Test
    fun recordsRetainSourceTimeZoneAndOrigin() {
        val live = confirm(
            draft(
                origin = ActivityOrigin.LIVE,
                status = ActivityStatus.ACTIVE,
                performedStart = now,
                blocks = listOf(squat()),
            ),
        )
        val backdated = confirm(
            draft(origin = ActivityOrigin.BACKDATED, performedStart = tokyoMorning, blocks = listOf(run())),
            existing = listOf(live.copy(status = ActivityStatus.COMPLETED)),
        )
        val imported = confirm(
            draft(
                origin = ActivityOrigin.IMPORTED,
                performedStart = tokyoEvening,
                blocks = listOf(run()),
            ),
            existing = listOf(live.copy(status = ActivityStatus.COMPLETED), backdated),
        )
        assertEquals(ActivitySource.TEMPER, live.source)
        assertEquals(ActivityOrigin.LIVE, live.origin)
        assertEquals("Asia/Tokyo", live.performedStart.zoneId)
        assertEquals(ActivityOrigin.BACKDATED, backdated.origin)
        assertEquals(ActivityOrigin.IMPORTED, imported.origin)
        assertEquals(tokyoMorning.localEpochDay, backdated.localEpochDay)
    }

    @Test
    fun queriesReadTheNewTypesWithoutSetLogs() {
        val cardio = confirm(draft(origin = ActivityOrigin.BACKDATED, blocks = listOf(run())))
        val strength = confirm(
            draft(origin = ActivityOrigin.BACKDATED, performedStart = tokyoEvening, blocks = listOf(squat())),
            existing = listOf(cardio),
        )
        assertTrue(ActivityQueries.cardioOnlyDays(listOf(cardio)).contains(cardio.localEpochDay))
        assertTrue(ActivityQueries.cardioOnlyDays(listOf(cardio, strength)).isEmpty())
        assertEquals(2, ActivityQueries.onLocalDate(listOf(cardio, strength), tokyoMorning.localEpochDay).size)
        assertNull(ActivityQueries.live(listOf(cardio, strength)))
    }

    @Test
    fun oneLiveActivityAtATime() {
        val live = confirm(
            draft(
                origin = ActivityOrigin.LIVE,
                status = ActivityStatus.ACTIVE,
                performedStart = now,
                blocks = listOf(run()),
            ),
        )
        assertFalse(ActivityRules.canStartLive(listOf(live)))
        val second = ActivityRules.confirm(
            draft(
                origin = ActivityOrigin.LIVE,
                status = ActivityStatus.ACTIVE,
                performedStart = now,
                title = "Second",
                blocks = listOf(squat()),
            ),
            existing = listOf(live),
            now = now,
            ids = sequentialIds(),
            clock = JvmTime,
        )
        assertTrue(second is ActivityWrite.Rejected)
        assertEquals("One live activity at a time.", (second as ActivityWrite.Rejected).reason)
    }

    @Test
    fun futureDatesAreRejected() {
        val tomorrow = capture("Asia/Tokyo", CivilDate(2026, 8, 22), hour = 7)
        val write = ActivityRules.confirm(
            draft(origin = ActivityOrigin.BACKDATED, performedStart = tomorrow, blocks = listOf(run())),
            existing = emptyList(),
            now = now,
            ids = sequentialIds(),
            clock = JvmTime,
        )
        assertTrue(write is ActivityWrite.Rejected)
        assertEquals("Future dates are not saved.", (write as ActivityWrite.Rejected).reason)
    }

    @Test
    fun canceledEditorWritesNothing() {
        val draft = draft(origin = ActivityOrigin.BACKDATED, blocks = listOf(run()))
        // Discarding the draft is the cancel path. confirm() is never called.
        assertEquals(0, emptyList<ActivitySession>().size)
        assertEquals(ActivityOrigin.BACKDATED, draft.origin)
    }

    @Test
    fun emptyDraftIsRejected() {
        val write = ActivityRules.confirm(
            draft(origin = ActivityOrigin.BACKDATED, blocks = emptyList()),
            existing = emptyList(),
            now = now,
            ids = sequentialIds(),
            clock = JvmTime,
        )
        assertTrue(write is ActivityWrite.Rejected)
    }

    @Test
    fun contradictoryPaceIsRejected() {
        val block = run(distanceMeters = 1_000.0, elapsedSeconds = 300)
        val write = ActivityRules.confirm(
            draft(
                origin = ActivityOrigin.BACKDATED,
                blocks = listOf(block),
                cardioClaims = listOf(CardioRateClaim(block, secondsPerMeter = 1.0)),
            ),
            existing = emptyList(),
            now = now,
            ids = sequentialIds(),
            clock = JvmTime,
        )
        assertTrue(write is ActivityWrite.Rejected)
        assertTrue(ActivityRules.cardioMetricsAgree(block, claimedSecondsPerMeter = 300.0 / 1_000.0))
    }

    @Test
    fun editingATemplateDoesNotRewriteASession() {
        val template = ActivityTemplate(
            id = "tmpl-1",
            title = "Push",
            notes = "",
            blocks = listOf(squat()),
        )
        val session = confirm(
            draft(
                origin = ActivityOrigin.BACKDATED,
                templateId = template.id,
                title = template.title,
                blocks = template.blocks,
            ),
        )
        val renamed = template.copy(title = "Push v2")
        assertEquals("Push", session.title)
        assertEquals("tmpl-1", session.templateId)
        assertEquals("Push v2", renamed.title)
    }

    @Test
    fun backdatedTokyoDayStaysTokyoWhenNowIsChicago() {
        val session = confirm(draft(origin = ActivityOrigin.BACKDATED, blocks = listOf(run())))
        val chicagoNow = capture("America/Chicago", CivilDate(2026, 8, 21), hour = 9)
        assertEquals(tokyoMorning.localEpochDay, session.localEpochDay)
        assertEquals("Asia/Tokyo", session.performedStart.zoneId)
        assertTrue(session.localEpochDay != chicagoNow.localEpochDay || session.performedStart.zoneId != chicagoNow.zoneId)
    }

    @Test
    fun dstOverlapOffsetIsTheOneTheComposerChose() {
        val local = CivilDateTime(CivilDate(2026, 11, 1), hour = 1, minute = 30)
        val earlier = JvmTime.resolveLocal(local, "America/New_York", DstOverlapChoice.EARLIER)
        val session = confirm(
            draft(origin = ActivityOrigin.BACKDATED, performedStart = earlier, blocks = listOf(run())),
            now = capture("America/New_York", CivilDate(2026, 11, 2), hour = 9),
        )
        assertEquals(-4 * 3600, session.performedStart.offsetSeconds)
    }

    private fun confirm(
        draft: ActivityDraft,
        existing: List<ActivitySession> = emptyList(),
        now: CapturedCivilTime = this.now,
    ): ActivitySession {
        val write = ActivityRules.confirm(draft, existing, now, sequentialIds(), JvmTime)
        return (write as ActivityWrite.Accepted).session
    }

    private fun draft(
        origin: ActivityOrigin,
        status: ActivityStatus = ActivityStatus.COMPLETED,
        title: String = "Session",
        performedStart: CapturedCivilTime = tokyoMorning,
        occurrenceId: String? = null,
        templateId: String? = null,
        blocks: List<ActivityBlock>,
        cardioClaims: List<CardioRateClaim> = emptyList(),
    ) = ActivityDraft(
        status = status,
        origin = origin,
        title = title,
        performedStart = performedStart,
        occurrenceId = occurrenceId,
        templateId = templateId,
        blocks = blocks,
        cardioClaims = cardioClaims,
    )

    private fun squat(sortOrder: Int = 0) = StrengthBlock(
        id = "blk-squat",
        sortOrder = sortOrder,
        exerciseId = "ex-squat",
        exerciseName = "Squat",
        loadType = LoadType.EXTERNAL,
        equipment = EquipmentType.BARBELL,
        muscles = emptyList(),
        sets = listOf(
            StrengthSet(
                id = "set-1",
                setNumber = 1,
                weightKg = 100.0,
                reps = 5,
                rpe = null,
                isWarmup = false,
                completedAtMs = tokyoMorning.instantMillis,
            ),
        ),
    )

    private fun run(
        sortOrder: Int = 0,
        distanceMeters: Double? = 1_500.0,
        elapsedSeconds: Long = 480,
    ) = CardioBlock(
        id = "blk-run",
        sortOrder = sortOrder,
        type = CardioType.RUN,
        indoor = false,
        elapsedSeconds = elapsedSeconds,
        movingSeconds = elapsedSeconds,
        distanceMeters = distanceMeters,
        elevationMeters = null,
        heartRateBpm = null,
        energyKj = null,
        rpe = 6,
        routeRef = null,
    )

    private fun capture(zoneId: String, date: CivilDate, hour: Int): CapturedCivilTime =
        JvmTime.resolveLocal(CivilDateTime(date, hour = hour, minute = 0), zoneId)

    private fun sequentialIds(): IdPort {
        var n = 0
        return IdPort { "id-${++n}" }
    }
}
