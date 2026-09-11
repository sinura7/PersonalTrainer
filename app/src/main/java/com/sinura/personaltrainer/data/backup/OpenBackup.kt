package com.sinura.personaltrainer.data.backup

/**
 * Open a backup file. An envelope unwraps with the password; a legacy
 * plaintext document is returned unchanged. The safety-copy import and
 * every other open path go through here so a new protection method cannot
 * land in one caller and miss another.
 */
class OpenBackup {
    operator fun invoke(raw: String, password: CharArray?): String =
        BackupEnvelope.open(raw, password)
}
