package com.sinura.personaltrainer.data.repository

import androidx.room.withTransaction
import com.sinura.personaltrainer.data.local.TrainerDatabase
import com.sinura.personaltrainer.data.local.entity.ExerciseEntity
import com.sinura.personaltrainer.data.local.entity.ExerciseMuscleEntity
import com.sinura.personaltrainer.data.local.entity.SeedMetaEntity
import com.sinura.personaltrainer.domain.DefaultExercises
import com.sinura.personaltrainer.domain.MuscleCredit
import com.sinura.personaltrainer.domain.MuscleNormalizer
import com.sinura.personaltrainer.logging.AppLog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * A name a built-in and a custom exercise both answer to.
 *
 * Recorded, never resolved. The custom exercise is the user's, and their history points at its
 * id by foreign key — renaming it, merging it, or deleting it would either lie about what they
 * called their lift or orphan the sets they logged against it. So both rows exist, the pair is
 * flagged, and a later phase gives the owner a surface to decide. Doing nothing is the correct
 * behaviour here, but doing nothing SILENTLY is not, which is what the flag is for.
 */
data class CatalogCollision(
    val builtInId: String,
    val customId: String,
    val nameKey: String,
)

/**
 * Everything that rewrites the database wholesale, behind one lock.
 *
 * Two things in this app can rewrite the catalog: the startup seed pass, launched
 * fire-and-forget from `Application.onCreate`, and a backup restore, which deletes every table
 * and repopulates it. Before this class they could overlap — a restore's delete pass and a
 * seed's upsert running against the same tables, in whatever order the dispatcher chose — and
 * the outcome ranged from a duplicated catalog to a half-restored one.
 *
 * The mutex makes them queue. The version check lives INSIDE the lock for the same reason: a
 * check-then-act outside it can read "catalog is current", lose the race to a restore that
 * resets the version to 0, and then skip the reconciliation that restore was depending on.
 */
class DbMaintenance(private val database: TrainerDatabase) {
    private val mutex = Mutex()

    /** Run [block] with exclusive access to the catalog. Restore holds this across its wipe. */
    suspend fun <T> withMaintenanceLock(block: suspend () -> T): T = mutex.withLock { block() }

    /**
     * Idempotent versioned seed. Safe to run on every process start; a no-op once the stored
     * catalog version has caught up.
     */
    suspend fun seedCatalog() {
        mutex.withLock {
            val stored = database.catalogDao().getSeedMeta()?.catalogVersion ?: 0
            if (stored >= DefaultExercises.CATALOG_VERSION) return@withLock
            reconcileCatalogLocked()
        }
    }

