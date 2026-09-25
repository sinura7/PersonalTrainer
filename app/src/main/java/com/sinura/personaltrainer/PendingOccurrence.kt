package com.sinura.personaltrainer

import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.PlannedOccurrence
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.util.runCatchingCancellable
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * The in-progress planned occurrence, if any.
 *
 * Cardio and mixed sessions write `occurrenceId` on the activity row.
 * Strength still goes through the legacy workout logger, so the binding
 * lives here and is persisted in DataStore. Process death must not drop
 * it: finishing that workout still marks the occurrence DONE.
 *
 * A binding is armed only AFTER a start comes back Open, and it
 * remembers which session it was armed for. Arming before the outcome
 * let a Blocked start leave the intent live, and completing without a
 * session check let whatever finished next — Tuesday's free workout, a
 * repeated session — mark Wednesday's plan row DONE against the wrong
 * id. The composer arm (a mixed session with no session id yet) is
 * consumed by the composer itself via [takeForComposer], so an
 * abandoned composer cannot leak it into an unrelated later save.
 *
 * Storage: the flow and preference hold `occurrenceId` alone (a
 * composer arm, or a row persisted before sessions were recorded) or
 * `occurrenceId\nsessionId` once a session is attached. This object is
 * the only reader of that encoding.
 *
 * No write here throws. Most follow something already done (a start
 * that opened, a finish or a discard that landed) and the rest hand the
 * link to the composer; a failed one is logged and the action stands. It
 * used to throw past the finish that had just been saved, closing the app
 * from the bottom bar with the summary never opened (audit UI-12). Once
 * this run has written the link, its in-memory copy is the truth: a clear
 * whose saved copy failed is not read back from the file.
 */
object PendingOccurrence {
    private const val SEP = '\n'
    private const val TAG = "PT/PendingOccurrence"

    /** Arms the composer: the next composer save follows [occurrenceId]. */
    suspend fun bind(deps: AppDependencies, occurrenceId: String?) {
        write(deps, occurrenceId)
    }

    /** [occurrenceId] is being followed by the live session [sessionId]. */
    suspend fun bindForSession(deps: AppDependencies, occurrenceId: String, sessionId: String) {
        write(deps, "$occurrenceId$SEP$sessionId")
    }

    /**
     * The dated strength occurrence [day] is following, or null when this
     * start is not a planned session (free workout, leftover slot with no
     * occurrence, rest). A lookup only — call [bindForSession] once the
     * start comes back Open.
     */
    suspend fun plannedOccurrenceId(deps: AppDependencies, day: SuggestedTrainingDay): String? {
        val occurrences = deps.plannerRepository.occurrencesBetween(day.epochDay, day.epochDay)
        val items = DailyAgenda.forDay(day.epochDay, occurrences, deps.plannerRepository.rules())
        return PlannedOccurrence.matching(day, items)?.occurrence?.id
    }

    /**
     * Loads the saved link at startup, unless this run has already written one. Restore runs
     * late, on another thread; a start or a finish that got in first is newer than the file,
     * and the file may hold a clear that failed to save.
     */
    suspend fun restore(deps: AppDependencies) {
        val saved = deps.preferencesRepository.pendingOccurrenceId.first()
        synchronized(written) {
            if (deps.pendingOccurrenceId !in written) deps.pendingOccurrenceId.value = saved
        }
    }

    /**
     * The finished session [completedId] marks its bound occurrence DONE.
     * A binding armed for a different session stays put — that session is
     * not the plan being followed — as does a composer arm, which carries
     * no session. A binding persisted before sessions were recorded has
     * no session to compare and completes as before.
     */
    suspend fun complete(deps: AppDependencies, completedId: String) {
        val stored = stored(deps) ?: return
        val (occurrenceId, sessionId) = decode(stored)
        if (sessionId != null && sessionId != completedId) return
        // A plan row that could not be marked keeps its binding: nothing is lost by it, and
        // the next start replaces it.
        runCatchingCancellable { deps.plannerRepository.markOccurrenceDone(occurrenceId, completedId) }
            .onFailure { thrown ->
                AppLog.e(TAG, "Marking the planned session done failed", thrown)
                return
            }
        write(deps, null)
    }

    suspend fun forget(deps: AppDependencies) {
        write(deps, null)
    }

    /**
     * Clears the binding only when it is armed for [sessionId] (or is an
     * untagged legacy row). Discarding one session must not clear another
     * session's binding, nor a composer arm.
     */
    suspend fun forgetIfSession(deps: AppDependencies, sessionId: String) {
        val stored = stored(deps) ?: return
        val (_, bound) = decode(stored)
        if (bound == null || bound == sessionId) write(deps, null)
    }

    /**
     * Transfers the composer arm to the caller: returns the occurrence id
     * and clears the store. A binding already attached to a session is
     * not the composer's and is left alone.
     */
    suspend fun takeForComposer(deps: AppDependencies): String? {
        val stored = stored(deps) ?: return null
        val (occurrenceId, sessionId) = decode(stored)
        if (sessionId != null) return null
        write(deps, null)
        return occurrenceId
    }

    /**
     * The link flows this run has written. Until one is written, an empty in-memory value may
     * only mean [restore] has not run yet, so the file is read; after it, empty means none.
     * Reading the file then would bring back a clear that failed to save: the composer arm
     * taken twice, or a later, unrelated finish marking that planned day done.
     */
    private val written: MutableSet<MutableStateFlow<String?>> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    private suspend fun stored(deps: AppDependencies): String? {
        val link = deps.pendingOccurrenceId
        val (inMemory, known) = synchronized(written) { link.value to (link in written) }
        if (inMemory != null) return inMemory
        if (known) return null
        return deps.preferencesRepository.pendingOccurrenceId.first()
    }

    private fun decode(stored: String): Pair<String, String?> {
        val cut = stored.indexOf(SEP)
        return if (cut < 0) {
            stored to null
        } else {
            stored.substring(0, cut) to stored.substring(cut + 1)
        }
    }

    /**
     * The in-memory value is set first and is what this run reads from then on, so it follows
     * the link even when the saved copy cannot be written; only a restart would find the older
     * one.
     */
    private suspend fun write(deps: AppDependencies, value: String?) {
        synchronized(written) {
            deps.pendingOccurrenceId.value = value
            written += deps.pendingOccurrenceId
        }
        runCatchingCancellable { deps.preferencesRepository.setPendingOccurrenceId(value) }
            .onFailure { thrown -> AppLog.e(TAG, "Saving the planned-session link failed", thrown) }
    }
}
