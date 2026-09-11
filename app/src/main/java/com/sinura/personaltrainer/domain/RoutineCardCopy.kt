package com.sinura.personaltrainer.domain

/**
 * How a start-sheet routine card names its kit and how many lifts it pictures.
 *
 * S-02: the card used to be a title and a numbered name list. The stills
 * are the first [STILL_LIMIT] lifts; [mix] is the equipment in tap order,
 * unique, so a machine day reads as machines rather than a paragraph of
 * names. Home's day board pictures four ([DayBlockCopy.STILL_LIMIT]); this
 * sheet pictures three, which is what the audit asked for here.
 */
object RoutineCardCopy {
    const val STILL_LIMIT = 3

    /**
     * Unique [EquipmentType.label] values in first-seen order.
     * Empty when there are no lifts. One type is that type's label
     * (`Machine`). A mixed floor is `Machine · Barbell`.
     */
    fun mix(equipment: List<EquipmentType>): String? {
        if (equipment.isEmpty()) return null
        return equipment.map { it.label }.distinct().joinToString(" · ")
    }
}
