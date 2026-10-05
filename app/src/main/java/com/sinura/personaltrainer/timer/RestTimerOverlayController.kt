package com.sinura.personaltrainer.timer

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Chronometer
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.logging.AppLog

/**
 * Small draggable rest countdown over home / other apps. Requires
 * [Settings.canDrawOverlays]; Settings → Permissions is the fix path.
 */
@SuppressLint("StaticFieldLeak")
object RestTimerOverlayController {
    private const val TAG = "PT/RestOverlay"
    private const val DRAG_SLOP_PX = 12

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dragMoved = false

    /** Last attach failure for Settings / diagnostics. Null when shown or not attempted. */
    @Volatile
    var lastFailureReason: String? = null
        private set

    fun canDrawOverlays(context: Context): Boolean {
        val appContext = context.applicationContext
        if (Settings.canDrawOverlays(appContext)) return true
        return appOpsAllowsOverlay(appContext)
    }

    fun sync(
        context: Context,
        state: RestTimerSnapshot,
    ) {
        val wantsOverlay = state.running &&
            !RestTimerAppForeground.isInForeground &&
            !RestTimerLockGlance.shouldAutoPresentRunning(context)
        if (!wantsOverlay) {
            detachFromWindow()
            return
        }
        if (!canDrawOverlays(context)) {
            lastFailureReason = "Display over other apps is off for Temper"
            detachFromWindow()
            return
        }
        mainHandler.post { attach(context.applicationContext, state) }
    }

    fun release() {
        mainHandler.post {
            detachFromWindow()
            pillView = null
            layoutParams = null
            windowManager = null
            lastFailureReason = null
        }
    }

    private fun detachFromWindow() {
        val wm = windowManager ?: return
        val view = pillView ?: return
        runCatching { wm.removeView(view) }
    }

    private fun attach(appContext: Context, state: RestTimerSnapshot) {
        val wm = windowManager ?: appContext.getSystemService(WindowManager::class.java).also {
            windowManager = it
        }
        @SuppressLint("InflateParams")
        val view = pillView ?: LayoutInflater.from(appContext)
            .inflate(R.layout.overlay_rest_pill, null)
            .also { inflated ->
                pillView = inflated
                inflated.setOnClickListener { view ->
                    val ctx = view.context.applicationContext
                    ctx.startActivity(
                        Intent(ctx, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        },
                    )
                }
                inflated.setOnTouchListener(dragListener)
            }
        val params = layoutParams ?: defaultLayoutParams(appContext).also { layoutParams = it }
        bindChronometer(view, state)
        try {
            if (view.parent == null) {
                wm.addView(view, params)
            } else {
                wm.updateViewLayout(view, params)
            }
            lastFailureReason = null
        } catch (thrown: Exception) {
            lastFailureReason = thrown.message ?: thrown.javaClass.simpleName
            AppLog.e(TAG, "Could not show the rest overlay", thrown)
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

    @SuppressLint("ClickableViewAccessibility")
    private val dragListener = View.OnTouchListener { view, event ->
        val params = layoutParams ?: return@OnTouchListener false
        val wm = windowManager ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMoved = false
                dragOffsetX = event.rawX - params.x
                dragOffsetY = event.rawY - params.y
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val nextX = (event.rawX - dragOffsetX).toInt()
                val nextY = (event.rawY - dragOffsetY).toInt()
                if (!dragMoved &&
                    (kotlin.math.abs(nextX - params.x) > DRAG_SLOP_PX ||
                        kotlin.math.abs(nextY - params.y) > DRAG_SLOP_PX)
                ) {
                    dragMoved = true
                }
                params.x = nextX
                params.y = nextY
                wm.updateViewLayout(view, params)
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragMoved) {
                    view.performClick()
                }
                true
            }
            else -> false
        }
    }

    private fun defaultLayoutParams(context: Context): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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

    private fun appOpsAllowsOverlay(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
