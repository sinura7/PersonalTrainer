package com.sinura.personaltrainer.domain

/**
 * Process-death recovery for one in-progress entry, not a shared bag.
 *
 * Workout drafts, composer drafts and live-cardio inputs each keep their own
 * store. [clear] runs on an accepted save so the next open does not greet the
 * owner with the session they just finished. A rejected or failed save leaves
 * the draft so retry still has what was typed.
 */
interface DraftStore<T> {
    fun read(): T?
    fun write(value: T)
    fun clear()
}
