package com.sinura.personaltrainer.ui.components

/**
 * G3 settings and picker test tags. Picker row ids match the G2 [GymTags] contract so flow 11
 * and G2 start paths stay aligned after #584 merges.
 */
object GymSettingsTags {
    /** Sentinel passed to [GymPickerList.onPick] for Unassigned; matches G2 GymTags.UNASSIGNED_PICK. */
    const val UNASSIGNED_PICK = "__gym_unassigned__"

    const val PICKER_RETRY = "gym-picker-retry"
    fun pickerBadge(id: String): String = "gym-badge-$id"
    fun pickerRow(id: String): String = "gym-row-$id"
    const val PICKER_ROW_UNASSIGNED = "gym-row-unassigned"

    const val SETTINGS_SECTION = "settings-gyms"
    const val SETTINGS_ADD = "gyms-add"
    fun settingsRow(id: String): String = "gyms-row-$id"
    const val SETTINGS_UNASSIGNED = "gyms-unassigned"
    const val SETTINGS_ARCHIVED_KICKER = "gyms-archived-kicker"
    fun settingsArchivedRow(id: String): String = "gyms-archived-$id"
    const val EDIT_SHEET = "gym-edit-sheet"
    const val EDIT_SAVE = "gym-edit-save"
    const val EDIT_ARCHIVE = "gym-edit-archive"
    const val EDIT_RESTORE = "gym-edit-restore"
    fun editField(field: String): String = "gym-edit-$field"
}
