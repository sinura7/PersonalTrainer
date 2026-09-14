package com.sinura.personaltrainer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paste importer must read Allen's written program and fill real
 * routines. It must not invent the work.
 */
class WorkoutPasteParserTest {
    @Test
    fun fourByFourToSixIsFourSetsAndARepRange() {
        val scheme = WorkoutPasteParser.parse(
            """
            Upper A (strength)
            Barbell bench press — 4×4–6
            """.trimIndent(),
        ).single().lines.single().schemes.single()
        assertEquals(4, scheme.setsMin)
        assertEquals(4, scheme.setsMax)
        assertEquals(4, scheme.repsMin)
        assertEquals(6, scheme.repsMax)
        assertEquals(null, scheme.secondsMin)
        assertEquals("4×4–6", scheme.prescription())
    }

    @Test
    fun perLegMarksUnilateralWalkingLunges() {
        val line = WorkoutPasteParser.parse(
            """
            Lower A (strength)
            Walking lunges — 3×8/leg
            """.trimIndent(),
        ).single().lines.single()
        val scheme = line.schemes.single()
        assertEquals(3, scheme.setsMin)
        assertEquals(8, scheme.repsMin)
        assertTrue(scheme.perSide)
    }

    @Test
    fun timedHoldReadsSecondsNotReps() {
        val scheme = WorkoutPasteParser.parse(
            """
            Upper A (strength)
            Static: dead hang — 2×20–40s (ligament/grip)
            """.trimIndent(),
        ).single().lines.single().schemes.single()
        assertEquals(2, scheme.setsMin)
        assertEquals(20, scheme.secondsMin)
        assertEquals(40, scheme.secondsMax)
        assertTrue(scheme.isTimed)
        assertEquals(1, scheme.storedReps)
        assertEquals("2×20–40s", scheme.prescription())
    }

    @Test
    fun setRangeAndPerSideOnSidePlank() {
        val scheme = WorkoutPasteParser.parse(
            """
            Lower B (muscle)
            Abs: side plank — 2–3×20–40s/side
            """.trimIndent(),
        ).single().lines.single().schemes.single()
        assertEquals(2, scheme.setsMin)
        assertEquals(3, scheme.setsMax)
        assertEquals(20, scheme.secondsMin)
        assertEquals(40, scheme.secondsMax)
        assertTrue(scheme.perSide)
    }

    @Test
    fun armsLineSplitsIntoTwoNameGroups() {
        val line = WorkoutPasteParser.parse(
            """
            Upper B (muscle)
            Arms: curl + triceps pushdown — 2–3×10–15 each
            """.trimIndent(),
        ).single().lines.single()
        assertEquals(listOf(listOf("curl"), listOf("triceps pushdown")), line.groups)
        assertTrue(line.each)
        assertEquals(2, line.schemes.single().setsMin)
        assertEquals(3, line.schemes.single().setsMax)
        assertEquals(10, line.schemes.single().repsMin)
        assertEquals(15, line.schemes.single().repsMax)
    }

    @Test
    fun alternativeSchemesStayPaired() {
        val line = WorkoutPasteParser.parse(
            """
            Lower A (strength)
            Abs: cable crunch or weighted plank — 3×10–15 / 3×30–45s
            """.trimIndent(),
        ).single().lines.single()
        assertEquals(2, line.schemes.size)
        assertEquals(10, line.schemes[0].repsMin)
        assertEquals(15, line.schemes[0].repsMax)
        assertEquals(30, line.schemes[1].secondsMin)
        assertEquals(45, line.schemes[1].secondsMax)
        assertEquals(listOf("cable crunch", "weighted plank"), line.groups.single())
    }

    @Test
    fun weeklyLayoutAndProgressionAreNotWorkLines() {
        val parsed = WorkoutPasteParser.parseDocument(
            """
            Flexibility / ligament block (Sat)
            Hamstring stretch
            Weekly layout
            Mon — Upper A (strength bias)
            Warm up every lift day: 5–8 min light cardio, then 2–3 ramp sets on the first compound.
            Progression
            Deload every 6th week (cut volume ~40%, keep light intensity).
            """.trimIndent(),
        )
        assertEquals(listOf("Flexibility"), parsed.sessions.map { it.name })
        assertEquals(1, parsed.sessions.single().lines.size)
        assertEquals(listOf("Mon — Upper A (strength bias)"), parsed.weekLines)
        assertTrue(checkNotNull(parsed.warmupLine).contains("ramp sets"))
        assertTrue(parsed.progressionLines.single().contains("Deload every 6th week"))
    }

