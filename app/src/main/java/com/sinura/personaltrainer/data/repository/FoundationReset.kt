package com.sinura.personaltrainer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sinura.personaltrainer.data.local.FoundationGeneration
import com.sinura.personaltrainer.data.local.TemperDatabase
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.CancellationException

data class ResetAcknowledgements(
    val hasExternalExport: Boolean,
    val understandsIrreversible: Boolean,
)

sealed class ResetResult {
    data object Accepted : ResetResult()
    data class Refused(val reason: String) : ResetResult()
    data class Failed(val reason: String) : ResetResult()
}

/**
 * The one authorized development reset (ADR-010 §2–7).
 *
 * Integrity-checks and seeds [TemperDatabase] *before* the legacy file is
 * deleted. When [FoundationGeneration.FROZEN] is true, wiping the foundation
 * database is refused. Deleting a leftover `personal_trainer.db` is cleanup,
 * not a second reset.
 */
class FoundationReset(
    private val context: Context,
    private val preferences: PreferencesRepository,
    private val onBeforeReset: suspend () -> Unit = {},
    private val frozen: Boolean = FoundationGeneration.FROZEN,
    private val openTemper: (Context) -> TemperDatabase = { ctx ->
        Room.databaseBuilder(
            ctx.applicationContext,
            TemperDatabase::class.java,
            FoundationGeneration.DATABASE_FILE,
        ).build()
    },
    private val deleteDatabase: (Context, String) -> Boolean = { ctx, name ->
        ctx.deleteDatabase(name)
    },
    private val legacyExists: (Context) -> Boolean = { ctx ->
        ctx.getDatabasePath(FoundationGeneration.LEGACY_DATABASE_FILE).exists()
    },
    private val temperExists: (Context) -> Boolean = { ctx ->
        ctx.getDatabasePath(FoundationGeneration.DATABASE_FILE).exists()
    },
) {
    suspend fun run(acknowledgements: ResetAcknowledgements): ResetResult {
        if (!acknowledgements.hasExternalExport) {
            return ResetResult.Refused("Keep an export off this device first.")
        }
        if (!acknowledgements.understandsIrreversible) {
            return ResetResult.Refused("This reset cannot be undone.")
        }
        if (frozen && temperExists(context) && !legacyExists(context)) {
            return ResetResult.Refused("Foundation generation is frozen.")
        }

        onBeforeReset()

        val temper = try {
            openTemper(context)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w(TAG, "Could not open TemperDatabase", error)
            return ResetResult.Failed("The new database could not be opened.")
        }

        try {
            if (!integrityOk(temper.openHelper.readableDatabase)) {
                return ResetResult.Failed("The new database failed its integrity check.")
            }
            DbMaintenance(temper).seedCatalog()
            val exercises = temper.exerciseDao().getAll()
            if (exercises.isEmpty()) {
                return ResetResult.Failed("The new database has no catalog.")
            }
            if (!integrityOk(temper.openHelper.readableDatabase)) {
                return ResetResult.Failed("The new database failed its integrity check.")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.w(TAG, "Foundation reset failed before cutover", error)
            return ResetResult.Failed("The new database could not be prepared.")
        } finally {
            temper.close()
        }

        preferences.resetForFoundationCutover()
        deleteDatabase(context, FoundationGeneration.LEGACY_DATABASE_FILE)
        return ResetResult.Accepted
    }

    companion object {
        private const val TAG = "PT/FoundationReset"

        fun integrityOk(db: SupportSQLiteDatabase): Boolean {
            val cursor = db.query("PRAGMA integrity_check")
            cursor.use {
                return it.moveToFirst() && it.getString(0).equals("ok", ignoreCase = true)
            }
        }
    }
}
