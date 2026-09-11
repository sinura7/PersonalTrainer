package com.sinura.personaltrainer.domain

/**
 * First rest in-app (T-16). Samsung (and other OEM) battery savers kill the
 * clock unless the app is Unrestricted. SETUP already documents the path;
 * this is the one mention on the gym floor, not a Settings essay.
 */
object RestBatteryCopy {
    const val SENTENCE = "Allow unrestricted battery or the clock dies."
    const val GOT_IT = "Got it"
}
