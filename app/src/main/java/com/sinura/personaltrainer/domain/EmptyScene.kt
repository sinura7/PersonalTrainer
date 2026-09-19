package com.sinura.personaltrainer.domain

/**
 * Which empty this is, so the picture can teach the next tap.
 *
 * D-04: one body still (or no picture) made every empty look the same.
 * Compact and full both draw; [teachesNextTap] scenes carry the Volt plus.
 */
enum class EmptyScene {
    /** Empty rack: add a lift, fill a session or a day. */
    RACK,

    /** Empty week: create a routine or put something on this day. */
    PLAN,

    /** Empty catalog: search miss, or no lifts in Library. */
    CATALOG,

    /** Empty log: History, a lift with no records, a session with no sets. */
    LOG,

    /** The thing this screen was about has left. Back, not create. */
    GONE,

    /** The read failed. Retry is the tap. */
    RETRY,
    ;

    val teachesNextTap: Boolean
        get() = this != GONE
}
