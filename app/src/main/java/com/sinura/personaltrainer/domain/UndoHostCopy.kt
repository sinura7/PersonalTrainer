package com.sinura.personaltrainer.domain

/**
 * Packet 5: one undo host, one labelled action, ~6s dwell
 * ([com.sinura.personaltrainer.ui.theme.Motion.STATUS_DWELL_MS]).
 *
 * Cheap destructives do the work first and offer this reversal. Finish,
 * discard, and leaving a live workout still ask first.
 */
object UndoHostCopy {
    const val ACTION = "Undo"

    fun setDeleted(line: String): String = "Set deleted · $line"

    fun liftRemoved(name: String): String = "Lift removed · $name"

    fun daySkipped(title: String): String = "Skipped · $title"
}
