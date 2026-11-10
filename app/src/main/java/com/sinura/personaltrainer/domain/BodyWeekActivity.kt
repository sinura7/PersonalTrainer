package com.sinura.personaltrainer.domain

/** Cardio and Extra minutes for the Body trends card (F1, mockup D). */
data class BodyWeekActivity(
    val cardioSessions: Int = 0,
    val cardioMinutes: Int = 0,
    val cardioDistanceMeters: Double? = null,
    val warmUpStretchMinutes: Int = 0,
)

object BodyWeekActivityCopy {
    const val CARDIO_TITLE = "Cardio this week"
    const val WARMUP_TITLE = "Warm-up and stretch"
    fun cardioLine(sessions: Int, minutes: Int, distanceMeters: Double?): String {
        val parts = mutableListOf<String>()
        parts += if (sessions == 1) "1 session" else "$sessions sessions"
        parts += "$minutes min"
        distanceMeters?.takeIf { it > 0.0 }?.let { m ->
            val km = m / 1000.0
            parts += if (km >= 10) "${km.toInt()} km" else String.format(java.util.Locale.US, "%.1f km", km)
        }
        return parts.joinToString(" · ")
    }

    fun warmupLine(minutes: Int): String = "$minutes min"
}

object BodyWeekActivityMath {
    fun forWeek(
        weekStartEpochDay: Long,
        weekEndEpochDay: Long,
        activitySummaries: List<SessionSummary>,
        auxiliaryWorkoutMinutes: Int,
    ): BodyWeekActivity {
        var cardioSessions = 0
        var cardioMinutes = 0
        var distance = 0.0
        var hasDistance = false
        activitySummaries.forEach { summary ->
            if (summary.localEpochDay !in weekStartEpochDay..weekEndEpochDay) return@forEach
            if (summary.cardioSeconds > 0L) {
                cardioSessions += 1
                cardioMinutes += (summary.cardioSeconds / 60L).toInt().coerceAtLeast(1)
                summary.cardioDistanceMeters?.let {
                    distance += it
                    hasDistance = true
                }
            }
        }
        return BodyWeekActivity(
            cardioSessions = cardioSessions,
            cardioMinutes = cardioMinutes,
            cardioDistanceMeters = distance.takeIf { hasDistance },
            warmUpStretchMinutes = auxiliaryWorkoutMinutes.coerceAtLeast(0),
        )
    }
}
