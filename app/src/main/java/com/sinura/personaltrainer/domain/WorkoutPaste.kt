package com.sinura.personaltrainer.domain

/**
 * A written workout, read into routines.
 *
 * The user is the author. This parser does not invent lifts, rewrite the
 * program, or call a model. It splits headings, schemes, and "or" lines,
 * then matches names against the library that is already on the phone.
 */
enum class PastedSessionKind {
    STRENGTH,
    CARDIO,
    FLEXIBILITY,
}

data class WorkScheme(
    val setsMin: Int,
    val setsMax: Int,
    val repsMin: Int? = null,
    val repsMax: Int? = null,
    val secondsMin: Int? = null,
    val secondsMax: Int? = null,
    val perSide: Boolean = false,
    val each: Boolean = false,
) {
    val isTimed: Boolean get() = secondsMin != null
    val storedSets: Int get() = setsMin.coerceAtLeast(1)
    /**
     * Reps column for Room. Holds keep a 1 placeholder so the NOT NULL
     * column stays valid; the prescription lives on [storedSeconds].
     */
    val storedReps: Int get() = if (isTimed) HoldWork.HOLD_REPS_PLACEHOLDER else (repsMin ?: 1).coerceAtLeast(1)
    val storedSeconds: Int? get() = secondsMin
    val storedSecondsMax: Int? get() = secondsMax?.takeIf { secondsMin != null && it != secondsMin }

    fun prescription(): String {
        val sets = if (setsMin == setsMax) "$setsMin" else "$setsMin–$setsMax"
        val work = when {
            secondsMin != null -> {
                val end = secondsMax?.takeIf { it != secondsMin }?.let { "–$it" } ?: ""
                "${secondsMin}${end}s"
            }
            repsMin != null -> {
                val end = repsMax?.takeIf { it != repsMin }?.let { "–$it" } ?: ""
                "$repsMin$end"
            }
            else -> ""
        }
        val side = if (perSide) "/side" else ""
        val eachMark = if (each) " each" else ""
        return "${sets}×$work$side$eachMark"
    }
}

data class PastedLift(
    val exercise: Exercise,
    val scheme: WorkScheme,
    val restSeconds: Int,
    val alternative: Exercise? = null,
    val alternativeNote: String? = null,
    val raw: String,
) {
    val targetSets: Int get() = scheme.storedSets
    val targetReps: Int get() = scheme.storedReps
    val targetSeconds: Int? get() = scheme.storedSeconds
    val targetSecondsMax: Int? get() = scheme.storedSecondsMax
}

enum class PasteIssueKind {
    UNKNOWN_EXERCISE,
    BAD_DOSE,
    TWO_LIFTS_NEEDED,
    HOLD_VS_REPS,
}

data class PastedUnmatched(
    val raw: String,
    val names: List<String>,
    val scheme: WorkScheme?,
    val kind: PasteIssueKind = PasteIssueKind.UNKNOWN_EXERCISE,
    val reason: String = "",
    val sessionName: String? = null,
) {
    fun displayLine(): String =
        if (sessionName.isNullOrBlank()) raw else "$sessionName: $raw"

    val canPick: Boolean
        get() = kind == PasteIssueKind.UNKNOWN_EXERCISE ||
            kind == PasteIssueKind.TWO_LIFTS_NEEDED
}

