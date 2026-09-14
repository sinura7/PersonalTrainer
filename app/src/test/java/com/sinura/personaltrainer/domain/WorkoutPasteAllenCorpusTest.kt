package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Allen's numbered Upper / Lower paste must fill four sessions: names,
 * sets, rep ranges, per-leg / per-side, and timed holds. Failures quote
 * the written line.
 */
class WorkoutPasteAllenCorpusTest {
    private val catalog = WorkoutPaste.catalogExercises()
    private val plan by lazy {
        val text = checkNotNull(
            javaClass.getResource("/allen-upper-lower-paste.md"),
        ) { "allen-upper-lower-paste.md missing from test resources" }.readText()
        WorkoutPaste.parseAndMatch(text, catalog)
    }

    @Test
    fun theFourStrengthBlocksBecomeSessions() {
        assertEquals(
            listOf("Upper A", "Lower A", "Upper B", "Lower B"),
            plan.strengthSessions().map { it.name },
        )
        assertEquals("strength", plan.sessionNamed("Upper A")?.emphasis)
        assertEquals("muscle", plan.sessionNamed("Upper B")?.emphasis)
        assertTrue(plan.sessions.none { it.kind == PastedSessionKind.CARDIO })
        assertTrue(plan.sessions.none { it.kind == PastedSessionKind.FLEXIBILITY })
    }

    @Test
    fun upperAImportsEveryWrittenLift() {
        val upper = plan.sessionNamed("Upper A")!!
        assertEquals(
            listOf(
                "ex-barbell-bench-press",
                "ex-pull-up",
                "ex-overhead-press",
                "ex-chest-supported-dumbbell-row",
                "ex-lateral-raise",
                "ex-hanging-leg-raise",
                "ex-dead-hang",
            ),
            upper.lifts.map { it.exercise.id },
        )
        val bench = upper.lift("ex-barbell-bench-press")
        assertEquals(4, bench.targetSets)
        assertEquals(4, bench.scheme.repsMin)
        assertEquals(6, bench.scheme.repsMax)
        assertEquals("ex-lat-pulldown", upper.lifts[1].alternative?.id)
        val hang = upper.lift("ex-dead-hang")
        assertTimedHold(hang, 20, 40)
        assertEquals(2, hang.targetSets)
        assertTrue("Upper A unmatched=${upper.unmatched}", upper.unmatched.isEmpty())
    }

    @Test
    fun lowerALungesArePerLegAndWallSitIsAHold() {
        val lower = plan.sessionNamed("Lower A")!!
        assertEquals(
            listOf(
                "ex-barbell-back-squat",
                "ex-romanian-deadlift",
                "ex-walking-lunge",
                "ex-leg-curl",
                "ex-standing-calf-raise",
                "ex-cable-crunch",
                "ex-wall-sit",
            ),
            lower.lifts.map { it.exercise.id },
        )
        val lunges = lower.lift("ex-walking-lunge")
        assertEquals(3, lunges.targetSets)
        assertEquals(8, lunges.targetReps)
        assertTrue(lunges.scheme.perSide)
        assertEquals("ex-plank", lower.lift("ex-cable-crunch").alternative?.id)
        assertFalse(lower.lift("ex-cable-crunch").scheme.isTimed)
        assertTimedHold(lower.lift("ex-wall-sit"), 30, 45)
        assertTrue("Lower A unmatched=${lower.unmatched}", lower.unmatched.isEmpty())
    }

    @Test
    fun upperBSplitsCurlAndPushdown() {
        val upper = plan.sessionNamed("Upper B")!!
        assertEquals(
            listOf(
                "ex-incline-dumbbell-bench-press",
                "ex-seated-cable-row",
                "ex-seated-dumbbell-press",
                "ex-lat-pulldown",
                "ex-face-pull",
                "ex-barbell-curl",
                "ex-tricep-pushdown",
                "ex-ab-wheel-rollout",
                "ex-scapular-hang",
            ),
            upper.lifts.map { it.exercise.id },
        )
        assertEquals(2, upper.lift("ex-barbell-curl").targetSets)
        assertEquals(10, upper.lift("ex-barbell-curl").scheme.repsMin)
        assertEquals(15, upper.lift("ex-barbell-curl").scheme.repsMax)
        assertEquals(2, upper.lift("ex-tricep-pushdown").targetSets)
        assertEquals("ex-dead-bug", upper.lift("ex-ab-wheel-rollout").alternative?.id)
        val hang = upper.lift("ex-scapular-hang")
        assertEquals("ex-y-hold", hang.alternative?.id)
        assertTimedHold(hang, 20, 30)
        assertTrue("Upper B unmatched=${upper.unmatched}", upper.unmatched.isEmpty())
    }

