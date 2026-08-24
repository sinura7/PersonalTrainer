package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.domain.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The generated program has to be trainable, not merely non-empty.
 *
 * The failure this guards against is quiet: a template asks for a barbell family, the lifter
 * has no barbell, the slot resolves to nothing, and a "five-lift session" ships with two lifts
 * in it. Nothing throws and nothing looks broken until someone tries to train it.
 */
class RoutineGeneratorTest {
    private val catalog: List<Exercise> = DefaultExercises.catalog().map { seed ->
        Exercise(
            id = seed.id,
            name = seed.name,
            muscleGroup = seed.muscleGroup,
            notes = "",
            isCustom = false,
            equipment = seed.equipment,
            loadType = seed.loadType,
            movementKey = seed.movementKey,
            muscles = seed.credits,
        )
    }

    private fun answers(
        age: TrainingAge = TrainingAge.RETURNING,
        days: Int = 4,
        place: TrainingPlace = TrainingPlace.FULL_GYM,
        goal: TrainingGoal = TrainingGoal.GENERAL,
        emphasis: TrainingEmphasis = TrainingEmphasis.BALANCED,
        preferred: Set<Weekday> = emptySet(),
    ) = OnboardingAnswers(
        trainingAge = age,
        daysPerWeek = days,
        preferredDays = preferred,
        place = place,
        goal = goal,
        emphasis = emphasis,
    )

    @Test
    fun everyCombinationProducesAFullSessionForEveryRoutine() {
        // The exhaustive sweep. 3 training ages x 7 day counts x 3 places x 4 goals = 252
        // programs, and not one of them may ship a short session.
        var checked = 0
        TrainingAge.entries.forEach { age ->
            (1..7).forEach { days ->
                TrainingPlace.entries.forEach { place ->
                    TrainingGoal.entries.forEach { goal ->
                        val plan = RoutineGenerator.generate(
                            answers(age = age, days = days, place = place, goal = goal),
                            catalog,
                        )
                        plan.routines.forEach { routine ->
                            assertEquals(
                                "$age/$days/$place/$goal: ${routine.name} is short",
                                age.liftsPerSession,
                                routine.lifts.size,
                            )
                        }
                        checked += 1
                    }
                }
            }
        }
        assertEquals(252, checked)
    }

    @Test
    fun bodyweightOnlyStillGetsARealProgram() {
        // The hardest case by far: most families in the templates need equipment.
        val plan = RoutineGenerator.generate(
            answers(age = TrainingAge.EXPERIENCED, days = 6, place = TrainingPlace.BODYWEIGHT_ONLY),
            catalog,
        )
        assertTrue(plan.routines.isNotEmpty())
        plan.routines.forEach { routine ->
            assertEquals(6, routine.lifts.size)
            routine.lifts.forEach { lift ->
                assertTrue(
                    "${lift.name} needs equipment a bodyweight lifter does not have",
                    lift.equipment in TrainingPlace.BODYWEIGHT_ONLY.equipment,
                )
            }
        }
    }

    @Test
    fun homeDumbbellsNeverProposesABarbellOrAMachine() {
        val plan = RoutineGenerator.generate(
            answers(days = 5, place = TrainingPlace.HOME_DUMBBELLS),
            catalog,
        )
        val illegal = plan.routines.flatMap { it.lifts }
            .filterNot { it.equipment in TrainingPlace.HOME_DUMBBELLS.equipment }
        assertEquals(emptyList<BlueprintLift>(), illegal)
    }

    @Test
    fun noSessionRepeatsALift() {
        TrainingPlace.entries.forEach { place ->
            val plan = RoutineGenerator.generate(answers(days = 6, place = place), catalog)
            plan.routines.forEach { routine ->
                val ids = routine.lifts.map { it.exerciseId }
                assertEquals("${routine.name} repeats a lift under $place", ids.size, ids.toSet().size)
            }
        }
    }

    @Test
    fun theWeekHasSevenDaysAndExactlyTheRequestedTrainingDays() {
        (2..6).forEach { days ->
            val plan = RoutineGenerator.generate(answers(days = days), catalog)
            assertEquals(7, plan.days.size)
            assertEquals(days, plan.trainingDayCount)
            assertEquals(
                "a week must not repeat a weekday",
                7,
                plan.days.map { it.dayOfWeek }.toSet().size,
            )
        }
    }

