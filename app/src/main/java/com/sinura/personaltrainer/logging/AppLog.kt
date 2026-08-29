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

    /**
     * Local diagnostic hook. Receives the tag and throwable only — never the
     * message — so a bundle cannot copy workout names out of a log line.
     */
    @Volatile
    var onError: ((tag: String, error: Throwable) -> Unit)? = null

    /**
     * True on release builds (the app class sets it at startup): free-text
     * messages are dropped before the sink, keeping tag, level, and
     * throwable. Messages interpolate user-authored text — routine titles,
     * internal paths — and release logcat is readable by anything with adb
     * or a bugreport; the custom seam also means R8 never strips these
     * calls the way it can strip direct android.util.Log ones.
     */
    @Volatile
    var redactMessages: Boolean = false

    const val REDACTED = "(redacted)"

    fun d(tag: String, message: String) = sink(DEBUG, tag, redact(message), null)

    fun w(tag: String, message: String, error: Throwable? = null) =
        sink(WARN, tag, redact(message), error)

    fun e(tag: String, message: String, error: Throwable? = null) {
        if (error != null) {
            runCatching { onError?.invoke(tag, error) }
        }
        sink(ERROR, tag, redact(message), error)
    }

    private fun redact(message: String): String = if (redactMessages) REDACTED else message

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
