package com.sinura.personaltrainer.ui.start

/** v4 blueprint 3.1: the one Start chooser's tags. */
object StartTags {
    const val SHEET = "start-sheet"
    const val START_TITLE = "start-title"
    const val START_TODAY = "start-today"
    const val START_RESUME = "start-resume"
    const val START_TODAY_REST = "start-today-rest"
    const val START_TODAY_NONE = "start-today-none"
    const val START_PLAN_WEEK = "start-plan-week"
    const val START_ALL_ROUTINES = "start-all-routines"
    const val START_NO_ROUTINES = "start-no-routines"
    const val START_EMPTY = "start-empty"
    const val START_OTHER = "start-other"
    const val START_ERROR = "start-error"
    const val START_RETRY = "start-retry"
    const val START_DISCARD = "start-discard"
    const val OTHER_BACK = "start-other-back"
    const val LOG_PAST = "start-log-past"
    const val LOG_MIXED = "start-log-mixed"
    const val LOG_CARDIO = "start-log-cardio"
    const val LIVE_CARDIO = "start-live-cardio"
    const val CARDIO_PAGE = "start-cardio-page"
    const val EXTRA = "start-extra"
    const val EXTRA_PAGE = "start-extra-page"
    const val START_PENDING = "start-pending"
    const val START_BLOCKED = "start-blocked"

    fun routine(id: String): String = "start-routine-$id"
}