data class PastedSession(
    val name: String,
    val emphasis: String?,
    val kind: PastedSessionKind,
    val lifts: List<PastedLift>,
    val unmatched: List<PastedUnmatched>,
    val originalLines: List<String>,
) {
    fun notes(): String {
        val lines = mutableListOf<String>()
        emphasis?.let { lines += it }
        if (originalLines.isNotEmpty()) {
            lines += WorkoutPasteCopy.PASTED_HEADING
            originalLines.forEach { lines += it }
        }
        val altNotes = lifts.mapNotNull { lift ->
            lift.alternativeNote?.let { "${lift.exercise.name}: or $it" }
        }
        if (altNotes.isNotEmpty()) {
            lines += "Alternatives"
            lines += altNotes
        }
        val holds = lifts.filter { it.scheme.isTimed }
        if (holds.isNotEmpty()) {
            lines += WorkoutPasteCopy.HOLDS_HEADING
            holds.forEach { lift ->
                lines += "• ${lift.exercise.name}: ${lift.scheme.prescription()} — hold, not reps"
            }
        }
        if (unmatched.isNotEmpty()) {
            lines += WorkoutPasteCopy.UNMATCHED_NOTES
            unmatched.forEach { lines += "• ${WorkoutPasteCopy.issue(it)}" }
        }
        return lines.joinToString("\n")
    }
}

data class PastedWeekPin(
    val weekday: Weekday,
    val sessionNames: List<String>,
    val raw: String,
)

data class WorkoutPastePlan(
    val sessions: List<PastedSession>,
    val weekPins: List<PastedWeekPin> = emptyList(),
    val warmupNote: String? = null,
    val progressionNote: String? = null,
) {
    fun strengthSessions(): List<PastedSession> =
        sessions.filter { it.kind == PastedSessionKind.STRENGTH }

    fun sessionNamed(name: String): PastedSession? =
        sessions.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun programNotesFor(session: PastedSession): String {
        val lines = mutableListOf<String>()
        if (session.kind == PastedSessionKind.STRENGTH && !warmupNote.isNullOrBlank()) {
            lines += WorkoutPasteCopy.WARMUP_HEADING
            lines += warmupNote
        }
        if (weekPins.isNotEmpty()) {
            lines += WorkoutPasteCopy.WEEKLY_HEADING
            weekPins.forEach { lines += it.raw }
        }
        if (!progressionNote.isNullOrBlank()) {
            lines += WorkoutPasteCopy.PROGRESSION_HEADING
            lines += progressionNote
        }
        return lines.joinToString("\n")
    }

    fun combinedNotes(session: PastedSession): String =
        listOf(session.notes(), programNotesFor(session))
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
}

/**
 * Rest from the written program, not from add-to-routine defaults.
 *
 * Heavy compounds: 2–3 min. Accessories (and every timed hold): 60–90s.
 * 150s and 75s sit inside those bands.
 */
internal object WorkoutPasteRest {
    const val COMPOUND_SECONDS = 150
    const val ACCESSORY_SECONDS = 75

    fun forLift(exercise: Exercise, scheme: WorkScheme): Int {
        if (scheme.isTimed) return ACCESSORY_SECONDS
        return if (AddDefaults.isCompound(exercise)) COMPOUND_SECONDS else ACCESSORY_SECONDS
    }
}

/**
 * Holds are seconds. A paired line like "3×10–15 / 3×30–45s" must not
 * put the plank on the rep scheme.
 */
internal object WorkoutPasteHolds {
    fun isHold(exercise: Exercise): Boolean = HoldWork.isHold(exercise)

    fun schemeFor(
        exercise: Exercise,
        schemes: List<WorkScheme>,
        index: Int,
        fallback: WorkScheme,
        role: String? = null,
    ): WorkScheme {
        if (schemes.isEmpty()) return fallback
        val picked = if (schemes.size == 1) {
            schemes.single()
        } else {
            val timed = schemes.firstOrNull { it.isTimed }
            val reps = schemes.firstOrNull { !it.isTimed }
            if (isHold(exercise)) {
                timed ?: schemes.getOrElse(index) { schemes.first() }
            } else {
                reps ?: schemes.getOrElse(index) { schemes.first() }
            }
        }
        return coerceHold(exercise, picked, role)
    }

