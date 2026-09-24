package com.sinura.personaltrainer.ui.workout


/**
 * Landscape survival for the workout log and the rest floor.
 *
 * Chrome alone used to exceed a 360 dp landscape height, so the log was
 * off-screen on a phone on a bench. Compact header (one row: the plan's words as the
 * title, no progress bar) and hide idle rest. What fits on a phone on its side is
 * rendered in LandscapeChromeRenderTest.
 */
object LandscapeChrome {
    fun isLandscape(widthDp: Int, heightDp: Int): Boolean = widthDp > heightDp

    fun compactHeader(landscape: Boolean): Boolean = landscape

    fun hideIdleRest(landscape: Boolean): Boolean = landscape
}
