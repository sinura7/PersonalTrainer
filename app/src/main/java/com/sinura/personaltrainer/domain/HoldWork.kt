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
) {
    val clock: String get() = HoldWork.clock(if (running) remainingSeconds else totalSeconds)
}
