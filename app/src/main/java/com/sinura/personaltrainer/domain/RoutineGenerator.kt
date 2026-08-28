package com.sinura.personaltrainer.domain


/**
 * Turns six answers into a week of real lifts.
 *
 * This is the piece the guided setup exists for. Everything around it already worked: the
 * planner could propose "Upper" on a Tuesday, and the schedule could pin it — but starting
 * that day produced a session named "Upper" containing nothing, because no routine existed to
 * fill it. A focus without lifts is a label, and the lifter still had to go and build the
 * program by hand. This builds it.
 *
 * Nothing here invents training science. It composes four things that already ship:
 *
 * - [WeeklySchedulePlanner.slotKinds] for the order of sessions in a week, so the setup and
 *   the planner cannot disagree about what a four-day upper/lower week looks like.
 * - The catalog's `movementKey` families, so a slot asks for "a row" and the catalog answers
 *   with the best row the lifter can actually reach.
 * - [CatalogMeta.sortRank] to decide which lift in a family is the best answer — the same
 *   order the library presents, so the app's idea of a good lift is one idea.
 * - [AddDefaults] for sets, reps and rest, so a generated lift and a hand-added one are sized
 *   by the same rule.
 *
 * The only judgment original to this file is which families make up a session, and in what
 * order — which is the part a lifter would otherwise be guessing at.
 */
object RoutineGenerator {

    /**
     * A slot in a session template: the families that can fill it, best first.
     *
     * Alternates are not preferences, they are availability. Someone training at home has no
     * bench-press family lift, and the slot should become a push-up rather than vanish — a
     * five-lift session that silently returns three because a filter matched nothing is how a
     * generated program quietly becomes useless.
     */
    private data class Slot(val families: List<String>)

    private fun slot(vararg families: String) = Slot(families.toList())

    /**
     * Where a session goes when its slots run dry.
     *
     * A template describes the session someone with a gym would train. A bodyweight-only
     * lifter has no overhead-press family and no curl family at all, so two of six upper-body
     * slots resolve to nothing and the "six-lift session" arrives with four in it. Nothing
     * throws; it just quietly stops being a program.
     *
     * The fallback pool is drawn from cyclically, taking the next-best UNUSED lift each pass —
     * so the second pass over `push-up` yields the diamond push-up rather than nothing. That
     * is also what a bodyweight program genuinely looks like: variants of the same pattern,
     * because the pattern is what you have.
     *
     * [SPECIALTY_FILL] is appended to every pool so a Hyper-Pro-only week can still reach
     * six lifts: the bench has a push-up, a row, nordics, and a lot of posterior-chain
     * work, but it does not have pulldown / dip / carry / plank families.
     */
    private val SPECIALTY_FILL: List<String> = listOf(
        "push-up", "row", "curl", "rear-delt", "pullover", "shrug",
        "sit-up", "twist", "nordic-curl", "reverse-hyper", "glute-ham-raise",
        "reverse-nordic", "ql-raise", "back-extension", "hip-thrust",
        "leg-curl", "squat", "lunge", "leg-raise", "leg-extension", "calf-raise",
    )

    private fun pool(vararg families: String): List<String> =
        (families.toList() + SPECIALTY_FILL).distinct()

    private val FALLBACKS: Map<SessionFocusKind, List<String>> = mapOf(
        SessionFocusKind.PUSH to pool("push-up", "dip", "chest-fly", "lateral-raise", "triceps-extension", "plank"),
        SessionFocusKind.PULL to pool("pull-up", "row", "pulldown", "curl", "rear-delt", "shrug", "carry"),
        SessionFocusKind.LEGS to pool(
            "squat", "lunge", "leg-press", "calf-raise", "hip-thrust", "back-extension",
            "leg-raise", "nordic-curl", "glute-ham-raise", "reverse-hyper", "reverse-nordic",
        ),
        SessionFocusKind.UPPER to pool("push-up", "pull-up", "row", "dip", "curl", "triceps-extension", "lateral-raise", "plank"),
        SessionFocusKind.LOWER to pool(
            "squat", "lunge", "calf-raise", "back-extension", "nordic-curl", "leg-raise",
            "plank", "glute-ham-raise", "reverse-hyper", "reverse-nordic", "ql-raise",
        ),
        SessionFocusKind.FULL_BODY to pool(
            "squat", "push-up", "row", "pull-up", "plank", "calf-raise", "back-extension",
            "leg-raise", "nordic-curl", "reverse-hyper",
        ),
        SessionFocusKind.RECOVERY to pool("plank", "dead-bug", "carry", "ql-raise", "sit-up"),
    )