    /**
     * A hold written without an `s` still has to land as seconds.
     * `Static: dead hang — 2×20–40` is 20–40 seconds, not 20–40 reps.
     */
    fun coerceHold(exercise: Exercise, scheme: WorkScheme, role: String? = null): WorkScheme {
        if (!isHold(exercise) || scheme.isTimed) return scheme
        val lo = scheme.repsMin ?: return scheme
        val hi = scheme.repsMax ?: lo
        val staticRole = role.equals("static", ignoreCase = true)
        if (!staticRole && lo < HoldWork.MIN_SECONDS) return scheme
        return scheme.copy(
            repsMin = null,
            repsMax = null,
            secondsMin = lo,
            secondsMax = hi,
        )
    }
}

object WorkoutPaste {
    fun catalogExercises(seeds: List<SeedExercise> = DefaultExercises.catalog()): List<Exercise> =
        seeds.map { seed ->
            Exercise(
                id = seed.id,
                name = seed.name,
                muscleGroup = seed.muscleGroup,
                notes = "",
                isCustom = false,
                equipment = seed.equipment,
                loadType = seed.loadType,
                movementKey = seed.movementKey,
                imageKey = seed.imageKey,
                muscles = seed.credits,
            )
        }

    fun parseAndMatch(
        text: String,
        catalog: List<Exercise> = catalogExercises(),
    ): WorkoutPastePlan {
        val parsed = WorkoutPasteParser.parseDocument(text)
        val sessions = parsed.sessions.map { matchSession(it, catalog) }
        val names = sessions.map { it.name }
        return WorkoutPastePlan(
            sessions = sessions,
            weekPins = parsed.weekLines.mapNotNull { WorkoutPasteParser.weekPin(it, names) },
            warmupNote = parsed.warmupLine,
            progressionNote = parsed.progressionLines
                .takeIf { it.isNotEmpty() }
                ?.joinToString("\n"),
        )
    }

    /**
     * Why this paste is not a workout, in everyday words. Null when at
     * least one lift matched or a line is waiting to be picked.
     *
     * Per-line quoting never runs when the parser never opened a session.
     */
    fun unreadableReason(text: String, plan: WorkoutPastePlan): String? {
        if (plan.sessions.any { it.lifts.isNotEmpty() || it.unmatched.isNotEmpty() }) {
            return null
        }
        return WorkoutPasteParser.explainEmpty(text)
    }