    @Test
    fun lowerBPicksFirstOrAndTimesTheHolds() {
        val lower = plan.sessionNamed("Lower B")!!
        assertEquals(
            listOf(
                "ex-front-squat",
                "ex-hip-thrust",
                "ex-bulgarian-split-squat",
                "ex-leg-press",
                "ex-seated-calf-raise",
                "ex-side-plank",
                "ex-deep-squat-hold",
            ),
            lower.lifts.map { it.exercise.id },
        )
        assertEquals("ex-goblet-squat", lower.lift("ex-front-squat").alternative?.id)
        assertEquals("ex-barbell-glute-bridge", lower.lift("ex-hip-thrust").alternative?.id)
        assertEquals("ex-hack-squat", lower.lift("ex-leg-press").alternative?.id)
        assertTrue(lower.lift("ex-bulgarian-split-squat").scheme.perSide)
        val plank = lower.lift("ex-side-plank")
        assertEquals(2, plank.targetSets)
        assertEquals(3, plank.scheme.setsMax)
        assertTrue(plank.scheme.perSide)
        assertTimedHold(plank, 20, 40)
        assertTimedHold(lower.lift("ex-deep-squat-hold"), 30, 45)
        assertTrue("Lower B unmatched=${lower.unmatched}", lower.unmatched.isEmpty())
    }

    private fun PastedSession.lift(id: String): PastedLift =
        lifts.first { it.exercise.id == id }
}

class WorkoutPastePhoneLowerATest {
    private val catalog = WorkoutPaste.catalogExercises()
    private val blob = checkNotNull(
        javaClass.getResource("/allen-lower-a-phone-paste.txt"),
    ) { "allen-lower-a-phone-paste.txt missing from test resources" }.readText()

    @Test
    fun phoneBlobIsTheExactLive56String() {
        assertTrue(blob, blob.startsWith("Lower A (strength)**"))
        assertFalse(blob, blob.contains("##"))
        assertTrue(blob, blob.contains("—"))
        assertTrue(blob, blob.contains("×"))
        assertTrue(blob, blob.contains("**"))
    }

    @Test
    fun phoneLowerATrailingStarsUsedToBeZeroSessions() {
        // Live 56: STRENGTH_HEADER required (strength|muscle) at end of line.
        // "Lower A (strength)**" did not match, current stayed null, numbered
        // lifts were skipped, and the banner said NOTHING.
        val plan = WorkoutPaste.parseAndMatch(blob, catalog)
        assertEquals(listOf("Lower A"), plan.sessions.map { it.name })
        assertEquals("strength", plan.sessionNamed("Lower A")?.emphasis)
        assertEquals(null, WorkoutPaste.unreadableReason(blob, plan))
    }

    @Test
    fun phoneLowerAImportsEveryWrittenLift() {
        val lower = WorkoutPaste.parseAndMatch(blob, catalog).sessionNamed("Lower A")!!
        assertEquals(
            listOf(
                "ex-barbell-back-squat",
                "ex-romanian-deadlift",
                "ex-walking-lunge",
                "ex-leg-curl",
                "ex-standing-calf-raise",
                "ex-cable-crunch",
                "ex-wall-sit",
            ),
            lower.lifts.map { it.exercise.id },
        )
        assertEquals(4, lower.lift("ex-barbell-back-squat").targetSets)
        assertEquals(4, lower.lift("ex-barbell-back-squat").scheme.repsMin)
        assertEquals(6, lower.lift("ex-barbell-back-squat").scheme.repsMax)
        val lunges = lower.lift("ex-walking-lunge")
        assertEquals(3, lunges.targetSets)
        assertEquals(8, lunges.targetReps)
        assertTrue(lunges.scheme.perSide)
        assertEquals("ex-plank", lower.lift("ex-cable-crunch").alternative?.id)
        assertFalse(lower.lift("ex-cable-crunch").scheme.isTimed)
        assertTimedHold(lower.lift("ex-wall-sit"), 30, 45)
        assertTrue("Lower A unmatched=${lower.unmatched}", lower.unmatched.isEmpty())
    }

