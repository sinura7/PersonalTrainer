package com.sinura.personaltrainer.domain

/**
 * Tap-order cart for multi-add.
 *
 * A [Set] can say which lifts are chosen. It cannot say which was first. The
 * routine stores that as [RoutineExercise.sortOrder], and Home and Plan read
 * the same order, so the picker has to keep it.
 */
object LiftCart {
    fun toggle(order: List<String>, id: String): List<String> =
        if (id in order) order.filter { it != id } else order + id

    fun cartNumber(order: List<String>, id: String): Int? {
        val index = order.indexOf(id)
        return if (index >= 0) index + 1 else null
    }
}
