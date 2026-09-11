package com.sinura.personaltrainer.data.backup

/**
 * Wrap a backup document in the portable envelope, after both byte budgets
 * have been checked: plaintext first, then the file that will actually be
 * written. Settings' safety-copy export used to call [BackupEnvelope.wrap]
 * itself and skip the document check.
 */
class ProtectBackup {
    operator fun invoke(
        plaintext: String,
        password: CharArray,
        iterations: Int = BackupEnvelope.DEFAULT_ITERATIONS,
    ): String {
        BackupScaleBudget.requireExportable(payload = plaintext, protected = false)
        val envelope = BackupEnvelope.wrap(
            plaintext = plaintext,
            password = password,
            iterations = iterations,
        )
        BackupScaleBudget.requireExportable(payload = envelope, protected = true)
        return envelope
    }
}
