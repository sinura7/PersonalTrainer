package com.sinura.personaltrainer.ui.components

object GymTags {
    fun badge(id: String): String = "gym-badge-$id"
    fun tag(id: String): String = "gym-tag-$id"
    const val TAG_UNASSIGNED = "gym-tag-unassigned"
    const val CHIP = "gym-chip"
    fun row(id: String): String = "gym-row-$id"
    const val ROW_UNASSIGNED = "gym-row-unassigned"
    fun filter(key: String): String = "gym-filter-$key"
    const val PICKER_RETRY = "gym-picker-retry"
    const val MOVE_SHEET = "gym-move-sheet"
    const val MOVE_CONFIRM = "gym-move-confirm"
    const val MOVE_KEEP = "gym-move-keep"
    const val UNASSIGNED_PICK = "__gym_unassigned__"
}