    @Test
    fun theDaysTheLifterPickedAreTheDaysTheyGet() {
        val picked = setOf(Weekday.TUESDAY, Weekday.THURSDAY, Weekday.SATURDAY)
        val plan = RoutineGenerator.generate(answers(days = 3, preferred = picked), catalog)
        val training = plan.days.filterNot { it.isRest }.map { it.dayOfWeek }.toSet()
        assertEquals(picked, training)
    }

    @Test
    fun pickingFewerDaysThanPromisedIsToppedUpNotIgnored() {
        // They said four days and tapped two. Both of theirs survive; the app finds the rest.
        val picked = setOf(Weekday.MONDAY, Weekday.FRIDAY)
        val plan = RoutineGenerator.generate(answers(days = 4, preferred = picked), catalog)
        val training = plan.days.filterNot { it.isRest }.map { it.dayOfWeek }.toSet()
        assertEquals(4, training.size)
        assertTrue(training.containsAll(picked))
    }

    @Test
    fun everyTrainingDayResolvesToARoutineThatExists() {
        // The empty-session bug, in blueprint form: a day pointing at a routine that was never
        // generated would produce exactly the "Upper with no lifts" session this phase exists
        // to kill.
        TrainingAge.entries.forEach { age ->
            (1..7).forEach { days ->
                val plan = RoutineGenerator.generate(answers(age = age, days = days), catalog)
                plan.days.filterNot { it.isRest }.forEach { day ->
                    val routine = plan.routineFor(day)
                    assertNotNull("$age/$days: ${day.dayOfWeek} points at nothing", routine)
                    assertTrue(routine!!.lifts.isNotEmpty())
                }
            }
        }
    }

    @Test
    fun aSingleDayIsOneFullBodySession() {
        val plan = RoutineGenerator.generate(answers(days = 1), catalog)
        assertEquals(SplitStyle.FULL_BODY, plan.splitStyle)
        assertEquals(1, plan.routines.size)
        assertEquals(1, plan.days.count { !it.isRest })
        assertNotNull(plan.days.first { !it.isRest }.let(plan::routineFor))
    }

    @Test
    fun fullBodyAlternatesTwoSessionsRatherThanRepeatingOne() {
        val plan = RoutineGenerator.generate(answers(days = 3), catalog)
        assertEquals(SplitStyle.FULL_BODY, plan.splitStyle)
        assertEquals(2, plan.routines.size)
        val used = plan.days.filterNot { it.isRest }.map { it.routineKey }
        assertTrue("three full-body days must not be the same session", used.toSet().size > 1)
        // And the two variants are genuinely different sessions.
        val a = plan.routines[0].lifts.map { it.exerciseId }.toSet()
        val b = plan.routines[1].lifts.map { it.exerciseId }.toSet()
        assertTrue("the variants overlap too much to be worth two", (a intersect b).size < a.size)
    }

    @Test
    fun compoundsComeFirst() {
        // Heaviest first is the one ordering rule the generator owns. A session that opens on
        // a lateral raise and closes on a squat is a session nobody would write.
        val plan = RoutineGenerator.generate(answers(age = TrainingAge.EXPERIENCED, days = 4), catalog)
        val byId = catalog.associateBy { it.id }
        plan.routines.forEach { routine ->
            val compound = routine.lifts.map { AddDefaults.isCompound(byId.getValue(it.exerciseId)) }
            val firstIsolation = compound.indexOfFirst { !it }
            if (firstIsolation >= 0) {
                assertFalse(
                    "${routine.name} puts a compound after an isolation",
                    compound.drop(firstIsolation).any { it },
                )
            }
        }
    }

    @Test
    fun targetsComeFromTheSameRuleAsAHandAddedLift() {
        // Same table, plus the one thing only the session knows: where the lift sits in it.
        val plan = RoutineGenerator.generate(answers(days = 4), catalog)
        val byId = catalog.associateBy { it.id }
        plan.routines.forEach { routine ->
            routine.lifts.forEachIndexed { index, lift ->
                val role = if (index < 2) LiftRole.PRIMARY else LiftRole.ACCESSORY
                assertEquals(
                    AddDefaults.forExercise(byId.getValue(lift.exerciseId), role),
                    lift.targets,
                )
            }
        }
    }

