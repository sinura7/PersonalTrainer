package com.sinura.personaltrainer.domain

/**
 * Kind tags stored on [ScheduleRule.templateId] so Plan can name a cardio
 * type or an auxiliary pack without a schema bump.
 *
 * Hours stay on the rule as a sort key (ADR-015, ADR-020). Home and
 * Plan do not show clocks; Up / Down permutes the stored hours.
 */
object ScheduleKind {
    private const val CARDIO = "cardio:"
    private const val AUX = "aux:"

    val planCardioTypes: List<CardioType> = listOf(
        CardioType.WALK,
        CardioType.RUN,
        CardioType.RIDE,
        CardioType.ROW,
        CardioType.SWIM,
        CardioType.HIKE,
    )

    fun cardio(type: CardioType): String = CARDIO + type.name

    fun aux(packId: String): String = AUX + packId

    fun cardioType(templateId: String?): CardioType? {
        if (templateId == null || !templateId.startsWith(CARDIO)) return null
        val raw = templateId.removePrefix(CARDIO)
        return runCatching { CardioType.valueOf(raw) }.getOrNull()
    }

    fun cardioTypeOrRun(templateId: String?): CardioType =
        cardioType(templateId) ?: CardioType.RUN

    fun auxPackId(templateId: String?): String? =
        templateId?.takeIf { it.startsWith(AUX) }?.removePrefix(AUX)

    fun isAux(templateId: String?): Boolean =
        templateId?.startsWith(AUX) == true
}
