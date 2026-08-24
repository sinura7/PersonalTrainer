package com.sinura.personaltrainer.data.backup

/**
 * A verified copy of the phone taken immediately before a restore wipe.
 *
 * The id is a filename only (`pre-restore-<millis>.json`). Settings lists
 * counts and a date. Raw private paths never leave the store.
 */
data class SafetySnapshotMeta(
    val id: String,
    val createdAtMillis: Long,
    val authored: AuthoredInventory,
) {
    val title: String get() = TITLE

    fun subtitle(dateLabel: String): String = "${authored.describe()} \u00b7 $dateLabel"

    companion object {
        const val TITLE = "Safety copy"
    }
}

object SafetySnapshot {
    const val KEEP = 3

    const val WRITE_FAILED =
        "A verified safety copy could not be saved on this phone, so restore was cancelled. " +
            "Nothing was changed."
    const val VERIFY_FAILED =
        "The safety copy did not match the training data on this phone, so restore was cancelled. " +
            "Nothing was changed."
    const val MISSING_DIR =
        "This phone has no place to keep a safety copy, so restore was cancelled. " +
            "Nothing was changed."
    const val NOT_FOUND = "That safety copy is no longer on this phone."
    const val BAD_ID = "That is not a safety copy."
    const val DELETE_FAILED = "That safety copy could not be deleted. Try again."

    private val ID_REGEX = Regex("^pre-restore-\\d+\\.json$")

    fun isSafeId(id: String): Boolean = ID_REGEX.matches(id)

    fun fileName(createdAtMillis: Long): String = "pre-restore-$createdAtMillis.json"

    fun createdAtFromId(id: String): Long? {
        if (!isSafeId(id)) return null
        return id.removePrefix("pre-restore-").removeSuffix(".json").toLongOrNull()
    }

    /**
     * Decode, structurally validate, and require authored counts equal [expected].
     *
     * [expected] is the inventory taken from the phone at write time. A
     * mismatch means the file on disk is not the copy we thought we wrote.
     */
    fun verify(json: String, expected: AuthoredInventory): AuthoredInventory {
        val document = try {
            BackupJson.decode(json)
        } catch (thrown: BackupException) {
            throw BackupException(VERIFY_FAILED)
        } catch (_: Exception) {
            throw BackupException(VERIFY_FAILED)
        }
        val validation = BackupValidator.validate(
            document = document,
            localAuthored = AuthoredInventory.EMPTY,
            allowEmptyDestructiveRestore = true,
        )
        if (validation is BackupValidation.Invalid) {
            throw BackupException(VERIFY_FAILED)
        }
        val authored = AuthoredInventory.fromDocument(document)
        if (authored != expected) {
            throw BackupException(VERIFY_FAILED)
        }
        return authored
    }
}
