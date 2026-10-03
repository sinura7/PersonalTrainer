package com.sinura.personaltrainer.timer

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/**
 * When to auto-present [RestLockActivity] for a **running** rest (full-screen
 * intent on the foreground notification). Unlocked floating rest on the live
 * workout is a planned follow-up — stock Android does not allow a draggable
 * app overlay on the lock screen.
 */
object RestTimerLockGlance {
    fun shouldAutoPresentRunning(context: Context): Boolean {
        val appContext = context.applicationContext
        val keyguard = appContext.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isKeyguardLocked == true) return true
        val power = appContext.getSystemService(PowerManager::class.java)
        if (power != null && !power.isInteractive) return true
        return false
    }
}