    private fun matchSession(session: ParsedSession, catalog: List<Exercise>): PastedSession {
        val lifts = mutableListOf<PastedLift>()
        val unmatched = mutableListOf<PastedUnmatched>()
        session.lines.forEach { line ->
            val schemes = line.schemes.ifEmpty {
                listOfNotNull(session.defaultScheme)
            }
            val used = mutableSetOf<String>()
            val matchedHere = mutableListOf<PastedLift>()
            line.groups.forEachIndexed { index, choice ->
                val hit = choice.firstNotNullOfOrNull { option ->
                    WorkoutCatalogMatch.match(option, catalog, used)
                }
                val schemeForMiss = schemes.getOrNull(index) ?: schemes.firstOrNull()
                if (hit == null) {
                    val kind = when {
                        line.groups.size > 1 -> PasteIssueKind.TWO_LIFTS_NEEDED
                        line.claimedDose && schemes.isEmpty() -> PasteIssueKind.BAD_DOSE
                        else -> PasteIssueKind.UNKNOWN_EXERCISE
                    }
                    unmatched += unmatchedLine(line, choice, schemeForMiss, kind)
                    return@forEachIndexed
                }
                used += hit.id
                val defaults = AddDefaults.forExercise(hit)
                val fallback = holdAwareFallback(defaults)
                var scheme = WorkoutPasteHolds.schemeFor(
                    exercise = hit,
                    schemes = schemes,
                    index = index,
                    fallback = fallback,
                    role = line.role,
                )
                if (WorkoutPasteHolds.isHold(hit) && !scheme.isTimed) {
                    unmatched += unmatchedLine(
                        line = line,
                        names = listOf(hit.name),
                        scheme = scheme,
                        kind = PasteIssueKind.HOLD_VS_REPS,
                    )
                    scheme = fallback.copy(setsMin = scheme.setsMin, setsMax = scheme.setsMax)
                } else if (
                    !WorkoutPasteHolds.isHold(hit) &&
                    scheme.isTimed &&
                    line.lineSchemes.any { it.isTimed }
                ) {
                    unmatched += unmatchedLine(
                        line = line,
                        names = listOf(hit.name),
                        scheme = scheme,
                        kind = PasteIssueKind.HOLD_VS_REPS,
                    )
                } else if (line.claimedDose && line.schemes.isEmpty() && session.defaultScheme == null) {
                    unmatched += unmatchedLine(
                        line = line,
                        names = listOf(hit.name),
                        scheme = scheme,
                        kind = PasteIssueKind.BAD_DOSE,
                    )
                }
                val rest = WorkoutPasteRest.forLift(hit, scheme)
                val remaining = choice.filterNot {
                    WorkoutCatalogMatch.normalize(it) == WorkoutCatalogMatch.normalize(hit.name) ||
                        WorkoutCatalogMatch.match(it, catalog) == hit
                }
                val altName = remaining.firstOrNull()
                val alt = remaining.firstNotNullOfOrNull { WorkoutCatalogMatch.match(it, catalog, used) }
                val sibling = alt != null && LibraryGrouping.siblings(
                    exercise = hit,
                    catalog = catalog,
                    exclude = emptySet(),
                ).any { it.id == alt.id }
                matchedHere += PastedLift(
                    exercise = hit,
                    scheme = scheme,
                    restSeconds = rest,
                    alternative = alt,
                    alternativeNote = if (sibling) null else altName?.let { alt?.name ?: it },
                    raw = line.raw,
                )
            }
            if (matchedHere.isNotEmpty()) {
                lifts += matchedHere
            } else if (line.groups.isEmpty()) {
                val kind = if (line.claimedDose) PasteIssueKind.BAD_DOSE else PasteIssueKind.UNKNOWN_EXERCISE
                unmatched += unmatchedLine(line, line.names, schemes.firstOrNull(), kind)
            }
        }
        return PastedSession(
            name = session.name,
            emphasis = session.emphasis,
            kind = session.kind,
            lifts = lifts,
            unmatched = unmatched,
            originalLines = session.lines.map { it.raw },
        )
    }

    private fun holdAwareFallback(defaults: TargetDefaults): WorkScheme =
        if (defaults.seconds != null) {
            WorkScheme(
                setsMin = defaults.sets,
                setsMax = defaults.sets,
                secondsMin = defaults.seconds,
                secondsMax = defaults.secondsMax,
            )
        } else {
            WorkScheme(
                setsMin = defaults.sets,
                setsMax = defaults.sets,
                repsMin = defaults.reps,
                repsMax = defaults.reps,
            )
        }

    private fun unmatchedLine(
        line: ParsedWorkLine,
        names: List<String>,
        scheme: WorkScheme?,
        kind: PasteIssueKind,
    ): PastedUnmatched = PastedUnmatched(
        raw = line.raw,
        names = names,
        scheme = scheme,
        kind = kind,
        reason = WorkoutPasteCopy.reason(kind, names),
    )
}

internal data class ParsedSession(
    val name: String,
    val emphasis: String?,
    val kind: PastedSessionKind,
    val lines: List<ParsedWorkLine>,
    val defaultScheme: WorkScheme? = null,
)

internal data class ParsedWorkLine(
    val raw: String,
    /** Each group is one lift. Options inside a group are "or" alternatives. */
    val groups: List<List<String>>,
    val schemes: List<WorkScheme>,
    val each: Boolean,
    val role: String? = null,
    val claimedDose: Boolean = false,
    /** Schemes written on this line, not a session default. */
    val lineSchemes: List<WorkScheme> = emptyList(),
) {
    val names: List<String> get() = groups.flatten()
}

internal data class ParsedPaste(
    val sessions: List<ParsedSession>,
    val weekLines: List<String>,
    val progressionLines: List<String>,
    val warmupLine: String?,
)

