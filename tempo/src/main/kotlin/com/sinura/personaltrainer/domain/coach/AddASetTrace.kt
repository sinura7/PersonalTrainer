package com.sinura.personaltrainer.domain.coach

import com.sinura.personaltrainer.domain.RuleTrace
import com.sinura.personaltrainer.domain.TraceFact
import com.sinura.personaltrainer.domain.TraceThreshold

/** The volume offer's explanation; the seed recommendation keeps its own micro-rec trace. */
object AddASetTrace {
    const val RULE_ID = "tempo-add-a-set"
    const val ACTION = "ADD_A_SET"
    const val CURRENT_PLAN_TARGET = "CURRENT_PLAN_TARGET"

    data class Metadata(
        val generatedAtMs: Long,
        val todayEpochDay: Long,
        val currentWeekStartEpochDay: Long,
        val zoneId: String,
    )

    fun from(offer: AddASetPolicy.Offer, seedTrace: RuleTrace): RuleTrace {
        val d = offer.decision
        val metadata = d.traceMetadata
        // Unknown aggregate dates remain unknown (the existing empty-window convention),
        // rather than borrowing the seed's one-day window for three weeks of volume.
        return RuleTrace(
            ruleId = RULE_ID,
            version = 1,
            action = ACTION,
            reasonCodes = listOf("PLAN_COMPLETE", "READINESS_MET", "LOAD_NOT_PREFERRED"),
            evidenceStartEpochDay = metadata?.currentWeekStartEpochDay?.minus(14) ?: 1L,
            evidenceEndEpochDay = metadata?.todayEpochDay ?: 0L,
            facts = buildList {
                fun fact(name: String, value: Any?) { add(TraceFact(name, value?.toString() ?: "unavailable")) }
                fact("goal", d.goal.name)
                fact("muscle", d.primaryMuscle?.displayName)
                fact("targetSets", d.targetSets)
                fact("workingLogged", d.workingLogged)
                fact("todayWorkingCount", d.todayWorkingCount)
                fact("lighterWeek", d.lighterWeek)
                fact("extraSetAlreadyAccepted", d.extraSetAlreadyAccepted)
                fact("dismissed", d.dismissed)
                fact("manualExtra", d.manualExtra)
                fact("rpeLogged", d.rpeLogged)
                fact("meanRpe", d.meanRpe)
                fact("weeklySets", d.weeklySets)
                fact("weeklySetsByWeek", d.weeklySetsByWeek.joinToString(","))
                fact("comparisonAvailable", d.comparisonAvailable)
                fact("comparisonDate", "unavailable")
                fact("effortSignal", d.effortSignal)
                fact("completionSignal", d.completionSignal)
                fact("performanceSignal", d.performanceSignal)
                fact("weeklyVolumeSignal", d.weeklyVolumeSignal)
                fact("trendOrBlockSignal", d.trendOrBlockSignal)
                fact("trendSignal", d.trendSignal)
                fact("blockSignal", d.blockSignal)
                fact("readinessCount", d.readinessCount)
                fact("blockWeekIndex", d.blockWeekIndex)
                fact("blockComparisonSets", d.blockComparisonSets)
                fact("blockComparisonSource", d.blockComparisonSource)
                fact("preferLoad", d.preferLoad)
                fact("lastRpe", d.lastRpe)
                fact("lastReps", d.lastReps)
                fact("targetReps", d.targetReps)
                fact("seedReason", d.seedReason)
                fact("loadProgressionBlocked", d.loadProgressionBlocked)
                fact("currentWeekStartEpochDay", metadata?.currentWeekStartEpochDay)
                fact("zoneId", metadata?.zoneId)
                fact("windowScope", "current week and prior two calendar weeks; comparison date unavailable")
                seedTrace.facts.firstOrNull { it.name == "lastSet" }?.let(::add)
            },
            thresholds = listOf(
                TraceThreshold("readinessRequired", AddASetPolicy.READINESS_THRESHOLD.toString()),
                TraceThreshold("readinessSignals", AddASetPolicy.READINESS_SIGNALS.toString()),
                TraceThreshold("meanRpeCeiling", AddASetPolicy.MEAN_RPE_CEILING.toString()),
                TraceThreshold("weeklySetGuide", AddASetPolicy.SOFT_WEEKLY_SET_TARGET.toString()),
                TraceThreshold("plannedSetCap", "3"),
                TraceThreshold("minimumLoggedEfforts", "2"),
                TraceThreshold("extraSetCap", "1"),
                TraceThreshold("blockWeeks", "4,5"),
                TraceThreshold("preferLoadRpeCeiling", AddASetPolicy.PREFER_LOAD_RPE_CEILING.toString()),
            ),
            alternatives = listOf("keep the planned sets", "prefer the existing load progression"),
            generatedAtMs = metadata?.generatedAtMs ?: seedTrace.generatedAtMs,
        )
    }
}
