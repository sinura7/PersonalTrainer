package com.sinura.personaltrainer.domain

import kotlinx.coroutines.CancellationException

/**
 * Runs one stage of a multi-stage calculation, degrading to null if it throws.
 *
 * [TrainingInsightsCalculator] computes a heat snapshot, a set of recommendations and a week
 * plan from one history read, and each stage has to survive the other two failing: a planner
 * fault must not blank a body map that was already computed correctly. That needs a per-stage
 * try/catch, and the discipline in that catch is a domain rule, not infrastructure — which is
 * why this lives here rather than being borrowed from `util`.
 *
 * It used to be borrowed. The calculator called `util.recoverWith`, which logs through
 * `AppLog` and so reaches `android.util.Log`: `domain/` had no Android import of its own but
 * arrived there in two hops, and `util` imports `TimePort`, `CivilDate` and `IdPort` straight
 * back, so the two packages could not be compiled apart. Splitting the concern fixes both —
 * the catch stays in the domain, and the caller says what to do with what was caught.
 *
 * [CancellationException] is rethrown, never reported. `catch (_: Exception)` was the pattern
 * across this codebase and cancellation extends Exception, so a `mapLatest` that cancelled a
 * transform mid-suspend had its cancellation eaten and carried on computing with fallbacks —
 * the bug `util.runCatchingCancellable` was written for, and the one `check-cancellation.py`
 * holds at zero. Importing the type is not a platform dependency: it is a kotlinx library
 * type present on every target, unlike the `java.time` and Android APIs
 * `check-domain-seams.py` bans.
 *
 * [report] receives the stage's name and the throwable. The default drops both, which keeps
 * the degrade behaviour and loses only the log line; every production caller passes a
 * reporter, so the failure is recorded at the layer that owns logging.
 */
inline fun <T : Any> recoverStage(
    what: String,
    report: (String, Throwable) -> Unit,
    block: () -> T?,
): T? = try {
    block()
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Throwable) {
    report(what, error)
    null
}
