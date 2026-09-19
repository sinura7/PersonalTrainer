package com.sinura.personaltrainer.domain

/**
 * Structured local explanation (ADR-008 / P8.4). Produced with the
 * advice. A later API may narrate this object; it may not invent a
 * new load or plan.
 */
data class TraceFact(
    val name: String,
    val value: String,
)

data class TraceThreshold(
    val name: String,
    val value: String,
)

data class RuleTrace(
    val ruleId: String,
    val version: Int = VERSION,
    val action: String,
    val reasonCodes: List<String>,
    val evidenceStartEpochDay: Long,
    val evidenceEndEpochDay: Long,
    val facts: List<TraceFact>,
    val thresholds: List<TraceThreshold>,
    val alternatives: List<String>,
    val generatedAtMs: Long,
) {
    companion object {
        const val VERSION = 1
        const val STALL = "STALL"
        const val VOLUME_RAMP = "VOLUME_RAMP"

        fun forRecommendation(
            recommendation: TrainingRecommendation,
            nowMs: Long,
            evidenceStartEpochDay: Long,
            evidenceEndEpochDay: Long,
        ): RuleTrace = RuleTrace(
            ruleId = recommendation.id,
            version = VERSION,
            action = recommendation.action?.name ?: "NONE",
            reasonCodes = listOf(recommendation.kicker),
            evidenceStartEpochDay = evidenceStartEpochDay,
            evidenceEndEpochDay = evidenceEndEpochDay,
            facts = listOf(
                TraceFact("title", recommendation.title),
                TraceFact("reason", recommendation.reason),
            ),
            thresholds = emptyList(),
            alternatives = emptyList(),
            generatedAtMs = nowMs,
        )

        fun forGeneration(answers: OnboardingAnswers, split: SplitStyle): RuleTrace {
            val dose = SessionDose.from(answers)
            return RuleTrace(
                ruleId = "program-generate",
                version = VERSION,
                action = "GENERATE_WEEK",
                reasonCodes = SplitDerivation.reasonCodes(answers) +
                    listOf(
                        "GOAL_${answers.goal.name}",
                        "AGE_${answers.trainingAge.name}",
                        "DAYS_${dose.daysPerWeek}",
                    ),
                evidenceStartEpochDay = 0L,
                evidenceEndEpochDay = 0L,
                facts = listOf(
                    TraceFact("split", split.name),
                    TraceFact("why", SplitDerivation.why(answers)),
                    TraceFact("liftsPerSession", answers.trainingAge.liftsPerSession.toString()),
                    TraceFact("goal", answers.goal.name),
                    TraceFact("age", answers.trainingAge.name),
                    TraceFact("days", dose.daysPerWeek.toString()),
                ),
                thresholds = listOf(
                    TraceThreshold("samePatternRestHours", "48"),
                    TraceThreshold("noviceSets", "3"),
                    TraceThreshold("hypertrophyWeeklySetsLandmark", "10"),
                ),
                alternatives = SplitDerivation.alternatives(answers),
                generatedAtMs = 0L,
            )
        }

        fun forHint(hint: ProgressionHint, nowMs: Long, todayEpochDay: Long): RuleTrace =
            RuleTrace(
                ruleId = "progression-${hint.exerciseId}",
                version = VERSION,
                action = hint.action.name,
                reasonCodes = buildList {
                    add(hint.action.name)
                    if (hint.rpeHold) add("RPE_HOLD")
                    if (hint.lighterHold) add("LIGHTER_HOLD")
                },
                evidenceStartEpochDay = todayEpochDay,
                evidenceEndEpochDay = todayEpochDay,
                facts = listOf(
                    TraceFact("exercise", hint.exerciseName),
                    TraceFact("lastWeightKg", hint.lastWeightKg.toString()),
                    TraceFact("suggestedWeightKg", hint.suggestedWeightKg.toString()),
                    TraceFact("lastReps", hint.lastReps.toString()),
                    TraceFact("suggestedReps", hint.suggestedReps.toString()),
                ),
                thresholds = listOf(TraceThreshold("targetReps", hint.targetReps.toString())),
                alternatives = emptyList(),
                generatedAtMs = nowMs,
            )

        fun forStall(finding: StallFinding, nowMs: Long): RuleTrace = RuleTrace(
            ruleId = "stall-${finding.exerciseId}",
            version = VERSION,
            action = RecommendationAction.MARK_LIGHTER_WEEK.name,
            reasonCodes = listOf(RecommendationEngine.KICKER_PROGRESSION, STALL),
            evidenceStartEpochDay = 1L,
            evidenceEndEpochDay = 0L,
            facts = listOf(
                TraceFact("exercise", finding.exerciseName),
                TraceFact("sessionsHeld", finding.sessionsHeld.toString()),
            ),
            thresholds = listOf(
                TraceThreshold("stallSessions", StallSignal.STALL_SESSIONS.toString()),
            ),
            alternatives = listOf(
                "swap the lift",
                "cut the load",
                "change the rep target",
            ),
            generatedAtMs = nowMs,
        )

        fun forVolumeRamp(
            finding: VolumeRampFinding,
            nowMs: Long,
            evidenceStartEpochDay: Long,
            evidenceEndEpochDay: Long,
        ): RuleTrace = RuleTrace(
            ruleId = "volume-ramp-${finding.muscle.name}",
            version = VERSION,
            action = RecommendationAction.OPEN_BODY_MAP.name,
            reasonCodes = listOf(RecommendationEngine.KICKER_LOAD, VOLUME_RAMP),
            evidenceStartEpochDay = evidenceStartEpochDay,
            evidenceEndEpochDay = evidenceEndEpochDay,
            facts = listOf(
                TraceFact("muscle", finding.muscle.displayName),
                TraceFact("lastWeekSets", finding.lastWeekSets.toString()),
                TraceFact("suggestedSets", finding.suggestedSets.toString()),
            ),
            thresholds = listOf(
                TraceThreshold("rpeCeiling", VolumeRamp.RPE_CEILING.toString()),
                TraceThreshold("addSets", VolumeRamp.ADD_SETS.toString()),
                TraceThreshold("highMinSets", HeatBand.HIGH_MIN_SETS.toInt().toString()),
            ),
            alternatives = listOf("write the extra sets into the plan"),
            generatedAtMs = nowMs,
        )

        fun forMicroRec(
            reasonCodes: List<String>,
            nextWeightKg: Double,
            nextReps: Int,
            nextRpe: Int?,
            nowMs: Long,
            todayEpochDay: Long,
            lastWeightKg: Double? = null,
            lastReps: Int? = null,
            lastRpe: Int? = null,
            lastSetLine: String? = null,
            incrementLabel: String? = null,
            targetReps: Int? = null,
            targetSets: Int? = null,
            restSeconds: Int? = null,
            call: String? = null,
            rule: String? = null,
            alternatives: List<String> = emptyList(),
        ): RuleTrace = RuleTrace(
            ruleId = SetMicroRecCalculator.RULE_ID,
            version = VERSION,
            action = reasonCodes.firstOrNull().orEmpty(),
            reasonCodes = reasonCodes,
            evidenceStartEpochDay = todayEpochDay,
            evidenceEndEpochDay = todayEpochDay,
            facts = buildList {
                call?.let { add(TraceFact("call", it)) }
                lastSetLine?.let { add(TraceFact("lastSet", it)) }
                lastWeightKg?.let { add(TraceFact("lastWeightKg", it.toString())) }
                lastReps?.let { add(TraceFact("lastReps", it.toString())) }
                lastRpe?.let { add(TraceFact("lastRpe", it.toString())) }
                add(TraceFact("nextWeightKg", nextWeightKg.toString()))
                add(TraceFact("nextReps", nextReps.toString()))
                nextRpe?.let { add(TraceFact("nextRpe", it.toString())) }
                incrementLabel?.let { add(TraceFact("increment", it)) }
                restSeconds?.let { add(TraceFact("rest", "Start at ${RestTimer.formatClock(it)}")) }
                rule?.let { add(TraceFact("rule", it)) }
            },
            thresholds = buildList {
                targetReps?.let { add(TraceThreshold("targetReps", it.toString())) }
                targetSets?.let { add(TraceThreshold("targetSets", it.toString())) }
                incrementLabel?.let { add(TraceThreshold("increment", incrementLabel)) }
                if (reasonCodes.contains(SetMicroRecCalculator.RPE_HOLD) ||
                    reasonCodes.contains(SetMicroRecCalculator.TOP_SET)
                ) {
                    add(
                        TraceThreshold(
                            "rpeHold",
                            RpeModifier.RPE_HOLD_THRESHOLD.toInt().toString() + "+",
                        ),
                    )
                }
            },
            alternatives = alternatives,
            generatedAtMs = nowMs,
        )
    }
}
