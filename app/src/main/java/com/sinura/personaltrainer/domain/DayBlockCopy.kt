package com.sinura.personaltrainer.domain

/**
 * The words on a day-board block.
 *
 * Home draws each of today's sessions as its own bordered block — the
 * control is the session (ADR-021 §1) — and the empty-agenda leftover card
 * draws its featured day with the same head. Both read these so a session
 * says the same thing on every Home surface, and so the lines can be
 * asserted without a composition.
 *
 * A block has a title, one still-and-name row per lift up to [ROW_LIMIT],
 * and a meta line. A session of seven (Upper A) names all seven. Past
 * eight, the rest is `+N` on its own last line. A block that is not
 * simply planned adds a status word at the foot, beside Start when it
 * can still be started.
 */
object DayBlockCopy {
    /**
     * How many lifts a block pictures beside their names. Typical sessions
     * fit; a longer one ends in "+N" so Home cannot become a novel.
     */
    const val ROW_LIMIT = 8

    data class Lines(
        /**
         * Numbered lifts in session order, one string per pictured lift
         * (`"1 Squat"`), then `"+2"` past [ROW_LIMIT]. Empty without lifts.
         */
        val names: List<String>,
        /**
         * "2 lifts · about 13 min". For an empty planned block, what it is
         * instead: "No lifts yet" for strength, "Ready" for cardio.
         */
        val meta: String?,
        /** Done / Skipped / Moved / Missed. Null while the block reads as planned. */
        val status: String?,
        /**
         * A block that cannot be started from where it sits goes quiet in
         * ink: done, moved, or skipped on an earlier day. The caller decides
         * with `DailyAgenda.canOpenStart`, so the ink and the missing Start
         * can never disagree.
         */
        val settled: Boolean = false,
    )

    /**
     * @param caption what an auxiliary pack says about itself. The start
     * confirm shows the caption in place of the count line, so the block
     * does too — a pack that says "About ten minutes" must not sit over a
     * second, different estimate.
     * @param startable `DailyAgenda.canOpenStart` for this block.
     */
    fun lines(
        names: List<String>,
        status: OccurrenceStatus,
        modality: ScheduleModality = ScheduleModality.STRENGTH,
        minutes: Int? = null,
        caption: String? = null,
        startable: Boolean = true,
    ): Lines {
        val settled = !startable
        if (names.isNotEmpty()) {
            return Lines(
                names = names(names),
                meta = caption?.takeIf { it.isNotBlank() } ?: meta(names.size, minutes),
                status = status(status),
                settled = settled,
            )
        }
        val empty = when {
            status != OccurrenceStatus.PLANNED -> null
            modality == ScheduleModality.STRENGTH -> SessionOrderCopy.EMPTY_PREVIEW
            else -> SessionOrderCopy.READY
        }
        return Lines(names = emptyList(), meta = empty, status = status(status), settled = settled)
    }

    /**
     * The leftover card's featured day. Never settled, and a proposed focus
     * with no routine behind it says nothing rather than "No lifts yet".
     */
    fun preview(names: List<String>, minutes: Int? = null): Lines = Lines(
        names = names(names),
        meta = if (names.isEmpty()) null else meta(names.size, minutes),
        status = null,
    )

    /**
     * The session order, numbered like the picker, one pictured lift per
     * entry. A middot sentence wrapped mid-name; a 4-up still strip
     * duplicated the names that sat under it. Rows carry the still, so a
     * typical session can name every lift. Past [ROW_LIMIT] the last entry
     * is "+N" rather than repeating the count the meta line already has.
     */
    fun names(names: List<String>, limit: Int = ROW_LIMIT): List<String> {
        if (names.isEmpty()) return emptyList()
        val cap = limit.coerceAtLeast(1)
        val shown = names.take(cap).mapIndexed { index, name -> "${index + 1} $name" }
        val rest = names.size - cap
        return if (rest > 0) shown + "+$rest" else shown
    }

    /**
     * The "+N" remainder, or null when every lift fits on a row. The card
     * draws this on its own line under the pictured rows.
     */
    fun extra(names: List<String>, limit: Int = ROW_LIMIT): String? =
        names(names, limit).lastOrNull()?.takeIf { EXTRA.matches(it) }

    /** True when [line] is the remainder (`"+3"`), not a numbered lift. */
    fun isExtra(line: String): Boolean = EXTRA.matches(line)

    /** "2 lifts · about 13 min". Also the last line of the start confirm. */
    fun meta(count: Int, minutes: Int?): String {
        val lifts = if (count == 1) "1 lift" else "$count lifts"
        return if (minutes != null) "$lifts · about $minutes min" else lifts
    }

    fun status(status: OccurrenceStatus): String? = when (status) {
        OccurrenceStatus.MISSED -> "Missed"
        else -> SessionOrderCopy.settledLabel(status)
    }

    private val EXTRA = Regex("""^\+\d+$""")
}