    private fun PastedSession.lift(id: String): PastedLift =
        lifts.first { it.exercise.id == id }
}

class WorkoutPasteIssueCopyTest {
    private val catalog = WorkoutPaste.catalogExercises()

    @Test
    fun unknownNameQuotesTheWrittenLine() {
        val session = WorkoutPaste.parseAndMatch(
            """
            Upper A (strength)
            1. Not a real lift — 3×8
            2. Barbell bench press — 4×4–6
            """.trimIndent(),
            catalog,
        ).sessionNamed("Upper A")!!
        assertEquals(listOf("ex-barbell-bench-press"), session.lifts.map { it.exercise.id })
        val miss = session.unmatched.single()
        assertEquals("1. Not a real lift — 3×8", miss.raw)
        assertEquals(PasteIssueKind.UNKNOWN_EXERCISE, miss.kind)
        assertTrue(miss.reason, miss.reason.contains("Not a real lift"))
        assertTrue(miss.reason, miss.reason.contains("No library lift"))
        val quoted = WorkoutPasteCopy.issue(miss)
        assertTrue(quoted, quoted.contains("1. Not a real lift — 3×8"))
        assertTrue(quoted, quoted.startsWith("“"))
        assertTrue(miss.canPick)
    }

    @Test
    fun neitherOrMatchQuotesBothNames() {
        val miss = WorkoutPaste.parseAndMatch(
            """
            Upper A (strength)
            1. Foo raise or Bar press — 3×8
            """.trimIndent(),
            catalog,
        ).sessionNamed("Upper A")!!.unmatched.single()
        assertEquals("1. Foo raise or Bar press — 3×8", miss.raw)
        assertEquals(PasteIssueKind.UNKNOWN_EXERCISE, miss.kind)
        assertTrue(miss.reason, miss.reason.contains("Foo raise"))
        assertTrue(miss.reason, miss.reason.contains("Bar press"))
        assertTrue(WorkoutPasteCopy.issue(miss).contains("1. Foo raise or Bar press — 3×8"))
    }

    @Test
    fun plusLineImportsTheMatchAndNamesTheMiss() {
        val session = WorkoutPaste.parseAndMatch(
            """
            Upper B (muscle)
            6. Arms: curl + not-a-push — 2–3×10–15 each
            """.trimIndent(),
            catalog,
        ).sessionNamed("Upper B")!!
        assertEquals(listOf("ex-barbell-curl"), session.lifts.map { it.exercise.id })
        val miss = session.unmatched.single()
        assertEquals(PasteIssueKind.TWO_LIFTS_NEEDED, miss.kind)
        assertEquals("6. Arms: curl + not-a-push — 2–3×10–15 each", miss.raw)
        assertTrue(miss.reason, miss.reason.contains("two lifts"))
        assertTrue(miss.reason, miss.reason.contains("not-a-push"))
        assertTrue(WorkoutPasteCopy.issue(miss).contains("6. Arms: curl + not-a-push"))
        assertTrue(miss.canPick)
    }

    @Test
    fun badDoseQuotesTheLineAndStillImportsTheLift() {
        val session = WorkoutPaste.parseAndMatch(
            """
            Upper A (strength)
            1. Barbell bench press — potato
            """.trimIndent(),
            catalog,
        ).sessionNamed("Upper A")!!
        assertEquals("ex-barbell-bench-press", session.lifts.single().exercise.id)
        val miss = session.unmatched.single()
        assertEquals(PasteIssueKind.BAD_DOSE, miss.kind)
        assertEquals("1. Barbell bench press — potato", miss.raw)
        assertTrue(miss.reason, miss.reason.contains("sets, reps, or hold time"))
        assertTrue(WorkoutPasteCopy.issue(miss).contains("1. Barbell bench press — potato"))
        assertFalse(miss.canPick)
    }

