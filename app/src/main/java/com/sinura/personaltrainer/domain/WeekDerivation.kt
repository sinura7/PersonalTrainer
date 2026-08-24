package com.sinura.personaltrainer.domain

import com.sinura.personaltrainer.util.JvmTime

/**
 * One position in the training cycle, as the app reasons about it.
 *
 * A slot is either a **routine slot** ([routineId] set) or a **focus-only slot** ([focusKind]
 * set) — never neither. A position with no slot is rest; rest is the absence of a slot, not an
 * empty one, which is why there is no "rest slot" here to construct.
 *
 * [anchorDay] is a *preference*, not a placement. Where a slot actually lands this week is
 * derived, and the derivation never writes back — so a week you miss reshapes what is shown
 * without quietly rewriting what you asked for.
 */
data class ScheduleSlot(
    val id: String,
    val position: Int,
    val routineId: String?,
    val focusKind: SessionFocusKind?,
    val anchorDay: Weekday?,
    val createdAt: Long,
    val updatedAt: Long,
)

data class DerivedDay(
    val epochDay: Long,
    val dayOfWeek: Weekday,
    /** Null means rest — no slot placed here. */
    val slot: ScheduleSlot?,
    val satisfiedBySessionId: String?,
) {
    val isRest: Boolean get() = slot == null
}

data class DerivedWeek(
    val weekStartEpochDay: Long,
    /** Always seven, in week-start order. */
    val days: List<DerivedDay>,
    /** The day holding the lowest-position slot still unsatisfied, or null when none is left. */
    val nextUp: DerivedDay?,
)

/**
 * Turns stored slots plus what actually happened into the week the app shows.
 *
 * Before this, the week was a pure function of *history* — recomputed from scratch on every
 * flow emission, from a fresh clock — which meant it reshuffled whenever anything was logged
 * and nothing the user decided about their own week survived a recomposition. What was on
 * screen looked like a plan and behaved like a suggestion, and there was no way to tell which
 * parts of it you had chosen.
 *
 * Now the plan is stored and this is the read model over it. Everything here is a pure
 * function of (slots, history, preferences, now) and nothing here writes: the same inputs
 * always give the same week, and a week that has drifted is drift you can point at.
 *
 * The rules below are the ones signed in `docs/SCHEDULE_SEMANTICS.md`. Where the
 * Phase-4 packet's restatement disagreed with the signed document, the signed document wins —
 * see [placeUnsatisfied] for the one place that matters.
 */
object WeekDerivation {

    fun derive(
        slots: List<ScheduleSlot>,
        history: List<WorkoutSession>,
        preferences: SchedulePreferences,
        nowMs: Long,
        time: TimePort = JvmTime,
        zoneId: String = time.defaultZoneId(),
    ): DerivedWeek {
        val prefs = preferences.sanitized()
        val today = time.civilDate(nowMs, zoneId)
        val weekStart = today.previousOrSame(prefs.weekStart)
        val dates = (0L..6L).map { weekStart.plusDays(it) }
        val ordered = slots.sortedBy { it.position }

        val satisfaction = satisfy(ordered, history, dates, time, zoneId)
        val placement = place(ordered, satisfaction, dates, today)

        val days = dates.map { date ->
            val epochDay = date.epochDay
            val slot = placement[epochDay]
            DerivedDay(
                epochDay = epochDay,
                dayOfWeek = date.dayOfWeek,
                slot = slot,
                satisfiedBySessionId = slot?.let { satisfaction[it.id]?.sessionId },
            )
        }
        return DerivedWeek(
            weekStartEpochDay = weekStart.epochDay,
            days = days,
            // The earliest day holding an undone slot. That IS the lowest-position unsatisfied
            // slot, because unsatisfied slots are placed with a cursor that never moves back.
            nextUp = days.firstOrNull { it.slot != null && it.satisfiedBySessionId == null },
        )
    }

    /**
     * Which slots this week's sessions have already covered.
     *
     * Matching, not an origin column — the schema is frozen and a "which slot started this"
     * column would be a v3. It costs a little precision (two identical routine slots in one
     * week are satisfied in position order regardless of which one you meant) and buys the
     * ability to credit a session you started any other way, which is how people actually
     * train: the plan says Push today, you open Push from the library, and the week should
     * still say you did it.
     *
     * Each session satisfies at most one slot, and each slot at most once.
     */
    private fun satisfy(
        slots: List<ScheduleSlot>,
        history: List<WorkoutSession>,
        dates: List<CivilDate>,
        time: TimePort,
        zoneId: String,
    ): Map<String, Satisfaction> {
        val first = dates.first().epochDay
        val last = dates.last().epochDay
        val thisWeek = history
            .filter { it.isFinished }
            .map { it to epochDayOf(it.date, time, zoneId) }
            .filter { (_, day) -> day in first..last }
            .sortedBy { (session, _) -> session.finishedAt ?: session.date }

        val result = LinkedHashMap<String, Satisfaction>()
        thisWeek.forEach { (session, day) ->
            val slot = slots.firstOrNull { candidate ->
                candidate.id !in result && matches(candidate, session)
            } ?: return@forEach
            result[slot.id] = Satisfaction(sessionId = session.id, epochDay = day)
        }
        return result
    }

    private fun matches(slot: ScheduleSlot, session: WorkoutSession): Boolean = when {
        slot.routineId != null -> session.routineId == slot.routineId
        slot.focusKind != null ->
            WeeklySchedulePlanner.compatible(
                WeeklySchedulePlanner.classifySession(session),
                slot.focusKind,
            )
        else -> false
    }

