// com.google.android.gms.tasks.Task — declaration-only stub. See compile-stubs/README.md.
//
// The three listeners are `fun interface`s so the lambdas DriveAuthClient passes SAM-convert,
// and OnSuccessListener is contravariant (`in T`) exactly as the real
// addOnSuccessListener(OnSuccessListener<? super TResult>) is. onFailure takes Exception, not
// Throwable: `continuation.resumeWithException(error)` must still accept it, and widening to
// Throwable here would accept a listener the real API rejects.

package com.google.android.gms.tasks

fun interface OnSuccessListener<T> {
    fun onSuccess(result: T)
}

fun interface OnFailureListener {
    fun onFailure(e: Exception)
}

fun interface OnCanceledListener {
    fun onCanceled()
}

abstract class Task<T> internal constructor() {
    abstract fun addOnSuccessListener(listener: OnSuccessListener<in T>): Task<T>
    abstract fun addOnFailureListener(listener: OnFailureListener): Task<T>
    abstract fun addOnCanceledListener(listener: OnCanceledListener): Task<T>
}
