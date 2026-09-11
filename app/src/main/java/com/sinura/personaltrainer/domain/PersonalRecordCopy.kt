package com.sinura.personaltrainer.domain

/**
 * What a broken record is called when the app is congratulating you for it.
 *
 * `PersonalRecordKind.label` is the readout wording — "Heaviest", "Most reps", "Est. 1RM" — for
 * a row in a list. These are the longer sentences for the two surfaces that celebrate: the
 * banner on the live floor and the hero on the summary. Both had their own copy of the strings,
 * in two different shapes, and the two had already drifted: the live banner said
 * "Strongest set ever" where the summary said "Best estimated 1RM" for the same record.
 */
object PersonalRecordCopy {
    fun celebration(kind: PersonalRecordKind): String = when (kind) {
        PersonalRecordKind.WEIGHT -> "Heaviest ever"
        PersonalRecordKind.REPS -> "Most reps ever"
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX -> "Best estimated 1RM"
        PersonalRecordKind.REPS_AT_WEIGHT -> "Most reps at that weight"
    }

    /**
     * The one headline for a set that broke several records at once.
     *
     * REPS outranks everything, because it is the only record a bodyweight lift can break: any
     * other order leaves the banner saying "most reps at this weight" about a push-up, whose
     * weight is nothing. Then heaviest, then the estimate, then reps-at-weight — strongest
     * claim the set can honestly make.
     */
    fun headline(kinds: Set<PersonalRecordKind>): String = when {
        PersonalRecordKind.REPS in kinds -> celebration(PersonalRecordKind.REPS)
        PersonalRecordKind.WEIGHT in kinds -> celebration(PersonalRecordKind.WEIGHT)
        PersonalRecordKind.ESTIMATED_ONE_REP_MAX in kinds ->
            celebration(PersonalRecordKind.ESTIMATED_ONE_REP_MAX)
        else -> celebration(PersonalRecordKind.REPS_AT_WEIGHT)
    }
}
