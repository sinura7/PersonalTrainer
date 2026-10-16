package com.sinura.personaltrainer.domain

/**
 * One rest length for the next clock, with an honest source tag (UX blueprint T2).
 *
 * Precedence: Tempo prescription from the next-set coach, then the routine row,
 * then the last preset on this phone, then Settings default.
 */
enum class RestPlanSource {
    TEMPO,
    ROUTINE,
    LAST_PRESET,
    DEFAULT,
}

data class RestPlan(
    val seconds: Int,
    val source: RestPlanSource,
    val tempoDetail: String? = null,
) {
    fun sourceTag(): String = when (source) {
        RestPlanSource.TEMPO -> "Tempo · ${tempoDetail ?: "set"}"
        RestPlanSource.ROUTINE -> "Routine"
        RestPlanSource.LAST_PRESET -> "Last used"
        RestPlanSource.DEFAULT -> "Default"
    }

    fun plannedLabel(clock: String): String = "${sourceTag()} · $clock"
}

object RestPlanResolver {
    fun resolve(
        exerciseRestSeconds: Int?,
        preferences: RestTimerPreferences,
        prescribedSeconds: Int?,
        tempoRepsForLabel: Int? = null,
    ): RestPlan {
        val min = RestTimerPreferences.MIN_SECONDS
        val max = RestTimerPreferences.MAX_SECONDS
        prescribedSeconds?.takeIf { it > 0 }?.let { prescribed ->
            return RestPlan(
                seconds = prescribed.coerceIn(min, max),
                source = RestPlanSource.TEMPO,
                tempoDetail = tempoRepsForLabel?.let { RestPrescription.tempoRestDetail(it) },
            )
        }
        exerciseRestSeconds?.takeIf { it > 0 }?.let { routine ->
            return RestPlan(
                seconds = routine.coerceIn(min, max),
                source = RestPlanSource.ROUTINE,
            )
        }
        preferences.lastPresetSeconds?.takeIf { it > 0 }?.let { last ->
            return RestPlan(
                seconds = last.coerceIn(min, max),
                source = RestPlanSource.LAST_PRESET,
            )
        }
        return RestPlan(
            seconds = preferences.defaultRestSeconds.coerceIn(min, max),
            source = RestPlanSource.DEFAULT,
        )
    }
}
