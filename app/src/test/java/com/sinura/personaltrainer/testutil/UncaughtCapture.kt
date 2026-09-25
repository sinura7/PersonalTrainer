package com.sinura.personaltrainer.testutil

import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [block] and returns the first exception that reached an uncaught-exception handler
 * meanwhile, on this thread or any other, or null. On a phone that exception closes the app;
 * in a JVM test it is only printed, so a test that means "does not crash" has to catch it.
 *
 * The default handler is swapped too because a coroutine resumed by Room's own threads
 * fails there, not on the test's thread.
 */
suspend fun catchingUncaught(block: suspend () -> Unit): Throwable? {
    val uncaught = AtomicReference<Throwable?>(null)
    val recorder = Thread.UncaughtExceptionHandler { _, thrown -> uncaught.compareAndSet(null, thrown) }
    val thread = Thread.currentThread()
    val previousOnThread = thread.uncaughtExceptionHandler
    val previousDefault = Thread.getDefaultUncaughtExceptionHandler()
    thread.uncaughtExceptionHandler = recorder
    Thread.setDefaultUncaughtExceptionHandler(recorder)
    try {
        block()
    } finally {
        thread.uncaughtExceptionHandler = previousOnThread
        Thread.setDefaultUncaughtExceptionHandler(previousDefault)
    }
    return uncaught.get()
}
