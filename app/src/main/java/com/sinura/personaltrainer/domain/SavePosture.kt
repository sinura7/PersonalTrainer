package com.sinura.personaltrainer.domain

/**
 * How the owner wants training data saved off this phone.
 *
 * Training never depends on either lane ([ADR-004]). Temper Account is cloud sync;
 * local keeps Room as source of truth with optional Google Drive whole-file backup.
 */
enum class SavePosture {
    /** Room on this phone; optional Drive backup from Settings. */
    LOCAL,
    /** Signed-in Temper Account; Room is cache while online sync runs. */
    ACCOUNT,
    ;

    companion object {
        fun fromStorage(raw: String?): SavePosture? =
            raw?.let { stored ->
                entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
            }
    }
}

data class SavePostureState(
    val chosen: Boolean,
    val posture: SavePosture = SavePosture.LOCAL,
)

/**
 * Upgrades before the first-launch chooser existed already made a save decision
 * implicitly — permissions walk, plan setup, Drive, or Account.
 */
fun inferLegacySavePosture(
    launchPermissionsAsked: Boolean,
    onboardingComplete: Boolean,
    driveAccountEmail: String?,
    accountSignedIn: Boolean,
): SavePosture? {
    val legacy = launchPermissionsAsked ||
        onboardingComplete ||
        !driveAccountEmail.isNullOrBlank() ||
        accountSignedIn
    if (!legacy) return null
    return if (accountSignedIn) SavePosture.ACCOUNT else SavePosture.LOCAL
}