    @Test
    fun onlyTheOpeningLiftsGetMainLiftTargets() {
        // The defect this exists to stop: a leg day of four separate movements, every one of
        // them 3 x 5 with 150 seconds' rest.
        val plan = RoutineGenerator.generate(answers(age = TrainingAge.EXPERIENCED, days = 4), catalog)
        plan.routines.forEach { routine ->
            val heavy = routine.lifts.count { it.targets.reps <= 6 && it.targets.restSeconds >= 120 }
            assertTrue(
                "${routine.name} has $heavy main-lift-weight entries",
                heavy <= 2,
            )
            // And rest never climbs as the session goes on.
            routine.lifts.zipWithNext().forEach { (earlier, later) ->
                assertTrue(
                    "${routine.name}: rest goes up at ${later.name}",
                    later.targets.restSeconds <= earlier.targets.restSeconds,
                )
            }
        }
    }

    @Test
    fun aSparseCatalogDegradesInsteadOfCrashing() {
        // Nothing but push-ups. The program should be short and honest, not an exception.
        val onlyPushUps = catalog.filter { it.movementKey == "push-up" }
        val plan = RoutineGenerator.generate(answers(days = 4), onlyPushUps)
        assertEquals(7, plan.days.size)
        plan.routines.forEach { routine ->
            assertTrue("a sparse catalog must not invent lifts", routine.lifts.size <= onlyPushUps.size)
        }
    }

    @Test
    fun balancedEmphasisMatchesTheUnemphasisedPlan() {
        val plain = RoutineGenerator.generate(answers(days = 4), catalog)
        val balanced = RoutineGenerator.generate(
            answers(days = 4, emphasis = TrainingEmphasis.BALANCED),
            catalog,
        )
        assertEquals(plain, balanced)
    }

    @Test
    fun fourDayUpperIsMajorityUpperAndKeepsALegDay() {
        val plan = RoutineGenerator.generate(
            answers(days = 4, emphasis = TrainingEmphasis.UPPER),
            catalog,
        )
        val kinds = plan.trainingKinds()
        assertTrue("upper days: $kinds", kinds.count { it.isUpperFamily } > kinds.count { it.isLowerFamily })
        assertTrue("lost the leg day: $kinds", kinds.any { it.isLowerFamily })
        assertEquals(4, kinds.size)
    }

    @Test
    fun fourDayLowerIsMajorityLowerAndKeepsAnUpperDay() {
        val plan = RoutineGenerator.generate(
            answers(days = 4, emphasis = TrainingEmphasis.LOWER),
            catalog,
        )
        val kinds = plan.trainingKinds()
        assertTrue("lower days: $kinds", kinds.count { it.isLowerFamily } > kinds.count { it.isUpperFamily })
        assertTrue("lost the upper day: $kinds", kinds.any { it.isUpperFamily })
        plan.routines.filter { it.focusKind.isLowerFamily }.forEach { routine ->
            assertTrue(
                "${routine.name} dropped the squat/hinge opener",
                routine.lifts.first().isSquatOrHinge(),
            )
        }
    }

    @Test
    fun threeDayUpperSwapsASlotWithoutStealingTheOpenerOrARestDay() {
        val picked = setOf(Weekday.TUESDAY, Weekday.THURSDAY, Weekday.SATURDAY)
        val balanced = RoutineGenerator.generate(answers(days = 3, preferred = picked), catalog)
        val upper = RoutineGenerator.generate(
            answers(days = 3, preferred = picked, emphasis = TrainingEmphasis.UPPER),
            catalog,
        )
        assertEquals(SplitStyle.FULL_BODY, upper.splitStyle)
        assertEquals(
            balanced.days.map { it.dayOfWeek to it.isRest },
            upper.days.map { it.dayOfWeek to it.isRest },
        )
        assertEquals(picked, upper.days.filterNot { it.isRest }.map { it.dayOfWeek }.toSet())
        val balancedA = balanced.routines.first()
        val upperA = upper.routines.first()
        assertEquals(balancedA.lifts.first().exerciseId, upperA.lifts.first().exerciseId)
        assertTrue(
            "upper emphasis left the full-body session unchanged",
            balancedA.lifts.map { it.exerciseId } != upperA.lifts.map { it.exerciseId },
        )
    }

