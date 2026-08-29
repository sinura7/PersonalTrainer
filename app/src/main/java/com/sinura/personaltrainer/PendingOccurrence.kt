package com.sinura.personaltrainer

import com.sinura.personaltrainer.domain.DailyAgenda
import com.sinura.personaltrainer.domain.PlannedOccurrence
import com.sinura.personaltrainer.domain.SuggestedTrainingDay
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
 */
object PendingOccurrence {
    private const val SEP = '\n'

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

    suspend fun restore(deps: AppDependencies) {
        deps.pendingOccurrenceId.value = deps.preferencesRepository.pendingOccurrenceId.first()
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
        deps.plannerRepository.markOccurrenceDone(occurrenceId, completedId)
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

    private suspend fun stored(deps: AppDependencies): String? =
        deps.pendingOccurrenceId.value
            ?: deps.preferencesRepository.pendingOccurrenceId.first()

    private fun decode(stored: String): Pair<String, String?> {
        val cut = stored.indexOf(SEP)
        return if (cut < 0) {
            stored to null
        } else {
            stored.substring(0, cut) to stored.substring(cut + 1)
        }
    }

    private suspend fun write(deps: AppDependencies, value: String?) {
        deps.pendingOccurrenceId.value = value
        deps.preferencesRepository.setPendingOccurrenceId(value)
    }
}
