package com.sinura.personaltrainer.domain

/**
 * Static holds are time, not reps.
 *
 * Dead hangs, wall sits, planks, stretches, and anything the catalog marks
 * isometric / hold / static are prescribed in seconds. The editor shows TIME.
 * The floor starts a work clock. Logging writes those seconds, never a fake
 * 1-rep stand-in.
 */
object HoldWork {
    const val DEFAULT_SECONDS = 30
    const val MIN_SECONDS = 5
    const val MAX_SECONDS = 30 * 60
    const val STEP_SECONDS = 5
    const val HOLD_REPS_PLACEHOLDER = 1

    /**
     * GET READY before the hold clock runs (P2b, owner decision of 29 September 2026: "a timer
     * countdown for exercises that require a timed workout. This gives the user a chance to
     * start"). Holds only: a plank or a hang is entered before the clock, and the tap that
     * starts it is made standing up. The set stopwatch gets none; the lifter starts it ready.
     * Three lengths, no Off: 3 s is the shortest a body needs.
     */
    const val LEAD_IN_DEFAULT_SECONDS = 5
    val LEAD_IN_CHOICES: List<Int> = listOf(3, 5, 10)
    const val LEAD_IN_KICKER = "GET READY"

    /** The nearest offered lead-in to [requested]; the default when nothing is asked. */
    fun leadInSeconds(requested: Int?): Int {
        val wanted = requested ?: return LEAD_IN_DEFAULT_SECONDS
        return LEAD_IN_CHOICES.minByOrNull { kotlin.math.abs(it - wanted) } ?: LEAD_IN_DEFAULT_SECONDS
    }

    /** Whole seconds of GET READY left before the hold starts at [holdStartElapsedRealtime]. */
    fun leadInRemaining(holdStartElapsedRealtime: Long, nowElapsedRealtime: Long): Int =
        RestTimer.remainingSeconds(holdStartElapsedRealtime, nowElapsedRealtime)

    private val HOLD_MOVEMENT_KEYS = setOf("plank", "hold", "isometric", "static")

    fun isHold(exercise: Exercise): Boolean =
        isHold(exercise.id, exercise.name, exercise.movementKey)

    fun isHold(id: String, name: String, movementKey: String? = null): Boolean {
        val movement = movementKey?.lowercase()
        if (movement != null && movement in HOLD_MOVEMENT_KEYS) return true
        return looksLikeHold(id, name)
    }

    /**
     * Catalog and custom names. `hanging leg raise` is reps; `dead hang` is not.
     */
    fun looksLikeHold(id: String, name: String): Boolean {
        val idKey = id.lowercase()
        val nameKey = name.lowercase()
        if (isHangingRepLift(idKey, nameKey)) return false
        if (HOLD_MOVEMENT_KEYS.any { nameKey.contains(it) }) return true
        if (listOf("wall sit", "wall-sit", "wallsit").any {
                nameKey.contains(it) || idKey.contains(it)
            }
        ) {
            return true
        }
        if (nameKey.contains("stretch") || idKey.contains("stretch")) return true
        if (nameKey.contains("plank") || idKey.contains("plank")) return true
        if (nameKey.contains("hold") || idKey.contains("hold")) return true
        if (nameKey.contains("dead hang") || nameKey.contains("scapular hang")) return true
        if (nameKey.endsWith(" hang") || nameKey == "hang") return true
        if (idKey.contains("deadhang") || idKey.contains("scapular-hang")) return true
        if (nameKey.contains("90/90") || nameKey.contains("90-90") || idKey.contains("90-90")) {
            return true
        }
        if (nameKey.contains("pigeon") || idKey.contains("pigeon")) return true
        return idKey.endsWith("-hang") || idKey.contains("-hang-")
    }

    /** Hanging leg raises are reps. Dead / scapular hangs are not. */
    private fun isHangingRepLift(idKey: String, nameKey: String): Boolean {
        val hanging = idKey.contains("hanging") || nameKey.contains("hanging")
        if (!hanging) return false
        return listOf("dead", "scapular", "hold", "plank", "stretch")
            .none { idKey.contains(it) || nameKey.contains(it) }
    }

    fun countdownSeconds(targetSeconds: Int?, fallback: Int = DEFAULT_SECONDS): Int =
        (targetSeconds ?: fallback).coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun formatRange(minSeconds: Int, maxSeconds: Int? = null): String {
        val lo = minSeconds.coerceAtLeast(0)
        val hi = maxSeconds?.takeIf { it > lo }
        return if (hi == null) "${lo}s" else "$lo–${hi}s"
    }

    fun workLine(sets: Int, minSeconds: Int, maxSeconds: Int? = null): String =
        "$sets × ${formatRange(minSeconds, maxSeconds)}"

    fun clock(seconds: Int): String = RestTimer.formatClock(seconds)

    /**
     * Seconds actually held. Countdown default: remaining 0 logs the plan,
     * an early tap logs elapsed, never zero.
     */
    fun elapsedSeconds(totalSeconds: Int, remainingSeconds: Int): Int {
        val total = totalSeconds.coerceAtLeast(1)
        val remaining = remainingSeconds.coerceAtLeast(0)
        return (total - remaining).coerceIn(1, total)
    }