    @Test
    fun unknownEmphasisFallsBackToBalanced() {
        assertEquals(TrainingEmphasis.BALANCED, TrainingEmphasis.fromStorage(null))
        assertEquals(TrainingEmphasis.BALANCED, TrainingEmphasis.fromStorage("NOPE"))
    }

    @Test
    fun athleticDiffersFromMuscleOnTheFirstDay() {
        val muscle = RoutineGenerator.generate(
            answers(age = TrainingAge.NEW, days = 4, goal = TrainingGoal.HYPERTROPHY),
            catalog,
        )
        val athletic = RoutineGenerator.generate(
            answers(age = TrainingAge.NEW, days = 4, goal = TrainingGoal.ATHLETIC),
            catalog,
        )
        val muscleFirst = muscle.routineFor(muscle.days.first { !it.isRest })!!
        val athleticFirst = athletic.routineFor(athletic.days.first { !it.isRest })!!
        val muscleFamilies = muscleFirst.lifts.map { it.family() }.toSet()
        val athleticFamilies = athleticFirst.lifts.map { it.family() }.toSet()
        assertTrue(
            "Athletic first day $athleticFamilies vs Muscle $muscleFamilies",
            (muscleFamilies - athleticFamilies).size + (athleticFamilies - muscleFamilies).size >= 2,
        )
    }

    @Test
    fun athleticBodyweightStillFillsAFullBodyDay() {
        val plan = RoutineGenerator.generate(
            answers(
                age = TrainingAge.NEW,
                days = 3,
                place = TrainingPlace.BODYWEIGHT_ONLY,
                goal = TrainingGoal.ATHLETIC,
            ),
            catalog,
        )
        plan.routines.forEach { routine ->
            assertTrue("${routine.name} is short", routine.lifts.size >= 4)
            routine.lifts.forEach { lift ->
                assertTrue(lift.equipment in TrainingPlace.BODYWEIGHT_ONLY.equipment)
            }
        }
    }

    @Test
    fun homeDumbbellAthleticNeverEmitsABarbell() {
        val plan = RoutineGenerator.generate(
            answers(
                age = TrainingAge.NEW,
                days = 4,
                place = TrainingPlace.HOME_DUMBBELLS,
                goal = TrainingGoal.ATHLETIC,
            ),
            catalog,
        )
        val illegal = plan.routines.flatMap { it.lifts }
            .filterNot { it.equipment in TrainingPlace.HOME_DUMBBELLS.equipment }
        assertEquals(emptyList<BlueprintLift>(), illegal)
    }

    @Test
    fun unknownGoalFallsBackToGeneral() {
        assertEquals(TrainingGoal.GENERAL, TrainingGoal.fromStorage(null))
        assertEquals(TrainingGoal.GENERAL, TrainingGoal.fromStorage("NOPE"))
    }

    @Test
    fun previewHeadlineNamesTheAnswersAStrangerCanRead() {
        val answers = answers(
            days = 4,
            place = TrainingPlace.HOME_DUMBBELLS,
            goal = TrainingGoal.ATHLETIC,
            emphasis = TrainingEmphasis.UPPER,
        )
        val plan = RoutineGenerator.generate(answers, catalog)
        assertEquals(
            "4 days · Upper body emphasis · Athletic · Home",
            OnboardingPreviewCopy.headline(answers, plan),
        )
        val rest = plan.days.first { it.isRest }
        assertEquals("Rest", OnboardingPreviewCopy.dayLine(rest, null).second)
    }

    @Test
    fun cardioFocusGeneratesNoLiftWeek() {
        val answers = OnboardingAnswers(focus = TrainingFocus.CARDIO, daysPerWeek = 4)
        val plan = RoutineGenerator.generate(answers, catalog)
        assertTrue(plan.routines.isEmpty())
        assertEquals(0, plan.trainingDayCount)
        assertTrue(plan.days.all { it.isRest })
        assertEquals(
            "Cardio logging is ready. A lift week is not generated.",
            OnboardingPreviewCopy.headline(answers, plan),
        )
    }

