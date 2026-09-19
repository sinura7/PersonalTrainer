package com.sinura.personaltrainer.domain


object WeeklySchedulePlanner {
    const val THIN_HISTORY_SESSIONS = 3
    private const val RECENT_SESSION_HOURS = 48L

    /**
     * Proposes a week. It no longer decides one.
     *
     * This used to run on every insights emission and its output WAS the plan, which is why
     * the week reshuffled whenever anything was logged. It is now called from exactly one
     * place — the Plan tab's "Suggest a week" — and what it produces is a preview that
     * persists only when the user accepts it.
     *
     * [pinnedSlots] is what the user has already decided. Days those slots occupy are echoed
     * back untouched (carrying their `slotId`) and are never proposed over: a suggestion that
     * could overwrite a pin is not a suggestion.
     */
    fun plan(
        preferences: SchedulePreferences,
        snapshot: BodyHeatSnapshot,
        recommendations: List<TrainingRecommendation>,
        routines: List<Routine>,
        recentSessions: List<WorkoutSession>,
        nowMs: Long,
        time: TimePort,
        zoneId: String = time.defaultZoneId(),
        pinnedSlots: List<ScheduleSlot> = emptyList(),
        emphasis: TrainingEmphasis = TrainingEmphasis.BALANCED,
        preferredDays: Set<Weekday> = emptySet(),
    ): WeeklySchedulePlan {
        val prefs = preferences.sanitized()
        val today = time.civilDate(nowMs, zoneId)
        val weekStart = today.previousOrSame(prefs.weekStart)
        val dates = (0..6).map { weekStart.plusDays(it.toLong()) }
        val derived = WeekDerivation.derive(
            slots = pinnedSlots,
            history = recentSessions,
            preferences = prefs,
            nowMs = nowMs,
            time = time,
            zoneId = zoneId,
        )
        val pinnedByDay = WeekDerivation
            .toWeeklySchedulePlan(derived, routines, prefs, nowMs)
            .days
            .filterNot { it.isRest }
            .associateBy { it.epochDay }
        val usableRoutines = routines.filter { it.exercises.isNotEmpty() }
        val finished = recentSessions.filter { it.isFinished }
        val thinHistory = !snapshot.hasAnyWorkingSets || finished.size < THIN_HISTORY_SESSIONS
        val resolved = resolveSplit(prefs, usableRoutines)
        val trainIndices = trainingOffsets(
            count = prefs.trainingDaysPerWeek,
            weekStart = prefs.weekStart,
            preferredDays = preferredDays,
        )
        val kinds = arrangeKinds(
            kinds = slotKinds(resolved, prefs.trainingDaysPerWeek, usableRoutines, emphasis),
            lastFocus = recentFocus(finished, nowMs),
        )
        val usedRoutineIds = linkedSetOf<String>()
        val days = dates.mapIndexed { index, date ->
            val pinned = pinnedByDay[date.epochDay]
            val slot = trainIndices.indexOf(index)
            // A day the user already owns is echoed, never proposed over. A day already behind
            // today gets no proposal at all: the planner used to lay a fresh week over the whole
            // calendar including days that had already gone, so a Thursday plan told you to do
            // Monday's session on Monday.
            if (pinned != null) {
                pinned
            } else if (slot < 0 || date < today) {
                restDay(date)
            } else {
                val kind = kinds.getOrElse(slot) { SessionFocusKind.FULL_BODY }
                trainingDay(
                    date = date,
                    kind = kind,
                    snapshot = snapshot,
                    recommendations = recommendations,
                    routines = usableRoutines,
                    usedRoutineIds = usedRoutineIds,
                    thinHistory = thinHistory,
                    resolved = resolved,
                )
            }
        }
        return WeeklySchedulePlan(
            weekStartEpochDay = weekStart.epochDay,
            generatedAtMs = nowMs,
            preferences = prefs,
            resolvedSplit = resolved,
            days = days,
            thinHistory = thinHistory,
            summary = summary(thinHistory, resolved, prefs, snapshot),
        )
    }

