package com.sinura.personaltrainer.logging

/**
 * The app's logging seam.
 *
 * Before this existed there was not a single log statement in the entire codebase, so a
 * failure on the owner's own phone left no trace anywhere — every catch block mapped the
 * throwable to a user-facing string and dropped it.
 *
 * [sink] is swappable so JVM tests do not touch `android.util.Log` (which throws
 * "not mocked" off-device). The default sink falls back to stdout if the Android runtime is
 * absent, so tests work without any setup.
 *
 * Tag policy: `PT/<Component>`, one `private const val TAG` per file.
 * Levels: [d] flow breadcrumbs · [w] degraded-to-fallback recoveries · [e] user-visible
 * failures and background faults.
 */
object AppLog {
    const val DEBUG = 3
    const val WARN = 5
    const val ERROR = 6

    @Volatile
    var sink: (priority: Int, tag: String, message: String, error: Throwable?) -> Unit =
        ::androidSink

    fun d(tag: String, message: String) = sink(DEBUG, tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = sink(WARN, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) = sink(ERROR, tag, message, error)

    private fun androidSink(priority: Int, tag: String, message: String, error: Throwable?) {
        try {
            android.util.Log.println(
                priority,
                tag,
                if (error == null) message else message + '\n' + android.util.Log.getStackTraceString(error),
            )
        } catch (_: Throwable) {
            // No Android runtime (plain JVM test). Never let logging break the caller.
            println("[$priority] $tag: $message")
            error?.printStackTrace()
        }
    }
}
