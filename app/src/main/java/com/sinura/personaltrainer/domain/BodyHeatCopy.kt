package com.sinura.personaltrainer.domain

/**
 * Honesty for Body chips and the silhouette wash.
 *
 * Day / Week / Month only retotal the figure. Year and all-time stay
 * History. Rest on the still is untrained in this window, not "never".
 */
object BodyHeatCopy {
    const val LEGEND_CAPTION = "Colour is muscle load in this window, not calendar sets."

    const val WINDOW_CAPTION =
        "The figure is this window. Rest is untrained here."

    const val EMPTY_LOG = "Finished sets light the figure."

    const val EMPTY_WINDOW =
        "Nothing in this window yet. Older work still shows recency."

    fun windowTitle(window: HeatWindow): String = window.label
}