    @Test
    fun markdownWeekAtTheTopDoesNotSwallowTheBlocks() {
        val parsed = WorkoutPasteParser.parseDocument(
            """
            ## Week
            Mon — Upper A (strength bias)
            ## Upper A (strength)
            Barbell bench press — 4×4–6
            Static: dead hang — 2×20–40s
            ## Progression
            Deload every 6th week
            """.trimIndent(),
        )
        assertEquals(listOf("Upper A"), parsed.sessions.map { it.name })
        assertEquals(2, parsed.sessions.single().lines.size)
        assertEquals(listOf("Mon — Upper A (strength bias)"), parsed.weekLines)
        assertTrue(parsed.progressionLines.single().contains("Deload"))
    }
}

class WorkoutPasteReferenceTest {
    private val catalog = WorkoutPaste.catalogExercises()
    private val plan by lazy {
        val text = checkNotNull(
            WorkoutPasteReferenceTest::class.java.getResource("/paste-routine-reference.md"),
        ) { "paste-routine-reference.md missing from test resources" }.readText()
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
        assertNotNull(plan.sessionNamed("Cardio"))
        assertNotNull(plan.sessionNamed("Flexibility"))
        assertEquals(PastedSessionKind.CARDIO, plan.sessionNamed("Cardio")?.kind)
        assertEquals(PastedSessionKind.FLEXIBILITY, plan.sessionNamed("Flexibility")?.kind)
    }

    @Test
    fun upperABenchIsBarbellBenchFourByFourToSix() {
        val bench = plan.sessionNamed("Upper A")!!.lift("ex-barbell-bench-press")
        assertEquals("Barbell Bench Press", bench.exercise.name)
        assertEquals(4, bench.targetSets)
        assertEquals(4, bench.scheme.repsMin)
        assertEquals(6, bench.scheme.repsMax)
        assertEquals(4, bench.targetReps)
        assertEquals(WorkoutPasteRest.COMPOUND_SECONDS, bench.restSeconds)
        assertFalse(bench.scheme.isTimed)
    }

    @Test
    fun upperAFillsTheRestOfTheWrittenWork() {
        val upper = plan.sessionNamed("Upper A")!!
        assertEquals("ex-pull-up", upper.lifts[1].exercise.id)
        assertEquals("ex-lat-pulldown", upper.lifts[1].alternative?.id)
        assertEquals("ex-overhead-press", upper.lift("ex-overhead-press").exercise.id)
        assertEquals(3, upper.lift("ex-overhead-press").targetSets)
        assertEquals(5, upper.lift("ex-overhead-press").scheme.repsMin)
        assertEquals(8, upper.lift("ex-overhead-press").scheme.repsMax)
        assertEquals("ex-chest-supported-dumbbell-row", upper.lift("ex-chest-supported-dumbbell-row").exercise.id)
        assertEquals("ex-lateral-raise", upper.lift("ex-lateral-raise").exercise.id)
        assertEquals("ex-hanging-leg-raise", upper.lift("ex-hanging-leg-raise").exercise.id)
        val hang = upper.lift("ex-dead-hang")
        assertEquals(2, hang.targetSets)
        assertEquals(20, hang.scheme.secondsMin)
        assertEquals(40, hang.scheme.secondsMax)
        assertTrue(hang.scheme.isTimed)
        assertEquals(1, hang.targetReps)
        assertEquals(WorkoutPasteRest.ACCESSORY_SECONDS, hang.restSeconds)
        assertEquals(WorkoutPasteRest.ACCESSORY_SECONDS, upper.lift("ex-lateral-raise").restSeconds)
        assertTrue(upper.unmatched.isEmpty())
        assertTrue(upper.notes().contains("2×20–40s"))
        assertTrue(upper.notes().contains("hold, not reps"))
    }

