// androidx.work (WorkManager 2.10.1) — declaration-only stubs. See compile-stubs/README.md.
// Nothing here executes.
//
// DELIBERATELY NARROWER THAN WORKMANAGER: only the members reminder/ReminderWorker.kt and
// reminder/WorkManagerReminderScheduler.kt actually call are declared. Constraints,
// PeriodicWorkRequest, backoff policy, Operation chaining and the rest are absent; a new use
// fails here with "unresolved reference" and needs this file extended.

package androidx.work

import android.content.Context
import java.util.concurrent.TimeUnit

/** Opaque by design: the repo only ever receives one and hands it to super(). */
class WorkerParameters internal constructor()

class Data internal constructor() {
    /** Returns String? — the real API can and does return null for an absent key, and
     *  ReminderWork.run's `deliveryId` parameter is nullable precisely to accept that. */
    fun getString(key: String): String? = TODO("compile-only stub")
}

/** `vararg pairs: Pair<String, Any?>` matches the real signature. Typed as Any? rather than a
 *  union of the legal Data types because that is what WorkManager accepts; the runtime type
 *  check is WorkManager's, not the compiler's, in the real build too. */
fun workDataOf(vararg pairs: Pair<String, Any?>): Data = TODO("compile-only stub")

abstract class ListenableWorker(appContext: Context, params: WorkerParameters) {
    val applicationContext: Context get() = TODO("compile-only stub")
    val inputData: Data get() = TODO("compile-only stub")

    abstract class Result internal constructor() {
        companion object {
            @JvmStatic fun success(): Result = TODO("compile-only stub")
            @JvmStatic fun success(outputData: Data): Result = TODO("compile-only stub")
            @JvmStatic fun retry(): Result = TODO("compile-only stub")
            @JvmStatic fun failure(): Result = TODO("compile-only stub")
        }
    }
}

/** `doWork` is abstract and `suspend`: a worker that forgot either must not compile. */
abstract class CoroutineWorker(
    appContext: Context,
    params: WorkerParameters,
) : ListenableWorker(appContext, params) {
    abstract suspend fun doWork(): Result
}

abstract class WorkRequest internal constructor()

class OneTimeWorkRequest internal constructor() : WorkRequest() {
    class Builder internal constructor() {
        fun setInputData(inputData: Data): Builder = TODO("compile-only stub")
        fun setInitialDelay(duration: Long, timeUnit: TimeUnit): Builder = TODO("compile-only stub")
        fun build(): OneTimeWorkRequest = TODO("compile-only stub")
    }
}

/** reified, as the real ktx helper is, and bounded by ListenableWorker so
 *  `OneTimeWorkRequestBuilder<SomethingElse>()` does not compile. */
inline fun <reified W : ListenableWorker> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder =
    TODO("compile-only stub")

enum class ExistingWorkPolicy { REPLACE, KEEP, APPEND, APPEND_OR_REPLACE }

abstract class WorkManager internal constructor() {
    abstract fun enqueueUniqueWork(
        uniqueWorkName: String,
        existingWorkPolicy: ExistingWorkPolicy,
        request: OneTimeWorkRequest,
    ): Operation

    abstract fun cancelUniqueWork(uniqueWorkName: String): Operation

    companion object {
        @JvmStatic
        fun getInstance(context: Context): WorkManager = TODO("compile-only stub")
    }
}

/** Returned by enqueue/cancel. Declared with no members: the repo discards it, and inventing
 *  `state`/`result` here would only widen what compiles. */
interface Operation
