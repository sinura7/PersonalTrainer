package com.sinura.personaltrainer.timer

import android.content.Context

/**
 * How the running rest foreground notification should behave on the exterior.
 * Lock-screen auto glance stays on the Debug 114 path until the owner picks a
 * follow-up; this only splits in-app vs background-unlocked vs locked.
 */
enum class RestTimerRunningPresentation {
    /** Temper visible: quiet FGS card, no heads-up peek. */
    FOREGROUND_IN_APP,

    /** Home / recents / another app: shade card + draggable overlay when allowed. */
    BACKGROUND_UNLOCKED,

    /** Keyguard or screen off: Debug 114 lock glance notification path. */
    LOCKED,
    ;

    companion object {
        fun resolve(context: Context): RestTimerRunningPresentation {
            if (RestTimerLockGlance.shouldAutoPresentRunning(context)) return LOCKED
            if (RestTimerAppForeground.isInForeground) return FOREGROUND_IN_APP
            return BACKGROUND_UNLOCKED
        }
    }
}