    /**
     * Bring the catalog in line with what this build knows, whatever state it is in.
     *
     * Called directly as a restore epilogue, where the caller already holds the lock — every
     * restore ends here, including a v1 document, because a restored database carries whatever
     * catalog the backup was taken from and `seed_meta` is never trusted across a restore.
     *
     * It never deletes an exercise and never touches an id. The strongest thing it does to a
     * custom exercise is give it junction credits derived from the muscle group the user
     * already chose, and rewrite its nameKey — both of which are recomputations of data the
     * user's own input implies, not edits to it.
     */
    suspend fun reconcileCatalogLocked() {
        database.withTransaction {
            val exerciseDao = database.exerciseDao()
            val catalogDao = database.catalogDao()

            val existing = exerciseDao.getAll().associateBy { it.id }
            val builtIns = DefaultExercises.catalog()

            builtIns.forEach { seed ->
                val nameKey = MuscleNormalizer.nameKeyOf(seed.name)
                val current = existing[seed.id]
                val row = current?.copy(
                    name = seed.name,
                    muscleGroup = seed.muscleGroup,
                    equipment = seed.equipment.name,
                    loadType = seed.loadType.name,
                    movementKey = seed.movementKey,
                    nameKey = nameKey,
                    // notes, isCustom and imageKey are deliberately preserved: notes may hold
                    // the owner's own cues, and imageKey is written by a later phase.
                ) ?: ExerciseEntity(
                    id = seed.id,
                    name = seed.name,
                    muscleGroup = seed.muscleGroup,
                    notes = "",
                    isCustom = false,
                    equipment = seed.equipment.name,
                    loadType = seed.loadType.name,
                    movementKey = seed.movementKey,
                    imageKey = null,
                    nameKey = nameKey,
                )
                // Update-or-insert, deliberately NOT INSERT OR REPLACE: `exercises` is the
                // parent of session_exercises and set_logs under ON DELETE RESTRICT, and
                // REPLACE resolves a conflict by DELETING the existing row first — which would
                // either fail the constraint or, worse, take history with it.
                if (current == null) exerciseDao.insert(row) else exerciseDao.update(row)
                catalogDao.replaceCreditsFor(seed.id, seed.credits.toRows(seed.id))
            }

            // Everything that is not a built-in: rewrite the nameKey through the one function,
            // and give it credits if it has none. An exercise that already has credits is left
            // exactly as it is — those came from a backup or from a later phase's editor.
            val builtInIds = builtIns.map { it.id }.toSet()
            val withCredits = catalogDao.exerciseIdsWithCredits().toSet()
            exerciseDao.getAll()
                .filter { it.id !in builtInIds }
                .forEach { custom ->
                    val nameKey = MuscleNormalizer.nameKeyOf(custom.name)
                    if (custom.nameKey != nameKey) {
                        exerciseDao.update(custom.copy(nameKey = nameKey))
                    }
                    if (custom.id !in withCredits) {
                        val derived = MuscleNormalizer.deriveCredits(custom.muscleGroup)
                        catalogDao.replaceCreditsFor(custom.id, derived.toRows(custom.id))
                    }
                }

            val collisions = detectCollisions(exerciseDao.getAll(), builtInIds)
            catalogDao.upsertSeedMeta(
                SeedMetaEntity(
                    id = 1,
                    catalogVersion = DefaultExercises.CATALOG_VERSION,
                    pendingCollisions = collisions.toJson(),
                ),
            )
            if (collisions.isNotEmpty()) {
                AppLog.w(TAG, "Catalog name collisions flagged: ${collisions.size}")
            }
        }
    }

    private fun detectCollisions(
        all: List<ExerciseEntity>,
        builtInIds: Set<String>,
    ): List<CatalogCollision> {
        val byKey = all.groupBy { it.nameKey }
        return byKey.flatMap { (key, rows) ->
            if (key.isBlank() || rows.size < 2) return@flatMap emptyList()
            val builtIn = rows.firstOrNull { it.id in builtInIds } ?: return@flatMap emptyList()
            rows.filter { it.id !in builtInIds }
                .map { custom ->
                    CatalogCollision(builtInId = builtIn.id, customId = custom.id, nameKey = key)
                }
        }
    }

    private companion object {
        const val TAG = "PT/DbMaintenance"
    }
}

private fun List<MuscleCredit>.toRows(exerciseId: String): List<ExerciseMuscleEntity> = map {
    ExerciseMuscleEntity(exerciseId = exerciseId, muscleKey = it.muscleKey, weight = it.weight)
}

/**
 * Hand-rolled rather than Gson: this string is written from a Room transaction on the IO
 * dispatcher and read by a later phase's UI, and the shape is three strings — a serializer
 * dependency here would only be one more thing that can be configured wrong.
 */
internal fun List<CatalogCollision>.toJson(): String {
    val array = JSONArray()
    forEach { collision ->
        array.put(
            JSONObject()
                .put("builtInId", collision.builtInId)
                .put("customId", collision.customId)
                .put("nameKey", collision.nameKey),
        )
    }
    return array.toString()
}

internal fun parseCollisions(raw: String?): List<CatalogCollision> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            CatalogCollision(
                builtInId = item.optString("builtInId"),
                customId = item.optString("customId"),
                nameKey = item.optString("nameKey"),
            )
        }
    } catch (error: Exception) {
        AppLog.w("PT/DbMaintenance", "Unreadable pendingCollisions payload", error)
        emptyList()
    }
}
