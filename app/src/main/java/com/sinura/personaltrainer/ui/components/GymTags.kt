package com.sinura.personaltrainer.ui.components

object GymTags {
    const val CHIP = "gym-chip"
    const val PLATES = "gym-plates"
    const val PICKER_RETRY = "gym-picker-retry"
    const val ROW_UNASSIGNED = "gym-row-unassigned"
    const val TAG_UNASSIGNED = "gym-tag-unassigned"
    const val UNASSIGNED_PICK = "__unassigned__"

    fun plate(gymId: String): String = "gym-plate-$gymId"
    fun row(gymId: String): String = "gym-row-$gymId"
    fun badge(gymId: String): String = "gym-badge-$gymId"
    fun tag(gymId: String): String = "gym-tag-$gymId"
    fun filter(id: String): String = "gym-filter-$id"
}