private enum class PasteSection {
    SESSION,
    WEEKLY,
    PROGRESSION,
}

internal object WorkoutPasteParser {
    private val STRENGTH_HEADER = Regex("""^(.+?)\s+\((strength|muscle)\)\s*$""", RegexOption.IGNORE_CASE)
    private val BLOCK_HEADER = Regex(
        """^(upper|lower)\s+([ab])(?:\s+\((strength|muscle)\))?\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val CARDIO_HEADER = Regex("""^cardio\b""", RegexOption.IGNORE_CASE)
    private val FLEX_HEADER = Regex("""^flexibility\b""", RegexOption.IGNORE_CASE)
    private val WEEKLY_HEADER = Regex("""^(weekly layout|week)$""", RegexOption.IGNORE_CASE)
    private val PROGRESSION_HEADER = Regex("""^progression$""", RegexOption.IGNORE_CASE)
    private val WARMUP_LINE = Regex("""^warm\s*up\b""", RegexOption.IGNORE_CASE)
    private val WEEKDAY_HEAD = Regex(
        """^(mon(?:day)?|tue(?:s(?:day)?)?|wed(?:nesday)?|thu(?:r(?:s(?:day)?)?)?|fri(?:day)?|sat(?:urday)?|sun(?:day)?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val HOLD_DEFAULT = Regex("""(\d+)\s*[–-]\s*(\d+)\s*s\b""", RegexOption.IGNORE_CASE)
    private val ROLE = Regex("""^(abs|static|arms|finish with)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val LIST_PREFIX = Regex("""^(\d+[.)]\s+|[-*•]\s+)""")
    private val SCHEME = Regex(
        """(\d+)(?:\s*[–-]\s*(\d+))?\s*[×xX]\s*(\d+)(?:\s*[–-]\s*(\d+))?(s|sec|secs|seconds)?(?:\s*/\s*(leg|side))?(\s*each)?""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): List<ParsedSession> = parseDocument(text).sessions

    fun parseDocument(text: String): ParsedPaste {
        val sessions = mutableListOf<ParsedSessionBuilder>()
        val weekLines = mutableListOf<String>()
        val progressionLines = mutableListOf<String>()
        var warmupLine: String? = null
        var current: ParsedSessionBuilder? = null
        var section = PasteSection.SESSION
        text.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.startsWith(">")) return@forEach
            val content = stripPasteDecor(line)
            if (content.isEmpty()) return@forEach
            header(content)?.let { next ->
                current?.let { sessions += it }
                current = next
                section = PasteSection.SESSION
                return@forEach
            }
            when {
                WEEKLY_HEADER.matches(content) -> {
                    current?.let { sessions += it }
                    current = null
                    section = PasteSection.WEEKLY
                    return@forEach
                }
                PROGRESSION_HEADER.matches(content) -> {
                    current?.let { sessions += it }
                    current = null
                    section = PasteSection.PROGRESSION
                    return@forEach
                }
                WARMUP_LINE.containsMatchIn(content) -> {
                    warmupLine = content
                    return@forEach
                }
            }
            when (section) {
                PasteSection.WEEKLY -> weekLines += content
                PasteSection.PROGRESSION -> progressionLines += content
                PasteSection.SESSION -> {
                    val session = current ?: return@forEach
                    if (looksLikeProse(content)) return@forEach
                    session.lines += workLine(content, session.defaultScheme).copy(raw = line)
                }
            }
        }
        current?.let { sessions += it }
        return ParsedPaste(
            sessions = sessions.map { it.build() },
            weekLines = weekLines,
            progressionLines = progressionLines,
            warmupLine = warmupLine,
        )
    }

    /**
     * ChatGPT leftover on a title: `Lower A (strength)**` or wrapping
     * `**Lower A (strength)**`. `##` headings are already a hash prefix.
     */
    internal fun stripPasteDecor(line: String): String {
        var cleaned = line.trim().trimStart('\uFEFF')
        cleaned = cleaned.trimStart('#').trim()
        cleaned = cleaned.replace(Regex("""^[\*_]+"""), "").replace(Regex("""[\*_]+$"""), "").trim()
        return cleaned
    }

    /**
     * Everyday words for a blob that never became a session with work.
     * Numbered lifts with no title used to vanish into the generic empty
     * parse; quote that, including leftover `**` on the first line.
     */
    fun explainEmpty(text: String): String {
        var leftoverStars = false
        var workLines = 0
        var sessionHeaders = 0
        text.lineSequence().forEach { raw ->
            val line = raw.trim().trimStart('\uFEFF')
            if (line.isEmpty() || line.startsWith(">")) return@forEach
            val content = stripPasteDecor(line)
            if (content.isEmpty()) return@forEach
            if (line.contains("**") || line.contains("__")) leftoverStars = true
            if (header(content) != null) sessionHeaders++
            if (looksLikeWork(content)) workLines++
        }
        return WorkoutPasteCopy.wholeFailure(
            noSessionName = sessionHeaders == 0,
            noNumberedLifts = workLines == 0,
            leftoverStars = leftoverStars,
        )
    }

    fun weekPin(line: String, sessionNames: List<String>): PastedWeekPin? {
        val match = WEEKDAY_HEAD.find(line) ?: return null
        val weekday = weekdayOf(match.groupValues[1]) ?: return null
        val named = sessionNames.filter { name ->
            Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(line)
        }
        return PastedWeekPin(weekday = weekday, sessionNames = named, raw = line)
    }

    private fun weekdayOf(token: String): Weekday? = when (token.take(3).lowercase()) {
        "mon" -> Weekday.MONDAY
        "tue" -> Weekday.TUESDAY
        "wed" -> Weekday.WEDNESDAY
        "thu" -> Weekday.THURSDAY
        "fri" -> Weekday.FRIDAY
        "sat" -> Weekday.SATURDAY
        "sun" -> Weekday.SUNDAY
        else -> null
    }

    private fun header(line: String): ParsedSessionBuilder? {
        STRENGTH_HEADER.matchEntire(line)?.let { match ->
            return ParsedSessionBuilder(
                name = match.groupValues[1].trim(),
                emphasis = match.groupValues[2].lowercase(),
                kind = PastedSessionKind.STRENGTH,
            )
        }
        BLOCK_HEADER.matchEntire(line)?.let { match ->
            val side = match.groupValues[1].lowercase().replaceFirstChar { it.uppercase() }
            val letter = match.groupValues[2].uppercase()
            val emphasis = match.groupValues[3].lowercase().ifBlank { null }
            return ParsedSessionBuilder(
                name = "$side $letter",
                emphasis = emphasis,
                kind = PastedSessionKind.STRENGTH,
            )
        }
        if (CARDIO_HEADER.containsMatchIn(line)) {
            return ParsedSessionBuilder(
                name = "Cardio",
                emphasis = null,
                kind = PastedSessionKind.CARDIO,
            )
        }
        if (FLEX_HEADER.containsMatchIn(line)) {
            val hold = HOLD_DEFAULT.find(line)
            val defaultScheme = hold?.let {
                WorkScheme(
                    setsMin = 1,
                    setsMax = 1,
                    secondsMin = it.groupValues[1].toInt(),
                    secondsMax = it.groupValues[2].toInt(),
                )
            }
            return ParsedSessionBuilder(
                name = "Flexibility",
                emphasis = null,
                kind = PastedSessionKind.FLEXIBILITY,
                defaultScheme = defaultScheme,
            )
        }
        return null
    }

    private fun looksLikeWork(line: String): Boolean {
        val unmarked = LIST_PREFIX.replaceFirst(line.trim(), "")
        return ROLE.containsMatchIn(unmarked) ||
            SCHEME.containsMatchIn(unmarked) ||
            LIST_PREFIX.containsMatchIn(line)
    }

    private fun looksLikeProse(line: String): Boolean {
        val unmarked = LIST_PREFIX.replaceFirst(line.trim(), "")
        if (ROLE.containsMatchIn(unmarked) || SCHEME.containsMatchIn(unmarked)) return false
        val words = unmarked.split(Regex("""\s+"""))
        return words.size > 14 && !unmarked.contains("—") && !unmarked.contains("–")
    }

    private fun workLine(raw: String, inherited: WorkScheme?): ParsedWorkLine {
        val unmarked = LIST_PREFIX.replaceFirst(raw.trim(), "")
        val role = ROLE.find(unmarked)?.groupValues?.get(1)?.lowercase()
        val withoutRole = ROLE.replace(unmarked, "")
        val (body, schemesFromLine) = splitSchemes(withoutRole)
        val schemes = schemesFromLine.ifEmpty { listOfNotNull(inherited) }
        val each = schemes.any { it.each } || withoutRole.contains(" each", ignoreCase = true)
        val groups = splitNameGroups(body)
        val looksLikeDose = withoutRole.contains('—') ||
            withoutRole.contains('–') ||
            SCHEME.containsMatchIn(withoutRole)
        val claimedDose = looksLikeDose && schemesFromLine.isEmpty()
        return ParsedWorkLine(
            raw = raw,
            groups = groups,
            schemes = schemes,
            each = each,
            role = role,
            claimedDose = claimedDose,
            lineSchemes = schemesFromLine,
        )
    }

    private fun splitSchemes(line: String): Pair<String, List<WorkScheme>> {
        val dash = Regex("""\s+[—–-]\s+""").find(line)
        if (dash != null) {
            val left = line.substring(0, dash.range.first).trim()
            val right = line.substring(dash.range.last + 1).trim()
            val schemes = parseSchemeList(right)
            if (schemes.isNotEmpty()) return left to schemes
            if (left.isNotEmpty() && right.isNotEmpty()) return left to emptyList()
        }
        val embedded = SCHEME.findAll(line).toList()
        val hasTimes = line.contains('×') || line.contains('x', ignoreCase = true)
        if (embedded.isNotEmpty() && hasTimes) {
            val first = embedded.first()
            if (first.range.first > 0) {
                val names = line.substring(0, first.range.first).trim().trimEnd('—', '–', '-', ' ')
                return names to embedded.map { parseSchemeMatch(it) }
            }
        }
        return line to emptyList()
    }

    private fun parseSchemeList(text: String): List<WorkScheme> {
        val cleaned = text.replace(Regex("""\([^)]*\)"""), " ").trim()
        val parts = cleaned.split(Regex("""\s*/\s*""")).map { it.trim() }.filter { it.isNotEmpty() }
        val schemes = parts.mapNotNull { part -> SCHEME.find(part)?.let { parseSchemeMatch(it) } }
        return if (schemes.size == parts.size && schemes.isNotEmpty()) schemes else SCHEME.findAll(cleaned).map { parseSchemeMatch(it) }.toList()
    }

    private fun parseSchemeMatch(match: MatchResult): WorkScheme {
        val setsMin = match.groupValues[1].toInt()
        val setsMax = match.groupValues[2].toIntOrNull() ?: setsMin
        val workMin = match.groupValues[3].toInt()
        val workMax = match.groupValues[4].toIntOrNull() ?: workMin
        val timed = match.groupValues[5].isNotEmpty()
        val perSide = match.groupValues[6].isNotEmpty()
        val each = match.groupValues[7].isNotEmpty()
        return if (timed) {
            WorkScheme(
                setsMin = setsMin,
                setsMax = setsMax,
                secondsMin = workMin,
                secondsMax = workMax,
                perSide = perSide,
                each = each,
            )
        } else {
            WorkScheme(
                setsMin = setsMin,
                setsMax = setsMax,
                repsMin = workMin,
                repsMax = workMax,
                perSide = perSide,
                each = each,
            )
        }
    }

    private fun splitNameGroups(body: String): List<List<String>> {
        val stripped = body.replace(Regex("""\([^)]*\)"""), " ").trim()
        if (stripped.isEmpty()) return emptyList()
        return stripped.split(Regex("""\s+\+\s+""")).map { group ->
            group.split(Regex("""\s+or\s+|\s*,\s+|\s+/\s+""", RegexOption.IGNORE_CASE))
                .map { cleanName(it) }
                .filter { it.isNotEmpty() }
        }.filter { it.isNotEmpty() }
    }

    private fun cleanName(name: String): String {
        val unmarked = LIST_PREFIX.replaceFirst(name.trim(), "")
        return ROLE.replace(unmarked, "").trim().trimEnd('—', '–', '-', ',', '.')
    }

    private data class ParsedSessionBuilder(
        val name: String,
        val emphasis: String?,
        val kind: PastedSessionKind,
        val defaultScheme: WorkScheme? = null,
        val lines: MutableList<ParsedWorkLine> = mutableListOf(),
    ) {
        fun build(): ParsedSession = ParsedSession(
            name = name,
            emphasis = emphasis,
            kind = kind,
            lines = lines,
            defaultScheme = defaultScheme,
        )
    }
}

