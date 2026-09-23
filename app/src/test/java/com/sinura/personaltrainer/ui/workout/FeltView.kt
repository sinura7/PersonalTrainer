package com.sinura.personaltrainer.ui.workout

import android.app.Activity
import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import java.util.Collections

/**
 * The floor's view, as far as the hand and the ear go: every haptic the screen asks for and
 * every line it announces, in order, each with the screen clock's time.
 *
 * Provide it as `LocalView` around the screen under test once it is attached to the
 * activity's window ([attachTo]): a press's ripple and a dialog find their window through the
 * view's parents. The screen's haptics and its one announcement go through
 * `LocalView.current`; a real view keeps only the last haptic and no announcements at all,
 * which cannot tell one commit from two or a line said once from twice.
 */
internal class FeltView(context: Context, private val clock: () -> Long = { 0L }) : View(context) {
    /** (haptic constant, screen time in ms), in the order asked for. */
    val haptics: MutableList<Pair<Int, Long>> = Collections.synchronizedList(mutableListOf())

    val announcements: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Just the constants, in order. */
    fun felt(): List<Int> = synchronized(haptics) { haptics.map { it.first } }

    /** When each commit-weight confirmation was felt. */
    fun confirmations(): List<Long> = synchronized(haptics) {
        haptics.filter { it.first == HapticFeedbackConstants.CONFIRM }.map { it.second }
    }

    override fun performHapticFeedback(feedbackConstant: Int): Boolean {
        haptics += feedbackConstant to clock()
        return true
    }

    override fun announceForAccessibility(text: CharSequence?) {
        announcements += text?.toString().orEmpty()
    }
}

/** Hangs [this] off [activity]'s window, zero-sized, so ripples and dialogs find a parent. */
internal fun FeltView.attachTo(activity: Activity) {
    (activity.window.decorView as ViewGroup).addView(this, 0, 0)
}