    @Test
    fun lowerALungesAreUnilateralAndWallSitIsTimed() {
        val lower = plan.sessionNamed("Lower A")!!
        assertEquals("ex-barbell-back-squat", lower.lift("ex-barbell-back-squat").exercise.id)
        assertEquals(4, lower.lift("ex-barbell-back-squat").targetSets)
        val lunges = lower.lift("ex-walking-lunge")
        assertEquals(3, lunges.targetSets)
        assertEquals(8, lunges.targetReps)
        assertTrue(lunges.scheme.perSide)
        assertEquals("ex-romanian-deadlift", lower.lift("ex-romanian-deadlift").exercise.id)
        assertEquals("ex-leg-curl", lower.lift("ex-leg-curl").exercise.id)
        assertEquals("ex-standing-calf-raise", lower.lift("ex-standing-calf-raise").exercise.id)
        assertEquals("ex-cable-crunch", lower.lift("ex-cable-crunch").exercise.id)
        val sit = lower.lift("ex-wall-sit")
        assertEquals(2, sit.targetSets)
        assertEquals(30, sit.scheme.secondsMin)
        assertEquals(45, sit.scheme.secondsMax)
        assertTrue(sit.scheme.isTimed)
        assertEquals(1, sit.targetReps)
        assertEquals(WorkoutPasteRest.ACCESSORY_SECONDS, sit.restSeconds)
    }

    @Test
    fun upperBSplitsTheArmsLineAndKeepsAbWheel() {
        val upper = plan.sessionNamed("Upper B")!!
        val ids = upper.lifts.map { it.exercise.id }
        val unmatched = upper.unmatched.map { it.raw }
        assertTrue(
            "Upper B lifts=$ids unmatched=$unmatched",
            "ex-barbell-curl" in ids && "ex-tricep-pushdown" in ids,
        )
        assertEquals("ex-incline-dumbbell-bench-press", upper.lift("ex-incline-dumbbell-bench-press").exercise.id)
        assertEquals("ex-seated-cable-row", upper.lift("ex-seated-cable-row").exercise.id)
        assertEquals("ex-seated-dumbbell-press", upper.lift("ex-seated-dumbbell-press").exercise.id)
        assertEquals("ex-lat-pulldown", upper.lift("ex-lat-pulldown").exercise.id)
        assertEquals("ex-face-pull", upper.lift("ex-face-pull").exercise.id)
        assertEquals("ex-barbell-curl", upper.lift("ex-barbell-curl").exercise.id)
        assertEquals("ex-tricep-pushdown", upper.lift("ex-tricep-pushdown").exercise.id)
        assertEquals(2, upper.lift("ex-barbell-curl").targetSets)
        assertEquals(10, upper.lift("ex-barbell-curl").scheme.repsMin)
        assertEquals(15, upper.lift("ex-barbell-curl").scheme.repsMax)
        assertEquals("ex-ab-wheel-rollout", upper.lift("ex-ab-wheel-rollout").exercise.id)
        assertEquals("ex-dead-bug", upper.lift("ex-ab-wheel-rollout").alternative?.id)
        val hang = upper.lift("ex-scapular-hang")
        assertEquals("ex-y-hold", hang.alternative?.id)
        assertEquals(2, hang.targetSets)
        assertEquals(20, hang.scheme.secondsMin)
        assertTrue(hang.scheme.isTimed)
        assertEquals(1, hang.targetReps)
    }

    @Test
    fun lowerBFrontSquatAndTimedSidePlank() {
        val lower = plan.sessionNamed("Lower B")!!
        val squat = lower.lift("ex-front-squat")
        assertEquals(3, squat.targetSets)
        assertEquals(8, squat.scheme.repsMin)
        assertEquals(12, squat.scheme.repsMax)
        assertEquals("ex-goblet-squat", squat.alternative?.id)
        assertEquals("ex-hip-thrust", lower.lift("ex-hip-thrust").exercise.id)
        assertEquals("ex-bulgarian-split-squat", lower.lift("ex-bulgarian-split-squat").exercise.id)
        assertTrue(lower.lift("ex-bulgarian-split-squat").scheme.perSide)
        assertEquals("ex-leg-press", lower.lift("ex-leg-press").exercise.id)
        assertEquals("ex-seated-calf-raise", lower.lift("ex-seated-calf-raise").exercise.id)
        val plank = lower.lift("ex-side-plank")
        assertEquals(2, plank.targetSets)
        assertEquals(20, plank.scheme.secondsMin)
        assertEquals(40, plank.scheme.secondsMax)
        assertTrue(plank.scheme.perSide)
        assertTrue(plank.scheme.isTimed)
        assertEquals(1, plank.targetReps)
        assertEquals(WorkoutPasteRest.ACCESSORY_SECONDS, plank.restSeconds)
        val hold = lower.lift("ex-deep-squat-hold")
        assertEquals(2, hold.targetSets)
        assertEquals(30, hold.scheme.secondsMin)
        assertEquals(45, hold.scheme.secondsMax)
        assertTrue(hold.scheme.isTimed)
        assertEquals(1, hold.targetReps)
    }