    /**
     * Displayed elapsed from [SystemClock.elapsedRealtime], not a `delay`
     * tick counter. Floor so a stall or a process restore cannot jump
     * backwards; cap at the prescribed total.
     */
    fun elapsedFromRealtime(
        startElapsedRealtime: Long,
        nowElapsedRealtime: Long,
        totalSeconds: Int,
    ): Int {
        if (nowElapsedRealtime < startElapsedRealtime) return 0
        val elapsed = ((nowElapsedRealtime - startElapsedRealtime) / 1_000L).toInt()
        return elapsed.coerceIn(0, totalSeconds.coerceAtLeast(0))
    }

    fun remainingFromDeadline(
        deadlineElapsedRealtime: Long,
        nowElapsedRealtime: Long,
        totalSeconds: Int,
    ): Int = RestTimer.remainingSeconds(deadlineElapsedRealtime, nowElapsedRealtime)
        .coerceAtMost(totalSeconds.coerceAtLeast(0))

    fun deadlineElapsedRealtime(startElapsedRealtime: Long, totalSeconds: Int): Long =
        startElapsedRealtime + totalSeconds.coerceAtLeast(0) * 1_000L

    const val DONE = "HOLD DONE"

    /**
     * Elapsed readout for persistence and [HoldTimerUiState.clock].
     * The dock bar uses [liveDockClock] so a live hold never paints as
     * idle `HOLD 0:00`.
     */
    fun dockClock(elapsedSeconds: Int, targetReached: Boolean): String =
        if (targetReached) DONE else "$HOLD_KICKER ${clock(elapsedSeconds.coerceAtLeast(0))}"

    fun liveDockSeconds(
        remainingSeconds: Int,
        targetReached: Boolean,
        running: Boolean = false,
        totalSeconds: Int = 0,
    ): Int {
        if (targetReached) return remainingSeconds.coerceAtLeast(0)
        return when {
            running && totalSeconds > 0 -> remainingSeconds.coerceIn(1, totalSeconds)
            running -> remainingSeconds.coerceAtLeast(1)
            else -> remainingSeconds.coerceAtLeast(0)
        }
    }

    /**
     * Live hold instrument: remaining countdown, same geometry as REST.
     * A running hold with a target shows at least `0:01`, never `0:00`.
     */
    fun liveDockClock(
        remainingSeconds: Int,
        targetReached: Boolean,
        running: Boolean = false,
        totalSeconds: Int = 0,
    ): String {
        if (targetReached) return DONE
        return "$HOLD_KICKER ${clock(liveDockSeconds(remainingSeconds, false, running, totalSeconds))}"
    }

    const val HOLD_KICKER = "HOLD"

    fun nextSeconds(current: Int, direction: Int): Int =
        (current + direction * STEP_SECONDS).coerceIn(MIN_SECONDS, MAX_SECONDS)

    data class HoldRange(val minSeconds: Int, val maxSeconds: Int? = null)

    /**
     * `20`, `20s`, `0:30`, `20-40`, `20–40s`.
     */
    fun parseRange(input: String): HoldRange? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val cleaned = trimmed.replace(Regex("""(?i)\s*secs?\b"""), "").trim()
        val parts = cleaned.split(Regex("""\s*[-–—]\s*"""))
        return if (parts.size == 2) {
            val first = parseOne(parts[0]) ?: return null
            val second = parseOne(parts[1]) ?: return null
            val lo = minOf(first, second)
            val hi = maxOf(first, second)
            HoldRange(lo, hi.takeIf { it != lo })
        } else {
            val one = parseOne(cleaned) ?: return null
            HoldRange(one, null)
        }
    }

    private fun parseOne(raw: String): Int? {
        val text = raw.trim().trimEnd('s', 'S')
        if (text.isEmpty()) return null
        val seconds = if (text.contains(":")) {
            val parts = text.split(":")
            if (parts.size != 2) return null
            val minutes = parts[0].trim().toIntOrNull() ?: return null
            val secs = parts[1].trim().toIntOrNull() ?: return null
            if (minutes < 0 || secs !in 0..59) return null
            minutes * 60 + secs
        } else {
            text.toIntOrNull() ?: return null
        }
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) return null
        return seconds
    }
}

data class HoldTimerUiState(
    val running: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    /** When the hold clock itself starts: the tap plus the lead-in. */
    val startElapsedRealtime: Long = 0L,
    val deadlineElapsedRealtime: Long = 0L,
    val targetReached: Boolean = false,
    /** The tap that started GET READY; equal to [startElapsedRealtime] with no lead-in. */
    val leadInStartElapsedRealtime: Long = 0L,
    /** Whole seconds of GET READY left; zero once the hold clock runs. */
    val leadInRemainingSeconds: Int = 0,
) {
    val active: Boolean get() = running || totalSeconds > 0

    /** GET READY is counting: the hold is armed and running, and its clock has not started. */
    val gettingReady: Boolean get() = running && leadInRemainingSeconds > 0
    val clock: String get() = HoldWork.dockClock(elapsedSeconds, targetReached)
}