    /**
     * Days the lifter picked always win. Empty preferred days keep the
     * default spacing so Suggest and generate still look alike.
     */
    internal fun trainingOffsets(
        count: Int,
        weekStart: Weekday,
        preferredDays: Set<Weekday>,
    ): List<Int> {
        val spaced = trainingDayIndices(count)
        if (preferredDays.isEmpty()) return spaced
        val week = (0 until Weekday.DAYS_IN_WEEK).map { offset -> weekStart.plus(offset.toLong()) }
        val picked = week.mapIndexedNotNull { index, day ->
            if (day in preferredDays) index else null
        }
        return (picked + spaced.filterNot { it in picked })
            .take(count.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS))
    }

    internal fun trainingDayIndices(count: Int): List<Int> = when (count.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)) {
        // Default spacing leaves ~48 hours between hits of the same pattern on 1–4 day
        // weeks (ACSM same-muscle rest for novices). Preferred days still win.
        1 -> listOf(3)
        2 -> listOf(0, 3)
        3 -> listOf(0, 2, 4)
        4 -> listOf(0, 2, 4, 5)
        5 -> listOf(0, 1, 2, 4, 5)
        6 -> listOf(0, 1, 2, 3, 4, 5)
        7 -> listOf(0, 1, 2, 3, 4, 5, 6)
        else -> listOf(0, 2, 4)
    }

    internal fun resolveSplit(preferences: SchedulePreferences, routines: List<Routine>): SplitStyle {
        val prefs = preferences.sanitized()
        if (prefs.splitStyle != SplitStyle.AUTO) return prefs.splitStyle
        val days = prefs.trainingDaysPerWeek
        val hasPush = routines.any { classifyRoutine(it) == SessionFocusKind.PUSH }
        val hasPull = routines.any { classifyRoutine(it) == SessionFocusKind.PULL }
        val hasLegs = routines.any { classifyRoutine(it) in setOf(SessionFocusKind.LEGS, SessionFocusKind.LOWER) }
        return when {
            days <= 2 -> SplitStyle.FULL_BODY
            days == 3 && hasPush && hasPull && hasLegs -> SplitStyle.PUSH_PULL_LEGS
            days == 3 -> SplitStyle.FULL_BODY
            days >= 5 && hasPush && hasPull -> SplitStyle.PUSH_PULL_LEGS
            else -> SplitStyle.UPPER_LOWER
        }
    }

    internal fun slotKinds(
        style: SplitStyle,
        trainingDays: Int,
        routines: List<Routine>,
        emphasis: TrainingEmphasis = TrainingEmphasis.BALANCED,
    ): List<SessionFocusKind> {
        val count = trainingDays.coerceIn(SchedulePreferences.MIN_DAYS, SchedulePreferences.MAX_DAYS)
        val base = when (style) {
            SplitStyle.CUSTOM -> customKinds(count, routines)
            SplitStyle.FULL_BODY -> List(count) { SessionFocusKind.FULL_BODY }
            SplitStyle.UPPER_LOWER -> upperLowerKinds(count)
            SplitStyle.PUSH_PULL_LEGS -> pplKinds(count)
            SplitStyle.AUTO -> slotKinds(SplitStyle.UPPER_LOWER, count, routines)
        }
        return EmphasisLayout.apply(base, emphasis)
    }

    internal fun classifyRoutine(routine: Routine): SessionFocusKind {
        val name = routine.name.lowercase()
        when {
            name.contains("push") -> return SessionFocusKind.PUSH
            name.contains("pull") -> return SessionFocusKind.PULL
            name.contains("full") -> return SessionFocusKind.FULL_BODY
            name.contains("upper") -> return SessionFocusKind.UPPER
            name.contains("lower") || name.contains("leg") -> return SessionFocusKind.LEGS
        }
        val muscles = routine.exercises.map { MuscleNormalizer.primaryOf(it.exercise.muscleGroup) }
        val push = muscles.count { it in PUSH_MUSCLES }
        val pull = muscles.count { it in PULL_MUSCLES }
        val legs = muscles.count { it in LEG_MUSCLES }
        val upper = muscles.count { it.region == MuscleRegion.UPPER }
        val lower = muscles.count { it.region == MuscleRegion.LOWER }
        return when {
            legs >= 2 && push + pull <= 1 -> SessionFocusKind.LEGS
            push >= 2 && push > pull && legs == 0 -> SessionFocusKind.PUSH
            pull >= 2 && pull > push && legs == 0 -> SessionFocusKind.PULL
            upper > 0 && lower > 0 -> SessionFocusKind.FULL_BODY
            lower > upper -> SessionFocusKind.LEGS
            upper > 0 -> SessionFocusKind.UPPER
            else -> SessionFocusKind.FULL_BODY
        }
    }

    internal fun scoreRoutine(routine: Routine, kind: SessionFocusKind): Int {
        val classified = classifyRoutine(routine)
        var score = 0
        if (classified == kind) score += 6
        if (compatible(classified, kind)) score += 3
        val muscles = routine.exercises.map { MuscleNormalizer.primaryOf(it.exercise.muscleGroup) }
        score += muscles.count { it in musclesFor(kind) }
        val name = routine.name.lowercase()
        if (name.contains(kind.label.lowercase().substringBefore(" "))) score += 2
        return score
    }

    private fun trainingDay(
        date: CivilDate,
        kind: SessionFocusKind,
        snapshot: BodyHeatSnapshot,
        recommendations: List<TrainingRecommendation>,
        routines: List<Routine>,
        usedRoutineIds: MutableSet<String>,
        thinHistory: Boolean,
        resolved: SplitStyle,
    ): SuggestedTrainingDay {
        val emphasis = if (thinHistory) emptyList() else emphasisFor(kind, snapshot, recommendations)
        val routine = pickRoutine(kind, routines, usedRoutineIds)
        if (routine != null) usedRoutineIds += routine.id
        val title = focusTitle(kind, emphasis)
        val reason = reasonFor(kind, emphasis, routine, snapshot, thinHistory, resolved)
        val confidence = when {
            thinHistory -> ScheduleConfidence.LOW
            routine != null -> ScheduleConfidence.HIGH
            else -> ScheduleConfidence.MEDIUM
        }
        return SuggestedTrainingDay(
            epochDay = date.epochDay,
            dayOfWeek = date.dayOfWeek,
            isRest = false,
            focusKind = kind,
            focusTitle = title,
            routineId = routine?.id,
            routineName = routine?.name,
            reason = reason,
            emphasisMuscles = emphasis,
            confidence = confidence,
        )
    }

    private fun restDay(date: CivilDate): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = date.epochDay,
        dayOfWeek = date.dayOfWeek,
        isRest = true,
        focusKind = SessionFocusKind.RECOVERY,
        focusTitle = "Rest",
        routineId = null,
        routineName = null,
        reason = "Recovery day so the week stays trainable.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
    )

    private fun emphasisFor(
        kind: SessionFocusKind,
        snapshot: BodyHeatSnapshot,
        recommendations: List<TrainingRecommendation>,
    ): List<CanonicalMuscle> {
        val targets = musclesFor(kind)
        if (targets.isEmpty()) {
            return recommendations.mapNotNull { it.actionMuscle }.distinct().take(2)
        }
        val fromRecs = recommendations
            .mapNotNull { rec -> rec.actionMuscle?.let { rec to it } }
            .filter { it.second in targets }
            .sortedBy { (rec, muscle) ->
                when {
                    rec.id.startsWith("imbalance") -> 0
                    rec.id.startsWith("coverage") -> 1
                    rec.id.startsWith("neglect") && snapshot.load(muscle).lastTrainedAtMs != null -> 2
                    rec.id.startsWith("neglect") -> 3
                    else -> 4
                }
            }
            .map { it.second }
            .distinct()
        if (fromRecs.isNotEmpty()) return fromRecs.take(2)
        return targets
            .sortedWith(compareBy<CanonicalMuscle> { snapshot.load(it).heat }.thenBy { it.displayName })
            .take(1)
            .filter { snapshot.load(it).heat < 0.5 }
    }

    private fun focusTitle(kind: SessionFocusKind, emphasis: List<CanonicalMuscle>): String {
        val extra = emphasis.firstOrNull()?.displayName
        return when {
            kind == SessionFocusKind.RECOVERY -> "Full Body – Recovery lean"
            extra != null && kind != SessionFocusKind.FULL_BODY -> "${kind.label} – $extra emphasis"
            extra != null && kind == SessionFocusKind.FULL_BODY -> "Full Body – $extra emphasis"
            else -> kind.label
        }
    }

    private fun reasonFor(
        kind: SessionFocusKind,
        emphasis: List<CanonicalMuscle>,
        routine: Routine?,
        snapshot: BodyHeatSnapshot,
        thinHistory: Boolean,
        resolved: SplitStyle,
    ): String {
        if (thinHistory) {
            return if (routine != null) {
                "Starter ${resolved.displayName} week using ${routine.name}. Suggestions tighten after a few logged sessions."
            } else {
                "Starter ${resolved.displayName} week. Log working sets and this plan will follow your heat map."
            }
        }
        val muscle = emphasis.firstOrNull()
        val heatReason = if (muscle != null) {
            val load = snapshot.load(muscle)
            val days = load.daysSinceLastTrained
            when {
                days == null -> "${muscle.displayName} has no recent working sets."
                days >= 7 -> "${muscle.displayName} hasn’t been trained in $days days."
                load.heat <= 0.34 -> "${muscle.displayName} heat is low relative to your other work."
                else -> "${muscle.displayName} is the priority for this ${kind.label.lowercase()} day."
            }
        } else {
            "Keeps ${kind.label.lowercase()} work in the week without stacking the same stress every day."
        }
        return if (routine != null) {
            "$heatReason Using ${routine.name}."
        } else {
            "$heatReason No saved routine matched cleanly — start a free session with this focus."
        }
    }

    private fun pickRoutine(
        kind: SessionFocusKind,
        routines: List<Routine>,
        usedRoutineIds: Set<String>,
    ): Routine? {
        if (routines.isEmpty()) return null
        val ranked = routines
            .map { it to scoreRoutine(it, kind) }
            .filter { it.second > 0 }
            .sortedWith(
                compareByDescending<Pair<Routine, Int>> { it.first.id !in usedRoutineIds }
                    .thenByDescending { it.second }
                    .thenBy { it.first.name },
            )
        return ranked.firstOrNull()?.first
    }

    private fun customKinds(count: Int, routines: List<Routine>): List<SessionFocusKind> {
        if (routines.isEmpty()) return List(count) { SessionFocusKind.FULL_BODY }
        return List(count) { index -> classifyRoutine(routines[index % routines.size]) }
    }

    private fun upperLowerKinds(count: Int): List<SessionFocusKind> = List(count) { index ->
        if (index % 2 == 0) SessionFocusKind.UPPER else SessionFocusKind.LOWER
    }

    private fun pplKinds(count: Int): List<SessionFocusKind> {
        val cycle = listOf(SessionFocusKind.PUSH, SessionFocusKind.PULL, SessionFocusKind.LEGS)
        return List(count) { cycle[it % cycle.size] }
    }

    /**
     * Keeps the week's first session off the family you trained yesterday, without wrecking the
     * week to do it.
     *
     * The old version swapped the first slot with the first non-clashing one, and could create
     * the very adjacency it exists to prevent: `[UPPER, LOWER, UPPER]` after an upper session
     * became `[LOWER, UPPER, UPPER]` — two upper days back to back, produced by the rule meant
     * to avoid them. A single swap also cannot fix the case it most needs to: `[U, L, U, L]`
     * has no swap that both starts with LOWER and keeps alternating.
     *
     * Rotation can. Rotating the cycle preserves the split's shape — a Push/Pull/Legs week
     * stays Push/Pull/Legs, just entered at a different point — and a rotation is only accepted
     * when it starts clear of yesterday's family AND leaves the week no more crowded than it
     * already was. A week where no rotation qualifies (everything in one family, or the
     * three-day `[U, L, U]`) is left exactly as it is: churning it trades one adjacency for
     * another and calls it a fix.
     */
    internal fun arrangeKinds(
        kinds: List<SessionFocusKind>,
        lastFocus: SessionFocusKind?,
    ): List<SessionFocusKind> {
        if (kinds.size < 2) return kinds
        if (lastFocus == null || !sameStressFamily(kinds.first(), lastFocus)) return kinds
        val baseline = adjacentSameFamilyCount(kinds)
        return (1 until kinds.size)
            .asSequence()
            .map { offset -> kinds.drop(offset) + kinds.take(offset) }
            .firstOrNull { rotation ->
                !sameStressFamily(rotation.first(), lastFocus) &&
                    adjacentSameFamilyCount(rotation) <= baseline
            }
            ?: kinds
    }

    private fun adjacentSameFamilyCount(kinds: List<SessionFocusKind>): Int =
        (1 until kinds.size).count { sameStressFamily(kinds[it - 1], kinds[it]) }

    private fun recentFocus(
        sessions: List<WorkoutSession>,
        nowMs: Long,
    ): SessionFocusKind? {
        val last = sessions.maxByOrNull { it.finishedAt ?: it.date } ?: return null
        val at = last.finishedAt ?: last.date
        if (nowMs - at > RECENT_SESSION_HOURS * 60L * 60L * 1000L) return null
        return classifySession(last)
    }

    internal fun classifySession(session: WorkoutSession): SessionFocusKind {
        val name = session.routineName.orEmpty()
        if (name.isNotBlank() && name != "Free workout") {
            val fake = Routine(
                id = session.routineId ?: name,
                name = name,
                notes = "",
                createdAt = 0L,
                updatedAt = 0L,
                exercises = session.exercises.map {
                    RoutineExercise(
                        id = it.id,
                        routineId = session.routineId ?: "",
                        exercise = it.exercise,
                        sortOrder = it.sortOrder,
                        targetSets = it.targetSets,
                        targetReps = it.targetReps,
                        targetWeightKg = it.targetWeightKg,
                        restSeconds = it.restSeconds,
                    )
                },
            )
            return classifyRoutine(fake)
        }
        val muscles = session.exercises.map { MuscleNormalizer.primaryOf(it.exercise.muscleGroup) }
        val upper = muscles.count { it.region == MuscleRegion.UPPER }
        val lower = muscles.count { it.region == MuscleRegion.LOWER }
        return when {
            upper > 0 && lower > 0 -> SessionFocusKind.FULL_BODY
            lower > upper -> SessionFocusKind.LOWER
            else -> SessionFocusKind.UPPER
        }
    }

    private fun summary(
        thinHistory: Boolean,
        resolved: SplitStyle,
        prefs: SchedulePreferences,
        snapshot: BodyHeatSnapshot,
    ): String {
        val days = prefs.trainingDaysPerWeek
        return if (thinHistory) {
            "$days-day ${resolved.displayName} starter week. Log workouts and the next plan will follow neglected muscles and imbalances."
        } else {
            val window = snapshot.window.shortLabel.lowercase()
            "$days-day ${resolved.displayName} week from your last $window of working sets."
        }
    }

    internal fun compatible(classified: SessionFocusKind, needed: SessionFocusKind): Boolean {
        if (classified == needed) return true
        return when (needed) {
            SessionFocusKind.UPPER -> classified in setOf(SessionFocusKind.PUSH, SessionFocusKind.PULL)
            SessionFocusKind.LOWER, SessionFocusKind.LEGS ->
                classified in setOf(SessionFocusKind.LOWER, SessionFocusKind.LEGS)
            SessionFocusKind.FULL_BODY, SessionFocusKind.RECOVERY -> true
            SessionFocusKind.PUSH -> classified == SessionFocusKind.UPPER
            SessionFocusKind.PULL -> classified == SessionFocusKind.UPPER
        }
    }

    private fun sameStressFamily(a: SessionFocusKind, b: SessionFocusKind): Boolean {
        val upper = setOf(SessionFocusKind.UPPER, SessionFocusKind.PUSH, SessionFocusKind.PULL)
        val lower = setOf(SessionFocusKind.LOWER, SessionFocusKind.LEGS)
        return (a in upper && b in upper) || (a in lower && b in lower)
    }

    private fun musclesFor(kind: SessionFocusKind): Set<CanonicalMuscle> = when (kind) {
        SessionFocusKind.UPPER -> UPPER_MUSCLES
        SessionFocusKind.PUSH -> PUSH_MUSCLES
        SessionFocusKind.PULL -> PULL_MUSCLES
        SessionFocusKind.LOWER, SessionFocusKind.LEGS -> LEG_MUSCLES
        SessionFocusKind.FULL_BODY, SessionFocusKind.RECOVERY -> CanonicalMuscle.mapped.toSet()
    }

    private val PUSH_MUSCLES = setOf(CanonicalMuscle.CHEST, CanonicalMuscle.SHOULDERS, CanonicalMuscle.TRICEPS)
    private val PULL_MUSCLES = setOf(CanonicalMuscle.BACK, CanonicalMuscle.BICEPS)
    private val LEG_MUSCLES = setOf(
        CanonicalMuscle.QUADRICEPS,
        CanonicalMuscle.HAMSTRINGS,
        CanonicalMuscle.GLUTES,
        CanonicalMuscle.CALVES,
    )
    private val UPPER_MUSCLES = PUSH_MUSCLES + PULL_MUSCLES
}