    /**
     * What each kind of session is made of, in the order the lifts should be performed.
     *
     * Compound and heaviest first, isolation last — the ordering every serious program shares,
     * and the reason is mechanical rather than stylistic: the lift that demands the most from
     * you is the one that should meet you fresh.
     */
    private val TEMPLATES: Map<SessionFocusKind, List<Slot>> = mapOf(
        SessionFocusKind.PUSH to listOf(
            slot("bench-press", "push-up", "dip"),
            slot("overhead-press", "lateral-raise"),
            slot("chest-fly", "push-up", "dip"),
            slot("triceps-extension", "dip", "push-up"),
            slot("lateral-raise", "rear-delt"),
            slot("rear-delt", "lateral-raise"),
        ),
        SessionFocusKind.PULL to listOf(
            slot("row", "pull-up", "pulldown"),
            slot("pulldown", "pull-up", "row"),
            slot("rear-delt", "shrug"),
            slot("curl"),
            slot("shrug", "pullover", "carry"),
            slot("pullover", "curl"),
        ),
        SessionFocusKind.LEGS to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("romanian-deadlift", "leg-curl", "good-morning", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("leg-press", "lunge", "step-up", "squat"),
            slot("leg-curl", "nordic-curl", "back-extension"),
            slot("calf-raise"),
            slot("hip-thrust", "glute-kickback", "hip-abduction"),
        ),
        SessionFocusKind.UPPER to listOf(
            slot("bench-press", "push-up", "dip"),
            slot("row", "pull-up", "pulldown"),
            slot("overhead-press", "lateral-raise"),
            slot("pulldown", "pull-up", "row"),
            slot("curl"),
            slot("triceps-extension", "dip", "push-up"),
        ),
        SessionFocusKind.LOWER to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("romanian-deadlift", "leg-curl", "good-morning", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("lunge", "leg-press", "step-up", "squat"),
            slot("leg-curl", "nordic-curl", "back-extension"),
            slot("calf-raise"),
            slot("plank", "dead-bug", "leg-raise"),
        ),
        SessionFocusKind.FULL_BODY to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("bench-press", "push-up", "dip"),
            slot("row", "pull-up", "pulldown"),
            slot("overhead-press", "lateral-raise"),
            slot("romanian-deadlift", "leg-curl", "nordic-curl", "back-extension", "glute-ham-raise", "reverse-hyper"),
            slot("plank", "dead-bug", "crunch"),
        ),
        // Never generated as a training day — a recovery slot is a rest day with a name. Kept
        // so the map is total over the enum and a future caller cannot fall off it.
        SessionFocusKind.RECOVERY to listOf(
            slot("plank", "dead-bug"),
            slot("back-extension", "hip-thrust", "reverse-hyper", "ql-raise"),
            slot("carry", "calf-raise"),
        ),
    )

    /**
     * Strength week: compounds keep the slots. Fly / curl / lateral exist as
     * fallbacks, not as the session's idea of work. Same kinds as [TEMPLATES]
     * so the split does not change — only what fills it.
     */
    private val STRENGTH_TEMPLATES: Map<SessionFocusKind, List<Slot>> = mapOf(
        SessionFocusKind.PUSH to listOf(
            slot("bench-press", "push-up", "dip"),
            slot("overhead-press", "dip", "push-up"),
            slot("dip", "push-up", "bench-press"),
            slot("overhead-press", "bench-press", "dip"),
            slot("carry", "push-up", "dip"),
            slot("triceps-extension", "plank", "push-up"),
        ),
        SessionFocusKind.PULL to listOf(
            slot("row", "pull-up", "pulldown"),
            slot("pulldown", "pull-up", "row"),
            slot("pull-up", "row", "pulldown"),
            slot("shrug", "row", "carry"),
            slot("carry", "shrug", "row"),
            slot("pullover", "plank", "row"),
        ),
        SessionFocusKind.LEGS to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("romanian-deadlift", "deadlift", "good-morning", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("leg-press", "lunge", "step-up", "squat"),
            slot("lunge", "step-up", "leg-press"),
            slot("back-extension", "hip-thrust", "nordic-curl", "reverse-hyper", "ql-raise"),
            slot("calf-raise"),
        ),
        SessionFocusKind.UPPER to listOf(
            slot("bench-press", "push-up", "dip"),
            slot("row", "pull-up", "pulldown"),
            slot("overhead-press", "dip", "push-up"),
            slot("pulldown", "pull-up", "row"),
            slot("dip", "push-up", "bench-press"),
            slot("carry", "shrug", "row"),
        ),
        SessionFocusKind.LOWER to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("romanian-deadlift", "deadlift", "good-morning", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("lunge", "leg-press", "step-up", "squat"),
            slot("leg-press", "step-up", "lunge"),
            slot("back-extension", "hip-thrust", "nordic-curl", "reverse-hyper", "ql-raise"),
            slot("calf-raise"),
        ),
        SessionFocusKind.FULL_BODY to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("bench-press", "push-up", "dip"),
            slot("row", "pull-up", "pulldown"),
            slot("romanian-deadlift", "deadlift", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("overhead-press", "dip", "push-up"),
            slot("carry", "plank", "dead-bug"),
        ),
        SessionFocusKind.RECOVERY to listOf(
            slot("plank", "dead-bug"),
            slot("back-extension", "hip-thrust", "reverse-hyper", "ql-raise"),
            slot("carry", "calf-raise"),
        ),
    )

    private val STRENGTH_FULL_BODY_B: List<Slot> = listOf(
        slot("deadlift", "romanian-deadlift", "kettlebell-swing", "back-extension"),
        slot("overhead-press", "dip", "push-up"),
        slot("pull-up", "pulldown", "row"),
        slot("lunge", "leg-press", "step-up", "squat"),
        slot("dip", "bench-press", "push-up"),
        slot("carry", "plank", "dead-bug"),
    )

    private val STRENGTH_FALLBACKS: Map<SessionFocusKind, List<String>> = mapOf(
        SessionFocusKind.PUSH to pool(
            "push-up", "dip", "bench-press", "overhead-press", "carry", "plank",
        ),
        SessionFocusKind.PULL to pool(
            "pull-up", "row", "pulldown", "shrug", "carry", "plank",
        ),
        SessionFocusKind.LEGS to pool(
            "squat", "lunge", "leg-press", "romanian-deadlift", "step-up",
            "back-extension", "calf-raise", "nordic-curl", "plank",
        ),
        SessionFocusKind.UPPER to pool(
            "push-up", "pull-up", "row", "dip", "overhead-press", "carry", "shrug", "plank",
        ),
        SessionFocusKind.LOWER to pool(
            "squat", "lunge", "leg-press", "romanian-deadlift", "step-up",
            "back-extension", "calf-raise", "nordic-curl", "plank",
        ),
        SessionFocusKind.FULL_BODY to pool(
            "squat", "push-up", "row", "pull-up", "lunge", "overhead-press",
            "carry", "dip", "plank", "back-extension",
        ),
        SessionFocusKind.RECOVERY to pool("plank", "dead-bug", "carry"),
    )

    /**
     * The second full-body session.
     *
     * Full body is the only split that repeats one session shape three times a week, and doing
     * the identical five lifts every time is both dull and worse training. Two variants
     * alternate: the same movement patterns, different lifts. Every other split already varies
     * by construction, because its days are different sessions.
     */
    private val FULL_BODY_B: List<Slot> = listOf(
        slot("deadlift", "romanian-deadlift", "kettlebell-swing", "back-extension"),
        slot("overhead-press", "dip", "push-up"),
        slot("pull-up", "pulldown", "row"),
        slot("lunge", "leg-press", "step-up", "squat"),
        slot("chest-fly", "push-up"),
        slot("carry", "plank", "dead-bug"),
    )

    /**
     * Resilience week: nordic, reverse hyper, reverse nordic, GHR first.
     *
     * Tendon and hip-flexor work wants the lengthened hamstring and quad patterns the Hyper
     * Pro exists for, not a hypertrophy accessory stack. Gym-only still fills from nordic-curl
     * and back-extension; Hyper Pro fills the rest.
     */
    private val RESILIENCE_TEMPLATES: Map<SessionFocusKind, List<Slot>> = TEMPLATES + mapOf(
        SessionFocusKind.LEGS to listOf(
            slot("nordic-curl", "glute-ham-raise", "reverse-hyper", "back-extension"),
            slot("reverse-nordic", "squat", "lunge", "leg-extension"),
            slot("reverse-hyper", "glute-ham-raise", "hip-thrust"),
            slot("leg-curl", "nordic-curl", "romanian-deadlift"),
            slot("ql-raise", "back-extension", "sit-up"),
            slot("calf-raise", "leg-raise"),
        ),
        SessionFocusKind.LOWER to listOf(
            slot("nordic-curl", "glute-ham-raise", "reverse-hyper", "back-extension"),
            slot("reverse-nordic", "squat", "lunge"),
            slot("reverse-hyper", "hip-thrust", "glute-ham-raise"),
            slot("ql-raise", "back-extension", "sit-up"),
            slot("leg-curl", "nordic-curl"),
            slot("leg-raise", "sit-up", "plank"),
        ),
        SessionFocusKind.FULL_BODY to listOf(
            slot("nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("push-up", "bench-press", "dip"),
            slot("row", "pull-up", "pulldown"),
            slot("reverse-nordic", "squat", "lunge"),
            slot("ql-raise", "back-extension", "sit-up"),
            slot("plank", "leg-raise"),
        ),
        SessionFocusKind.RECOVERY to listOf(
            slot("ql-raise", "back-extension", "reverse-hyper"),
            slot("reverse-nordic", "lunge"),
            slot("sit-up", "leg-raise", "plank"),
        ),
    )

    private val RESILIENCE_FULL_BODY_B: List<Slot> = listOf(
        slot("reverse-hyper", "back-extension", "glute-ham-raise", "nordic-curl"),
        slot("overhead-press", "dip", "push-up"),
        slot("row", "pull-up", "pulldown"),
        slot("reverse-nordic", "lunge", "squat"),
        slot("sit-up", "leg-raise", "ql-raise"),
        slot("plank", "dead-bug"),
    )

    private val RESILIENCE_FALLBACKS: Map<SessionFocusKind, List<String>> =
        FALLBACKS.mapValues { (kind, list) ->
            val extra = when (kind) {
                SessionFocusKind.LEGS, SessionFocusKind.LOWER, SessionFocusKind.FULL_BODY,
                SessionFocusKind.RECOVERY,
                -> listOf(
                    "nordic-curl", "reverse-hyper", "glute-ham-raise", "reverse-nordic",
                    "ql-raise", "sit-up",
                )
                else -> emptyList()
            }
            (extra + list).distinct()
        }

    /**
     * Athletic week: same session kinds, different families.
     *
     * Hinge, carry, lunge, step-up, swing first. Curl / fly / lateral only as last resort,
     * which is what the fallbacks are for when the catalog cannot answer a slot.
     */
    private val ATHLETIC_TEMPLATES: Map<SessionFocusKind, List<Slot>> = mapOf(
        SessionFocusKind.PUSH to listOf(
            slot("bench-press", "push-up", "dip"),
            slot("overhead-press", "dip", "push-up"),
            slot("dip", "push-up", "bench-press"),
            slot("carry", "push-up", "dip"),
            slot("push-up", "dip"),
            slot("dip", "push-up"),
        ),
        SessionFocusKind.PULL to listOf(
            slot("row", "pull-up", "pulldown"),
            slot("pull-up", "pulldown", "row"),
            slot("carry", "row", "pull-up"),
            slot("pulldown", "row", "pull-up"),
            slot("shrug", "pullover", "carry"),
            slot("carry", "shrug"),
        ),
        SessionFocusKind.LEGS to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("romanian-deadlift", "deadlift", "kettlebell-swing", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("lunge", "step-up", "leg-press", "squat"),
            slot("step-up", "lunge", "kettlebell-swing"),
            slot("kettlebell-swing", "back-extension", "nordic-curl"),
            slot("carry", "calf-raise", "plank"),
        ),
        SessionFocusKind.UPPER to listOf(
            slot("bench-press", "push-up", "dip"),
            slot("row", "pull-up", "pulldown"),
            slot("pull-up", "overhead-press", "dip"),
            slot("carry", "dip", "push-up"),
            slot("overhead-press", "dip", "push-up"),
            slot("dip", "push-up"),
        ),
        SessionFocusKind.LOWER to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("romanian-deadlift", "deadlift", "kettlebell-swing", "nordic-curl", "glute-ham-raise", "reverse-hyper"),
            slot("lunge", "step-up", "leg-press", "squat"),
            slot("step-up", "lunge", "kettlebell-swing"),
            slot("kettlebell-swing", "back-extension", "nordic-curl"),
            slot("carry", "plank", "dead-bug"),
        ),
        SessionFocusKind.FULL_BODY to listOf(
            slot("squat", "leg-press", "lunge", "reverse-nordic"),
            slot("bench-press", "push-up", "dip"),
            slot("pull-up", "row", "pulldown"),
            slot("kettlebell-swing", "romanian-deadlift", "deadlift"),
            slot("romanian-deadlift", "lunge", "step-up"),
            slot("carry", "plank", "dead-bug"),
        ),
        SessionFocusKind.RECOVERY to listOf(
            slot("carry", "plank", "dead-bug"),
            slot("back-extension", "hip-thrust", "reverse-hyper", "ql-raise"),
            slot("kettlebell-swing", "calf-raise"),
        ),
    )

    private val ATHLETIC_FULL_BODY_B: List<Slot> = listOf(
        slot("deadlift", "romanian-deadlift", "kettlebell-swing", "back-extension"),
        slot("overhead-press", "dip", "push-up"),
        slot("pull-up", "row", "pulldown"),
        slot("lunge", "step-up", "leg-press", "squat"),
        slot("kettlebell-swing", "step-up", "lunge"),
        slot("carry", "plank", "dead-bug"),
    )

    private val ATHLETIC_FALLBACKS: Map<SessionFocusKind, List<String>> = mapOf(
        SessionFocusKind.PUSH to pool("push-up", "dip", "bench-press", "overhead-press", "carry", "plank"),
        SessionFocusKind.PULL to pool("pull-up", "row", "pulldown", "carry", "shrug", "plank"),
        SessionFocusKind.LEGS to pool(
            "squat", "lunge", "step-up", "kettlebell-swing", "nordic-curl",
            "back-extension", "carry", "calf-raise", "leg-raise", "plank",
        ),
        SessionFocusKind.UPPER to pool(
            "push-up", "pull-up", "row", "dip", "carry", "overhead-press", "plank",
        ),
        SessionFocusKind.LOWER to pool(
            "squat", "lunge", "step-up", "kettlebell-swing", "nordic-curl",
            "back-extension", "carry", "leg-raise", "plank",
        ),
        SessionFocusKind.FULL_BODY to pool(
            "squat", "push-up", "row", "pull-up", "lunge", "kettlebell-swing",
            "carry", "dip", "plank", "back-extension", "leg-raise",
        ),
        SessionFocusKind.RECOVERY to pool("carry", "plank", "dead-bug"),
    )

    /**
     * Builds the proposal.
     *
     * @param catalog the exercises to choose from — the built-in catalog in practice, passed in so
     * this stays pure and so a test can hand it a deliberately sparse catalog.
     */
    fun generate(
        answers: OnboardingAnswers,
        catalog: List<Exercise>,
        weekStart: Weekday = Weekday.MONDAY,
    ): PlanBlueprint {
        val clean = answers.sanitized()
        if (clean.focus == TrainingFocus.CARDIO) {
            val days = (0 until 7).map { offset ->
                BlueprintDay(dayOfWeek = weekStart.plus(offset.toLong()), routineKey = null)
            }
            return PlanBlueprint(
                splitStyle = SplitDerivation.forAnswers(clean),
                routines = emptyList(),
                days = days,
                trace = RuleTrace.forGeneration(clean, SplitDerivation.forAnswers(clean)),
            )
        }
        val split = SplitDerivation.forAnswers(clean)
        val kinds = WeeklySchedulePlanner.slotKinds(
            split,
            clean.daysPerWeek,
            routines = emptyList(),
            emphasis = clean.emphasis,
        )
        val allowed = catalog.filter { it.equipment in clean.equipment() }

        val routines = buildRoutines(kinds, split, clean, allowed)
        val days = layOutWeek(clean, kinds, routines, weekStart)
        return PlanBlueprint(
            splitStyle = split,
            routines = routines,
            days = days,
            trace = RuleTrace.forGeneration(clean, split),
        )
    }

    /**
     * One routine per distinct session in the cycle — not one per training day.
     *
     * A four-day upper/lower week is two routines used twice, which is what the lifter would
     * build by hand and what makes the Plan tab readable. The exception is full body, where
     * the A/B pair is the point.
     */
    private fun buildRoutines(
        kinds: List<SessionFocusKind>,
        split: SplitStyle,
        answers: OnboardingAnswers,
        allowed: List<Exercise>,
    ): List<BlueprintRoutine> {
        val liftsPerSession = answers.trainingAge.liftsPerSession
        if (split == SplitStyle.FULL_BODY) {
            val variants = buildList {
                add("full-body-a" to templateFor(SessionFocusKind.FULL_BODY, answers.goal))
                // A one-day week never reaches B. Keep the pair once there are two days so
                // the rotation is honest the moment a third day is added.
                if (answers.daysPerWeek >= 2) {
                    add("full-body-b" to fullBodyBFor(answers.goal))
                }
            }
            return variants.mapIndexed { index, (key, template) ->
                BlueprintRoutine(
                    key = key,
                    name = "Full Body ${'A' + index}",
                    focusKind = SessionFocusKind.FULL_BODY,
                    lifts = fill(
                        remixFullBody(template, answers.emphasis),
                        fallbacksFor(SessionFocusKind.FULL_BODY, answers.goal),
                        liftsPerSession,
                        allowed,
                        SessionDose.from(answers),
                    ),
                )
            }
        }
        return kinds.distinct().map { kind ->
            BlueprintRoutine(
                key = kind.name.lowercase(),
                name = kind.label,
                focusKind = kind,
                lifts = fill(
                    templateFor(kind, answers.goal),
                    fallbacksFor(kind, answers.goal),
                    liftsPerSession,
                    allowed,
                    SessionDose.from(answers),
                ),
            )
        }
    }

    /**
     * Resolves a template into actual lifts.
     *
     * Walks the slots in order and takes the best available lift from the first family that
     * has one, skipping anything already chosen for this session. It keeps going past [target]
     * slots only if earlier ones came up empty, which is what stops a bodyweight-only lifter
     * getting a three-lift "session" because half the families needed a barbell.
     */
    private fun fill(
        template: List<Slot>,
        fallback: List<String>,
        target: Int,
        allowed: List<Exercise>,
        dose: SessionDose,
    ): List<BlueprintLift> {
        val chosen = LinkedHashMap<String, Exercise>()
        for (slot in template) {
            if (chosen.size >= target) break
            val pick = slot.families
                .asSequence()
                .mapNotNull { family -> bestIn(family, allowed, exclude = chosen.keys) }
                .firstOrNull()
            if (pick != null) chosen[pick.id] = pick
        }
        // Top up from the pool, cycling until a whole pass finds nothing new. Stopping on the
        // first empty family would give up while later ones still had lifts; not stopping at
        // all would not terminate on a sparse catalog.
        while (chosen.size < target) {
            var added = false
            for (family in fallback) {
                if (chosen.size >= target) break
                val pick = bestIn(family, allowed, exclude = chosen.keys) ?: continue
                chosen[pick.id] = pick
                added = true
            }
            if (!added) break
        }
        return chosen.values.mapIndexed { index, exercise ->
            BlueprintLift(
                exerciseId = exercise.id,
                name = exercise.name,
                equipment = exercise.equipment,
                // The opening lifts are what the session is built around; everything after
                // them is accessory work. Without this a four-lift leg day asked for four
                // separate movements at 3 x 5 with 150 seconds' rest, which is not a leg day,
                // it is four main lifts wearing one.
                targets = ProgramDose.forExercise(
                    exercise,
                    if (index < PRIMARY_LIFTS_PER_SESSION) LiftRole.PRIMARY else LiftRole.ACCESSORY,
                    dose,
                ),
            )
        }
    }

    /**
     * Full-body weeks do not steal a whole day. Emphasis swaps one lower-priority slot
     * so the opener — squat or hinge — never leaves the session.
     */
    private fun remixFullBody(
        template: List<Slot>,
        emphasis: TrainingEmphasis,
    ): List<Slot> {
        if (emphasis == TrainingEmphasis.BALANCED) return template
        val start = 1
        val index = when (emphasis) {
            TrainingEmphasis.UPPER ->
                template.indices.lastOrNull { it >= start && isLowerFamily(template[it].families.first()) }
            TrainingEmphasis.LOWER ->
                template.indices.lastOrNull { it >= start && isUpperFamily(template[it].families.first()) }
            TrainingEmphasis.BALANCED -> null
        } ?: return template
        val replacement = when (emphasis) {
            TrainingEmphasis.UPPER -> slot("overhead-press", "row", "bench-press", "pull-up")
            TrainingEmphasis.LOWER -> slot("lunge", "hip-thrust", "step-up", "leg-press")
            TrainingEmphasis.BALANCED -> template[index]
        }
        return template.toMutableList().also { it[index] = replacement }
    }

    /** The catalog's own idea of the best lift in a family — the order the library shows. */
    private fun bestIn(family: String, allowed: List<Exercise>, exclude: Set<String>): Exercise? =
        allowed
            .filter { it.movementKey == family && it.id !in exclude }
            .minByOrNull { CatalogMeta.sortRank(it.id) }

    /**
     * Places the sessions on days.
     *
     * A day the lifter picked always wins — they know their week and the app does not. Only
     * when they pick fewer days than they said they would train does
     * [WeeklySchedulePlanner.trainingDayIndices] fill the rest, which is the same spacing the
     * planner uses everywhere else, so a hand-picked week and a suggested one look alike.
     */
    private fun layOutWeek(
        answers: OnboardingAnswers,
        kinds: List<SessionFocusKind>,
        routines: List<BlueprintRoutine>,
        weekStart: Weekday,
    ): List<BlueprintDay> {
        val week = (0 until DAYS_IN_WEEK).map { offset -> weekStart.plus(offset.toLong()) }
        val picked = week.filter { it in answers.preferredDays }
        val spaced = WeeklySchedulePlanner.trainingDayIndices(answers.daysPerWeek).map { week[it] }
        val training = (picked + spaced.filterNot { it in picked })
            .take(answers.daysPerWeek)
            .toSet()

        // Assign in week order so the cycle reads left to right the way it will be trained.
        val cycle = if (routines.size == 1) routines else routines
        var next = 0
        return week.map { day ->
            if (day !in training) {
                BlueprintDay(dayOfWeek = day, routineKey = null)
            } else {
                val kind = kinds.getOrNull(next % kinds.size)
                val routine = cycle.firstOrNull { it.focusKind == kind && it.key == keyFor(kind, next, cycle) }
                    ?: cycle[next % cycle.size]
                next += 1
                BlueprintDay(dayOfWeek = day, routineKey = routine.key)
            }
        }
    }

    /**
     * Which of a kind's routines this session gets. Only full body has more than one, and it
     * alternates; every other kind resolves to its single routine.
     */
    private fun keyFor(kind: SessionFocusKind?, index: Int, routines: List<BlueprintRoutine>): String? {
        val ofKind = routines.filter { it.focusKind == kind }
        if (ofKind.isEmpty()) return null
        return ofKind[index % ofKind.size].key
    }

    private const val DAYS_IN_WEEK = 7

    /**
     * How many lifts open a session as main work.
     *
     * Two. One is a session with a single hard lift and a lot of filler; three leaves no room
     * for accessories in a four-lift beginner day. Two also happens to be what every template
     * here opens with — a big lower or push movement and its opposite.
     */
    private const val PRIMARY_LIFTS_PER_SESSION = 2

    private fun templateFor(kind: SessionFocusKind, goal: TrainingGoal): List<Slot> =
        when (goal) {
            TrainingGoal.ATHLETIC -> ATHLETIC_TEMPLATES.getValue(kind)
            TrainingGoal.STRENGTH -> STRENGTH_TEMPLATES.getValue(kind)
            TrainingGoal.RESILIENCE -> RESILIENCE_TEMPLATES.getValue(kind)
            else -> TEMPLATES.getValue(kind)
        }

    private fun fullBodyBFor(goal: TrainingGoal): List<Slot> =
        when (goal) {
            TrainingGoal.ATHLETIC -> ATHLETIC_FULL_BODY_B
            TrainingGoal.STRENGTH -> STRENGTH_FULL_BODY_B
            TrainingGoal.RESILIENCE -> RESILIENCE_FULL_BODY_B
            else -> FULL_BODY_B
        }

    private fun fallbacksFor(kind: SessionFocusKind, goal: TrainingGoal): List<String> =
        when (goal) {
            TrainingGoal.ATHLETIC -> ATHLETIC_FALLBACKS.getValue(kind)
            TrainingGoal.STRENGTH -> STRENGTH_FALLBACKS.getValue(kind)
            TrainingGoal.RESILIENCE -> RESILIENCE_FALLBACKS.getValue(kind)
            else -> FALLBACKS.getValue(kind)
        }

    private fun isLowerFamily(family: String): Boolean = family in LOWER_FAMILIES

    private fun isUpperFamily(family: String): Boolean = family in UPPER_FAMILIES

    private val LOWER_FAMILIES = setOf(
        "squat",
        "lunge",
        "leg-press",
        "calf-raise",
        "hip-thrust",
        "back-extension",
        "leg-raise",
        "romanian-deadlift",
        "deadlift",
        "nordic-curl",
        "good-morning",
        "step-up",
        "glute-kickback",
        "hip-abduction",
        "leg-curl",
        "kettlebell-swing",
        "reverse-hyper",
        "glute-ham-raise",
        "reverse-nordic",
        "ql-raise",
    )

    private val UPPER_FAMILIES = setOf(
        "bench-press",
        "push-up",
        "dip",
        "overhead-press",
        "lateral-raise",
        "chest-fly",
        "triceps-extension",
        "rear-delt",
        "row",
        "pull-up",
        "pulldown",
        "curl",
        "shrug",
        "pullover",
    )
}