    private fun place(
        slots: List<ScheduleSlot>,
        satisfaction: Map<String, Satisfaction>,
        dates: List<CivilDate>,
        today: CivilDate,
    ): Map<Long, ScheduleSlot> {
        val placed = LinkedHashMap<Long, ScheduleSlot>()

        // Rule 1: a satisfied slot shows on the day its session finished. If two land on one
        // day the second is still satisfied — it just has nowhere to be drawn.
        slots.forEach { slot ->
            val hit = satisfaction[slot.id] ?: return@forEach
            if (!placed.containsKey(hit.epochDay)) placed[hit.epochDay] = slot
        }

        val unsatisfied = slots.filter { it.id !in satisfaction }
        placeUnsatisfied(unsatisfied, placed, dates, today)
        return placed
    }

    /**
     * Where the slots you have not done yet go.
     *
     * Two rules, applied in cycle order, with a cursor that never moves backwards:
     *
     * - An anchored slot takes its anchor day when that day is still ahead of the cursor and
     *   still open. "Ahead of the cursor" is what makes **cycle order beat anchors**: if an
     *   earlier slot has already claimed Friday, Friday's anchored slot shifts forward rather
     *   than jumping the queue.
     * - Anything else takes the earliest open day at or after the cursor. A missed day shifts
     *   forward; it is never skipped, and it is never placed in the past.
     *
     * Slots that run out of days are simply not shown this week — the cycle does not restart
     * mid-week, and an unfinished slot is not carried into next week as debt (signed rule 6).
     *
     * **Deviation, recorded.** The Phase-4 packet restated this rule as an even spread of
     * unanchored slots across the remaining open days, which would put a missed Wednesday
     * session on Friday rather than Thursday. The signed semantics document says "the earliest
     * open day ≥ today that preserves cycle order", and its worked examples only work under
     * that reading — so that is what is implemented, per the packet's own instruction that the
     * signed document wins on conflict.
     */
    private fun placeUnsatisfied(
        unsatisfied: List<ScheduleSlot>,
        placed: MutableMap<Long, ScheduleSlot>,
        dates: List<CivilDate>,
        today: CivilDate,
    ) {
        val todayEpoch = today.epochDay
        var cursor = maxOf(dates.first().epochDay, todayEpoch)
        unsatisfied.forEach { slot ->
            val anchorEpoch = slot.anchorDay?.let { anchor ->
                dates.firstOrNull { it.dayOfWeek == anchor }?.epochDay
            }
            val target = if (anchorEpoch != null && anchorEpoch >= cursor && !placed.containsKey(anchorEpoch)) {
                anchorEpoch
            } else {
                dates.map { it.epochDay }
                    .firstOrNull { it >= cursor && !placed.containsKey(it) }
            } ?: return@forEach
            placed[target] = slot
            cursor = target + 1
        }
    }

    /**
     * The derived week in the shape the rest of the app already reads.
     *
     * [WeeklySchedulePlan] was built by the planner and consumed by Home, the week card and the
     * old Schedule screen. Keeping the type means none of those had to be rewritten to stop
     * believing in an invented week — what changed is where the days come from, not what they
     * are. Days here carry a `slotId`; planner proposals do not, which is how a surface tells
     * a pinned day from a suggestion without asking anyone.
     */
    fun toWeeklySchedulePlan(
        week: DerivedWeek,
        routines: List<Routine>,
        preferences: SchedulePreferences,
        nowMs: Long,
    ): WeeklySchedulePlan {
        val prefs = preferences.sanitized()
        val byId = routines.associateBy { it.id }
        val days = week.days.map { day ->
            val slot = day.slot
            if (slot == null) {
                restDay(day)
            } else {
                val routine = slot.routineId?.let(byId::get)
                val kind = when {
                    routine != null -> WeeklySchedulePlanner.classifyRoutine(routine)
                    else -> slot.focusKind ?: SessionFocusKind.FULL_BODY
                }
                SuggestedTrainingDay(
                    epochDay = day.epochDay,
                    dayOfWeek = day.dayOfWeek,
                    isRest = false,
                    focusKind = kind,
                    focusTitle = routine?.name ?: kind.label,
                    routineId = routine?.id,
                    routineName = routine?.name,
                    reason = if (day.satisfiedBySessionId != null) "Logged." else "Pinned to your week.",
                    emphasisMuscles = emptyList(),
                    confidence = ScheduleConfidence.HIGH,
                    slotId = slot.id,
                )
            }
        }
        val pinned = days.count { !it.isRest }
        val logged = week.days.count { it.satisfiedBySessionId != null }
        return WeeklySchedulePlan(
            weekStartEpochDay = week.weekStartEpochDay,
            generatedAtMs = nowMs,
            preferences = prefs,
            resolvedSplit = prefs.splitStyle,
            days = days,
            // Nothing here is inferred from history, so there is no thin-history caveat to make.
            thinHistory = false,
            summary = if (pinned == 0) {
                "No sessions pinned yet."
            } else {
                "$pinned pinned · $logged logged this week"
            },
        )
    }

    private fun restDay(day: DerivedDay): SuggestedTrainingDay = SuggestedTrainingDay(
        epochDay = day.epochDay,
        dayOfWeek = day.dayOfWeek,
        isRest = true,
        focusKind = SessionFocusKind.RECOVERY,
        focusTitle = "Rest",
        routineId = null,
        routineName = null,
        reason = "Rest day.",
        emphasisMuscles = emptyList(),
        confidence = ScheduleConfidence.HIGH,
        slotId = null,
    )

    private fun epochDayOf(atMs: Long, time: TimePort, zoneId: String): Long =
        time.civilDate(atMs, zoneId).epochDay

    private data class Satisfaction(val sessionId: String, val epochDay: Long)
}
