package com.sinura.personaltrainer.timer

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot

/**
 * Small draggable rest countdown over home / other apps. Requires
 * [Settings.canDrawOverlays]; Settings → Permissions is the fix path.
 */
object RestTimerOverlayController {
    fun canDrawOverlays(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return Settings.canDrawOverlays(context.applicationContext)
    }

    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    fun sync(
        context: Context,
        state: RestTimerSnapshot,
        presentation: RestTimerRunningPresentation,
    ) {
        if (presentation != RestTimerRunningPresentation.BACKGROUND_UNLOCKED ||
            !state.running ||
            !canDrawOverlays(context)
        ) {
            hide()
            return
        }
        show(context, state)
    }

    fun hide() {
        val wm = windowManager ?: return
        val view = pillView ?: return
        runCatching { wm.removeView(view) }
        pillView = null
        layoutParams = null
        windowManager = null
    }

    private fun show(context: Context, state: RestTimerSnapshot) {
        val appContext = context.applicationContext
        val wm = windowManager ?: appContext.getSystemService(WindowManager::class.java).also {
            windowManager = it
        }
        val view = pillView ?: LayoutInflater.from(appContext)
            .inflate(R.layout.overlay_rest_pill, null)
            .also { inflated ->
                pillView = inflated
                inflated.setOnTouchListener(dragListener)
            }
        val params = layoutParams ?: defaultLayoutParams(appContext).also { layoutParams = it }
        bindChronometer(view, state)
        if (view.parent == null) {
            wm.addView(view, params)
        } else {
            wm.updateViewLayout(view, params)
        }
    }

    private fun bindChronometer(view: View, state: RestTimerSnapshot) {
        val chrono = view.findViewById<Chronometer>(R.id.rest_overlay_chrono)
        val remaining = state.remainingSeconds(SystemClock.elapsedRealtime()).coerceAtLeast(0)
        if (RestTimer.usesLiveChronometer(remaining)) {
            chrono.isCountDown = true
            chrono.base = state.endsAtElapsedRealtime
            chrono.start()
        } else {
            chrono.stop()
            chrono.text = RestTimer.formatClock(remaining)
        }
    }

    private val dragListener = View.OnTouchListener { view, event ->
        val params = layoutParams ?: return@OnTouchListener false
        val wm = windowManager ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragOffsetX = event.rawX - params.x
                dragOffsetY = event.rawY - params.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = (event.rawX - dragOffsetX).toInt()
                params.y = (event.rawY - dragOffsetY).toInt()
                wm.updateViewLayout(view, params)
                true
            }
            else -> false
        }
    }

    private fun defaultLayoutParams(context: Context): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val dm = context.resources.displayMetrics
            x = (dm.widthPixels * 0.55f).toInt()
            y = (dm.heightPixels * 0.12f).toInt()
        }
    }
}