    private fun BlueprintLift.family(): String =
        catalog.first { it.id == exerciseId }.movementKey.orEmpty()

    private fun PlanBlueprint.trainingKinds(): List<SessionFocusKind> =
        days.filterNot { it.isRest }.map { day -> routineFor(day)!!.focusKind }

    private fun BlueprintLift.isSquatOrHinge(): Boolean {
        val key = catalog.first { it.id == exerciseId }.movementKey
        return key in setOf(
            "squat",
            "deadlift",
            "romanian-deadlift",
            "leg-press",
            "lunge",
            "good-morning",
            "kettlebell-swing",
        )
    }
}

/**
 * The split is derived, never asked. These are the rules that make that defensible.
 */
class SplitDerivationTest {
    private fun answers(age: TrainingAge, days: Int, goal: TrainingGoal = TrainingGoal.GENERAL) =
        OnboardingAnswers(trainingAge = age, daysPerWeek = days, goal = goal)

    @Test
    fun oneTwoOrThreeDaysIsAlwaysFullBody() {
        TrainingAge.entries.forEach { age ->
            listOf(1, 2, 3).forEach { days ->
                assertEquals(
                    "$age at $days days",
                    SplitStyle.FULL_BODY,
                    SplitDerivation.forAnswers(answers(age, days)),
                )
            }
        }
    }

    @Test
    fun aNewLifterNeverGetsPushPullLegs() {
        // Six separate sessions to learn at once, for no benefit while the weights are light.
        (1..7).forEach { days ->
            assertTrue(
                "a new lifter was given PPL at $days days",
                SplitDerivation.forAnswers(answers(TrainingAge.NEW, days)) != SplitStyle.PUSH_PULL_LEGS,
            )
        }
    }

    @Test
    fun fiveToSevenDaysWithExperienceGetsPushPullLegs() {
        listOf(5, 6, 7).forEach { days ->
            assertEquals(
                SplitStyle.PUSH_PULL_LEGS,
                SplitDerivation.forAnswers(answers(TrainingAge.EXPERIENCED, days)),
            )
        }
    }

    @Test
    fun strengthPrefersFewerBiggerSessions() {
        assertEquals(
            SplitStyle.UPPER_LOWER,
            SplitDerivation.forAnswers(answers(TrainingAge.EXPERIENCED, 6, TrainingGoal.STRENGTH)),
        )
    }

    @Test
    fun athleticIsNotPushPullLegsAtFiveDays() {
        listOf(TrainingAge.NEW, TrainingAge.RETURNING, TrainingAge.EXPERIENCED).forEach { age ->
            assertTrue(
                "$age athletic at 5 days was PPL",
                SplitDerivation.forAnswers(answers(age, 5, TrainingGoal.ATHLETIC))
                    != SplitStyle.PUSH_PULL_LEGS,
            )
        }
    }

    @Test
    fun theSplitIsNeverAutoOrCustom() {
        // Both are resolution strategies for a lifter who already has routines. Handing one to
        // someone with none would put them straight back on the fallback path.
        TrainingAge.entries.forEach { age ->
            (1..7).forEach { days ->
                TrainingGoal.entries.forEach { goal ->
                    val style = SplitDerivation.forAnswers(answers(age, days, goal))
                    assertTrue("$age/$days/$goal produced $style", style != SplitStyle.AUTO && style != SplitStyle.CUSTOM)
                }
            }
        }
    }
}

/**
 * The generated programs and their review document cannot drift apart.
 *
 * Same bet the catalog artifact makes, on a subject where it matters more: which lifts go in a
 * session at what sets and reps is a training opinion, and an opinion nobody can read is an
 * opinion nobody can disagree with. Change a template without re-rendering and this fails.
 */
class PlanReviewArtifactTest {
    @Test
    fun artifactMatchesTheGenerator() {
        val expected = PlanReviewRenderer.render()
        val name = PlanReviewRenderer.artifactName()
        val file = listOf(
            java.io.File("../docs/artifacts/$name"),
            java.io.File("docs/artifacts/$name"),
        ).firstOrNull { it.exists() }
        assertNotNull("$name is not committed", file)
        assertEquals(
            "Re-render docs/artifacts/$name with tools/render-artifacts.sh",
            expected,
            file!!.readText(),
        )
    }
}