    @Test
    fun cardioStaysAVisibleDraftAndFlexibilityMatchesStretches() {
        val cardio = plan.sessionNamed("Cardio")!!
        assertTrue(cardio.lifts.isEmpty())
        assertTrue(cardio.unmatched.isNotEmpty())
        assertTrue(cardio.notes().contains("Zone 2"))
        val flex = plan.sessionNamed("Flexibility")!!
        assertEquals("ex-couch-stretch", flex.lift("ex-couch-stretch").exercise.id)
        assertEquals("ex-hamstring-stretch", flex.lift("ex-hamstring-stretch").exercise.id)
        assertEquals("ex-doorway-chest-stretch", flex.lift("ex-doorway-chest-stretch").exercise.id)
        assertEquals("ex-calf-stretch", flex.lift("ex-calf-stretch").exercise.id)
        assertEquals("ex-ankle-rocks", flex.lift("ex-ankle-rocks").exercise.id)
        assertEquals("ex-90-90-hips", flex.lift("ex-couch-stretch").alternative?.id)
        assertTrue(flex.unmatched.any { it.raw.contains("articular", ignoreCase = true) })
        assertTrue(flex.unmatched.none { it.raw.contains("Weekly", ignoreCase = true) })
        assertTrue(flex.unmatched.none { it.raw.contains("Deload", ignoreCase = true) })
        assertEquals(45, flex.lift("ex-couch-stretch").scheme.secondsMin)
        assertEquals(60, flex.lift("ex-couch-stretch").scheme.secondsMax)
        assertTrue(flex.lift("ex-couch-stretch").scheme.isTimed)
        assertEquals(1, flex.lift("ex-couch-stretch").targetReps)
    }

    @Test
    fun weeklyLayoutPinsStrengthDaysAndKeepsProgression() {
        assertEquals(
            listOf(
                Weekday.MONDAY to listOf("Upper A"),
                Weekday.TUESDAY to listOf("Lower A"),
                Weekday.WEDNESDAY to listOf("Cardio"),
                Weekday.THURSDAY to listOf("Upper B"),
                Weekday.FRIDAY to listOf("Lower B"),
                Weekday.SATURDAY to listOf("Cardio", "Flexibility"),
                Weekday.SUNDAY to emptyList(),
            ),
            plan.weekPins.map { it.weekday to it.sessionNames },
        )
        assertTrue(checkNotNull(plan.warmupNote).contains("ramp sets"))
        val progression = checkNotNull(plan.progressionNote)
        assertTrue(progression.contains("2–3 min"))
        assertTrue(progression.contains("60–90s"))
        assertTrue(progression.contains("Deload every 6th week"))
        val notes = plan.combinedNotes(plan.sessionNamed("Upper A")!!)
        assertTrue(notes.contains("Warm-up"))
        assertTrue(notes.contains("Weekly layout"))
        assertTrue(notes.contains("Mon — Upper A"))
        assertTrue(notes.contains("Progression"))
    }

    @Test
    fun staticsAreTimeNotReps() {
        assertTimedHold(plan.sessionNamed("Upper A")!!.lift("ex-dead-hang"), 20, 40)
        assertTimedHold(plan.sessionNamed("Lower A")!!.lift("ex-wall-sit"), 30, 45)
        assertTimedHold(plan.sessionNamed("Upper B")!!.lift("ex-scapular-hang"), 20, 30)
        assertTimedHold(plan.sessionNamed("Lower B")!!.lift("ex-side-plank"), 20, 40)
        assertTimedHold(plan.sessionNamed("Lower B")!!.lift("ex-deep-squat-hold"), 30, 45)
        val crunch = plan.sessionNamed("Lower A")!!.lift("ex-cable-crunch")
        assertFalse(crunch.scheme.isTimed)
        assertEquals("ex-plank", crunch.alternative?.id)
    }

    @Test
    fun weightedPlankIsSecondsNotReps() {
        val hold = WorkoutPaste.parseAndMatch(
            """
            Lower A (strength)
            Abs: weighted plank — 3×30–45s
            """.trimIndent(),
            catalog,
        ).sessionNamed("Lower A")!!.lift("ex-plank")
        assertTimedHold(hold, 30, 45)
    }

