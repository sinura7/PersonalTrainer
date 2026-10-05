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
import android.widget.FrameLayout
import android.widget.TextView
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimer
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.overlay.RestOverlayRingView
import com.sinura.personaltrainer.ui.theme.Volt
import kotlin.math.roundToInt

/**
 * Draggable, resizable rest countdown over home / other apps. Requires
 * [Settings.canDrawOverlays]; Settings → Permissions is the fix path.
 */
@SuppressLint("StaticFieldLeak")
object RestTimerOverlayController {
    private const val TAG = "PT/RestOverlay"
    private const val DRAG_SLOP_PX = 10

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dragMoved = false
    private var resizeStartSizePx = 0
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f
    private var lastSnapshot: RestTimerSnapshot? = null

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
        lastSnapshot = state
        mainHandler.post { attach(context.applicationContext, state) }
    }

    fun release() {
        mainHandler.post {
            detachFromWindow()
            pillView = null
            layoutParams = null
            windowManager = null
            lastSnapshot = null
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
                wireCard(appContext, inflated)
            }
        val saved = RestOverlayLayoutStore.load(appContext)
        applyCardSize(view, saved.sizeDp.dpToPx(appContext))
        val params = layoutParams ?: defaultLayoutParams(appContext, saved).also { layoutParams = it }
        bindContent(view, state)
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

    @SuppressLint("ClickableViewAccessibility")
    private fun wireCard(appContext: Context, root: View) {
        val card = root.findViewById<FrameLayout>(R.id.rest_overlay_card)
        val handle = root.findViewById<View>(R.id.rest_overlay_resize_handle)
        card.setOnClickListener { _ ->
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
        }
        card.setOnTouchListener(cardDragListener)
        handle.setOnTouchListener(resizeListener)
    }

    private fun bindContent(view: View, state: RestTimerSnapshot) {
        val ring = view.findViewById<RestOverlayRingView>(R.id.rest_overlay_ring)
        val chrono = view.findViewById<Chronometer>(R.id.rest_overlay_chrono)
        val remaining = state.remainingSeconds(SystemClock.elapsedRealtime()).coerceAtLeast(0)
        val total = state.totalSeconds.coerceAtLeast(1)
        ring.progress = remaining.toFloat() / total.toFloat()
        if (RestTimer.usesLiveChronometer(remaining)) {
            chrono.isCountDown = true
            chrono.base = state.endsAtElapsedRealtime
            chrono.start()
        } else {
            chrono.stop()
            chrono.text = RestTimer.formatClock(remaining)
        }
        scaleTypeForCard(view)
    }

    private fun scaleTypeForCard(view: View) {
        val card = view.findViewById<FrameLayout>(R.id.rest_overlay_card)
        val chrono = view.findViewById<Chronometer>(R.id.rest_overlay_chrono)
        val kicker = view.findViewById<TextView>(R.id.rest_overlay_kicker)
        val sizeDp = (card.layoutParams.width / view.resources.displayMetrics.density).toInt()
        val scale = (sizeDp.toFloat() / RestOverlayLayoutStore.DEFAULT_SIZE_DP).coerceIn(0.75f, 1.45f)
        chrono.textSize = 34f * scale
        kicker.textSize = 11f * scale
        kicker.setTextColor(
            android.graphics.Color.argb(
                255,
                (Volt.red * 255).toInt(),
                (Volt.green * 255).toInt(),
                (Volt.blue * 255).toInt(),
            ),
        )
    }

    private fun applyCardSize(root: View, sizePx: Int) {
        val card = root.findViewById<FrameLayout>(R.id.rest_overlay_card)
        val lp = card.layoutParams
        lp.width = sizePx
        lp.height = sizePx
        card.layoutParams = lp
        scaleTypeForCard(root)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val cardDragListener = View.OnTouchListener { view, event ->
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
                wm.updateViewLayout(view.rootView, params)
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragMoved) {
                    view.performClick()
                } else {
                    RestOverlayLayoutStore.savePosition(
                        view.context.applicationContext,
                        params.x,
                        params.y,
                    )
                }
                true
            }
            else -> false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val resizeListener = View.OnTouchListener { handle, event ->
        val root = pillView ?: return@OnTouchListener false
        val card = root.findViewById<FrameLayout>(R.id.rest_overlay_card)
        val wm = windowManager ?: return@OnTouchListener false
        val params = layoutParams ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartSizePx = card.layoutParams.width
                resizeStartRawX = event.rawX
                resizeStartRawY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = ((event.rawX - resizeStartRawX) + (event.rawY - resizeStartRawY)) / 2f
                val density = handle.resources.displayMetrics.density
                val minPx = RestOverlayLayoutStore.MIN_SIZE_DP.dpToPx(handle.context)
                val maxPx = RestOverlayLayoutStore.MAX_SIZE_DP.dpToPx(handle.context)
                val nextPx = (resizeStartSizePx + delta).toInt().coerceIn(minPx, maxPx)
                applyCardSize(root, nextPx)
                wm.updateViewLayout(root, params)
                lastSnapshot?.let { bindContent(root, it) }
                true
            }
            MotionEvent.ACTION_UP -> {
                val sizeDp = (card.layoutParams.width / handle.resources.displayMetrics.density).roundToInt()
                RestOverlayLayoutStore.saveSize(handle.context.applicationContext, sizeDp)
                true
            }
            else -> false
        }
    }

    private fun defaultLayoutParams(
        context: Context,
        saved: RestOverlayLayoutStore.SavedLayout,
    ): WindowManager.LayoutParams {
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
            x = if (saved.x != Int.MIN_VALUE) saved.x else (dm.widthPixels * 0.52f).toInt()
            y = if (saved.y != Int.MIN_VALUE) saved.y else (dm.heightPixels * 0.14f).toInt()
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