internal object WorkoutCatalogMatch {
    private val STOP = setOf("the", "a", "an", "and", "or", "with", "of", "for", "to")

    fun match(name: String, catalog: List<Exercise>, used: Set<String> = emptySet()): Exercise? {
        val query = normalize(name)
        if (query.isEmpty()) return null
        var best: Exercise? = null
        var bestScore = Int.MIN_VALUE
        catalog.forEach { exercise ->
            if (exercise.id in used) return@forEach
            val score = score(query, exercise) ?: return@forEach
            val rank = CatalogMeta.sortRank(exercise.id)
            val better = score > bestScore ||
                (score == bestScore && best != null && rank < CatalogMeta.sortRank(best.id))
            if (better) {
                best = exercise
                bestScore = score
            }
        }
        return best
    }

    private fun score(query: String, exercise: Exercise): Int? {
        val name = normalize(exercise.name)
        if (name == query) return 10_000
        val terms = CatalogMeta.searchTerms(exercise.id).map { normalize(it) }
        if (terms.any { it == query }) return 8_000
        val movement = exercise.movementKey?.let { normalize(it.replace('-', ' ')) }
        if (movement != null && movement == query) return 7_500
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return null
        val nameTokens = tokens(name)
        if (queryTokens.all { token -> nameTokens.any { matchesToken(it, token) } }) {
            val extra = (nameTokens.size - queryTokens.size).coerceAtLeast(0)
            return 6_000 - extra * 20
        }
        if (terms.any { term -> queryTokens.all { token -> tokens(term).any { matchesToken(it, token) } } }) {
            return 4_000
        }
        if (query.length >= 4 && name.contains(query)) return 2_000
        return null
    }

    internal fun normalize(raw: String): String = raw.lowercase()
        .replace('–', ' ')
        .replace('—', ' ')
        .replace('×', ' ')
        .replace(Regex("""[^a-z0-9/]+"""), " ")
        .trim()
        .replace(Regex("""\s+"""), " ")

    private fun tokens(text: String): List<String> =
        text.split(Regex("""\s+|/""")).map { it.trim() }.filter { it.isNotEmpty() && it !in STOP }

    private fun matchesToken(hay: String, needle: String): Boolean {
        if (hay == needle) return true
        if (stem(hay) == stem(needle)) return true
        return hay.length >= 4 && needle.length >= 4 && (hay.startsWith(needle) || needle.startsWith(hay))
    }

    private fun stem(token: String): String = when {
        token.endsWith("es") && token.length > 4 -> token.dropLast(2)
        token.endsWith("s") && token.length > 3 -> token.dropLast(1)
        else -> token
    }
}