    @Test
    fun pairedCrunchOrPlankDoesNotPutTheHoldOnReps() {
        val plank = WorkoutPaste.parseAndMatch(
            """
            Lower A (strength)
            Abs: weighted plank or cable crunch — 3×30–45s / 3×10–15
            """.trimIndent(),
            catalog,
        ).sessionNamed("Lower A")!!.lift("ex-plank")
        assertTimedHold(plank, 30, 45)
    }

    @Test
    fun bareCurlMatchesBarbellCurlNotLegCurl() {
        val hit = WorkoutCatalogMatch.match("curl", catalog)
        assertEquals("ex-barbell-curl", hit?.id)
    }

    private fun PastedSession.lift(id: String): PastedLift =
        lifts.first { it.exercise.id == id }
}

class WorkoutPasteWeeklyProgramTest {
    private val catalog = WorkoutPaste.catalogExercises()
    private val plan by lazy {
        val text = checkNotNull(
            WorkoutPasteWeeklyProgramTest::class.java.getResource("/weekly-program-reference.md"),
        ) { "weekly-program-reference.md missing from test resources" }.readText()
        WorkoutPaste.parseAndMatch(text, catalog)
    }

    @Test
    fun markdownWeekStillMakesTheFourStrengthBlocks() {
        assertEquals(
            listOf("Upper A", "Lower A", "Upper B", "Lower B"),
            plan.strengthSessions().map { it.name },
        )
        assertNotNull(plan.sessionNamed("Cardio"))
        assertNotNull(plan.sessionNamed("Flexibility"))
        assertEquals(Weekday.MONDAY, plan.weekPins.first().weekday)
        assertEquals(listOf("Upper A"), plan.weekPins.first().sessionNames)
        assertTrue(checkNotNull(plan.progressionNote).contains("Deload every 6th week"))
    }

    @Test
    fun staticsOnTheWeeklyProgramAreTimeNotReps() {
        assertTimedHold(plan.sessionNamed("Upper A")!!.lift("ex-dead-hang"), 20, 40)
        assertTimedHold(plan.sessionNamed("Lower A")!!.lift("ex-wall-sit"), 30, 45)
        assertTimedHold(plan.sessionNamed("Upper B")!!.lift("ex-scapular-hang"), 20, 30)
        assertTimedHold(plan.sessionNamed("Lower B")!!.lift("ex-side-plank"), 20, 40)
        assertTimedHold(plan.sessionNamed("Lower B")!!.lift("ex-deep-squat-hold"), 30, 45)
        val yHold = plan.sessionNamed("Upper B")!!.lift("ex-scapular-hang").alternative
        assertEquals("ex-y-hold", yHold?.id)
        assertFalse(plan.sessionNamed("Lower A")!!.lift("ex-cable-crunch").scheme.isTimed)
        assertEquals("ex-plank", plan.sessionNamed("Lower A")!!.lift("ex-cable-crunch").alternative?.id)
    }

    @Test
    fun cardioAndCarsStayVisibleDrafts() {
        val cardio = plan.sessionNamed("Cardio")!!
        assertTrue(cardio.lifts.isEmpty())
        assertTrue(cardio.unmatched.isNotEmpty())
        val flex = plan.sessionNamed("Flexibility")!!
        assertTrue(flex.unmatched.any { it.raw.contains("articular", ignoreCase = true) })
        assertEquals("ex-ankle-rocks", flex.lift("ex-ankle-rocks").exercise.id)
        assertTrue(flex.unmatched.none { it.raw.contains("Ankle", ignoreCase = true) })
    }

    private fun PastedSession.lift(id: String): PastedLift =
        lifts.first { it.exercise.id == id }
}

internal fun assertTimedHold(lift: PastedLift, secondsMin: Int, secondsMax: Int) {
    assertTrue("${lift.exercise.id} must be a hold, was ${lift.scheme.prescription()}", lift.scheme.isTimed)
    assertEquals(secondsMin, lift.scheme.secondsMin)
    assertEquals(secondsMax, lift.scheme.secondsMax)
    assertEquals(secondsMin, lift.targetSeconds)
    assertEquals(secondsMax, lift.targetSecondsMax)
    assertEquals(
        "${lift.exercise.id} stored the hold as reps (${lift.targetReps})",
        1,
        lift.targetReps,
    )
    assertNotEquals(secondsMin, lift.targetReps)
    assertNotEquals(secondsMax, lift.targetReps)
}