    @Test
    fun staticHoldWithoutAnSIsStillSeconds() {
        val hang = WorkoutPaste.parseAndMatch(
            """
            Upper A (strength)
            7. Static: dead hang — 2×20–40
            """.trimIndent(),
            catalog,
        ).sessionNamed("Upper A")!!.lift("ex-dead-hang")
        assertTimedHold(hang, 20, 40)
        assertEquals(2, hang.targetSets)
    }

    @Test
    fun repsOnAHoldNameTheHoldVsRepsIssue() {
        val session = WorkoutPaste.parseAndMatch(
            """
            Upper A (strength)
            Dead hang — 3×4
            """.trimIndent(),
            catalog,
        ).sessionNamed("Upper A")!!
        val hang = session.lift("ex-dead-hang")
        assertTrue(hang.scheme.isTimed)
        val miss = session.unmatched.single()
        assertEquals(PasteIssueKind.HOLD_VS_REPS, miss.kind)
        assertEquals("Dead hang — 3×4", miss.raw)
        assertTrue(WorkoutPasteCopy.issue(miss).contains("Dead hang — 3×4"))
        assertTrue(miss.reason, miss.reason.contains("timed hold") || miss.reason.contains("seconds"))
        assertFalse(miss.canPick)
    }

    @Test
    fun garbagePasteQuotesNoSessionNameAndNoNumberedLifts() {
        val text = "asdf potato"
        val plan = WorkoutPaste.parseAndMatch(text, catalog)
        assertTrue(plan.sessions.isEmpty())
        val reason = checkNotNull(WorkoutPaste.unreadableReason(text, plan))
        assertTrue(reason, reason.contains(WorkoutPasteCopy.NO_SESSION_NAME))
        assertTrue(reason, reason.contains(WorkoutPasteCopy.NO_NUMBERED_LIFTS))
        assertFalse(reason, reason.contains("Could not read a workout in that text"))
    }

    @Test
    fun numberedLiftsWithoutASessionNameQuoteTheMissingTitle() {
        val text = "1. Back squat — 4×4–6"
        val plan = WorkoutPaste.parseAndMatch(text, catalog)
        assertTrue(plan.sessions.isEmpty())
        val reason = checkNotNull(WorkoutPaste.unreadableReason(text, plan))
        assertEquals(WorkoutPasteCopy.NO_SESSION_NAME, reason)
        assertFalse(reason, reason.contains("Could not read a workout in that text"))
    }

    @Test
    fun leftoverStarsOnANonSessionTitleAreQuoted() {
        val text = "**Tuesday**\nasdf"
        val plan = WorkoutPaste.parseAndMatch(text, catalog)
        val reason = checkNotNull(WorkoutPaste.unreadableReason(text, plan))
        assertTrue(reason, reason.contains(WorkoutPasteCopy.NO_SESSION_NAME))
        assertTrue(reason, reason.contains(WorkoutPasteCopy.IGNORED_STARS))
        assertTrue(reason, reason.contains(WorkoutPasteCopy.NO_NUMBERED_LIFTS))
    }

    @Test
    fun pasteIssuesSummaryQuotesTheFirstLine() {
        val miss = PastedUnmatched(
            raw = "1. Mystery raise — 3×8",
            names = listOf("Mystery raise"),
            scheme = null,
            kind = PasteIssueKind.UNKNOWN_EXERCISE,
            reason = WorkoutPasteCopy.reason(
                kind = PasteIssueKind.UNKNOWN_EXERCISE,
                names = listOf("Mystery raise"),
            ),
        )
        val text = WorkoutPasteCopy.pasteIssues(listOf(miss, miss.copy(raw = "2. Other")))
        assertTrue(text, text.contains("1. Mystery raise — 3×8"))
        assertTrue(text, text.contains("And 1 more below"))
        assertTrue(text, text.contains("No library lift"))
    }

    private fun PastedSession.lift(id: String): PastedLift =
        lifts.first { it.exercise.id == id }
}
