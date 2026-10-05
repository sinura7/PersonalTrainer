package com.sinura.personaltrainer.timer

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect as AndroidRect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.sinura.personaltrainer.MainActivity
import com.sinura.personaltrainer.R
import com.sinura.personaltrainer.domain.RestTimerSnapshot
import com.sinura.personaltrainer.logging.AppLog
import com.sinura.personaltrainer.ui.overlay.OVERLAY_VOLT_COLOR
import com.sinura.personaltrainer.ui.overlay.RestOverlayRingView
import kotlin.math.roundToInt

/**
 * Draggable, resizable rest countdown over home / other apps. Requires
 * [Settings.canDrawOverlays]; Settings → Permissions is the fix path.
 */
@SuppressLint("StaticFieldLeak")
object RestTimerOverlayController {
    private const val TAG = "PT/RestOverlay"
    private const val DRAG_SLOP_PX = 10
    private const val REFRESH_MS = 1_000L
    private const val DISMISS_ZONE_SIZE_DP = 72
    private const val DISMISS_ZONE_BOTTOM_MARGIN_DP = 128

    private val mainHandler = Handler(Looper.getMainLooper())

    private var windowManager: WindowManager? = null
    private var pillView: View? = null
    private var dismissZoneView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var dismissZoneParams: WindowManager.LayoutParams? = null
    private var dismissZoneBounds = AndroidRect()
    private var dismissZoneHot = false
    private var cardSizePx: Int = 0
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f
    private var dragMoved = false
    private var resizeStartSizePx = 0
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f
    private var lastSnapshot: RestTimerSnapshot? = null
    private var syncGeneration = 0

    /** Last attach failure for Settings / diagnostics. Null when shown or not attempted. */
    @Volatile
    var lastFailureReason: String? = null
        private set

    private val refreshRunnable = Runnable {
        val view = pillView ?: return@Runnable
        val state = lastSnapshot ?: return@Runnable
        if (!state.running || !view.isAttachedToWindow) return@Runnable
        bindContent(view, state, updateScale = false)
        scheduleRefresh()
    }

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
        val generation = ++syncGeneration
        if (!wantsOverlay) {
            lastSnapshot = null
            mainHandler.post { detachFromWindow(generation) }
            return
        }
        if (!canDrawOverlays(context)) {
            lastFailureReason = "Display over other apps is off for Temper"
            lastSnapshot = null
            mainHandler.post { detachFromWindow(generation) }
            return
        }
        if (RestOverlayDismissStore.isDismissedForRest(context, state.timerId)) {
            lastSnapshot = state
            mainHandler.post { detachFromWindow(generation) }
            return
        }
        lastSnapshot = state
        mainHandler.post { attach(context.applicationContext, state, generation) }
    }

    fun release() {
        ++syncGeneration
        mainHandler.post {
            val appContext = pillView?.context?.applicationContext
            stopRefresh()
            hideDismissZone()
            detachFromWindow(syncGeneration)
            pillView = null
            layoutParams = null
            dismissZoneView = null
            dismissZoneParams = null
            windowManager = null
            lastSnapshot = null
            cardSizePx = 0
            lastFailureReason = null
            appContext?.let { RestOverlayDismissStore.clear(it) }
        }
    }

    private fun stopRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
    }

    private fun scheduleRefresh() {
        mainHandler.removeCallbacks(refreshRunnable)
        mainHandler.postDelayed(refreshRunnable, REFRESH_MS)
    }

    private fun detachFromWindow(expectedGeneration: Int) {
        if (expectedGeneration != syncGeneration) return
        stopRefresh()
        hideDismissZone()
        val wm = windowManager ?: return
        val view = pillView ?: return
        if (view.isAttachedToWindow) {
            runCatching { wm.removeView(view) }
                .onFailure { AppLog.w(TAG, "Overlay remove failed", it) }
        }
    }

    private fun attach(appContext: Context, state: RestTimerSnapshot, expectedGeneration: Int) {
        if (expectedGeneration != syncGeneration) return
        if (RestOverlayDismissStore.isDismissedForRest(appContext, state.timerId)) {
            detachFromWindow(expectedGeneration)
            return
        }
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
        cardSizePx = saved.sizeDp.dpToPx(appContext)
        applyCardSize(view, cardSizePx)
        val params = layoutParams ?: defaultLayoutParams(appContext, saved, cardSizePx).also {
            layoutParams = it
        }
        params.width = cardSizePx
        params.height = cardSizePx
        try {
            if (!view.isAttachedToWindow) {
                wm.addView(view, params)
            } else {
                safeUpdateViewLayout(wm, view, params)
            }
            if (expectedGeneration != syncGeneration) {
                detachFromWindow(syncGeneration)
                return
            }
            bindContent(view, state, updateScale = true)
            lastFailureReason = null
            scheduleRefresh()
        } catch (thrown: Exception) {
            lastFailureReason = thrown.message ?: thrown.javaClass.simpleName
            AppLog.e(TAG, "Could not show the rest overlay", thrown)
            runCatching {
                if (view.isAttachedToWindow) wm.removeView(view)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun wireCard(appContext: Context, root: View) {
        val card = root.findViewById<FrameLayout>(R.id.rest_overlay_card)
        val handle = root.findViewById<View>(R.id.rest_overlay_resize_handle)
        card.setOnClickListener { _ ->
            val snap = lastSnapshot
            appContext.startActivity(
                Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    snap?.sessionId?.let { putExtra(RestTimerService.EXTRA_SESSION_ID, it) }
                },
            )
        }
        card.setOnTouchListener(cardDragListener)
        handle.setOnTouchListener(resizeListener)
    }

    private fun bindContent(
        view: View,
        state: RestTimerSnapshot,
        updateScale: Boolean,
    ) {
        val ring = view.findViewById<RestOverlayRingView>(R.id.rest_overlay_ring)
        val time = view.findViewById<TextView>(R.id.rest_overlay_time)
        val kicker = view.findViewById<TextView>(R.id.rest_overlay_kicker)
        val appContext = view.context.applicationContext
        val model = RestExteriorDisplay.model(
            state = state,
            nowElapsedRealtime = SystemClock.elapsedRealtime(),
            kickerRunning = appContext.getString(R.string.lockscreen_rest_widget_kicker),
            kickerDone = appContext.getString(R.string.rest_exterior_done_kicker),
        )
        ring.progress = model.progressLevel.toFloat() / RestExteriorDisplay.PROGRESS_MAX
        ring.emphasizeDone = model.atZero
        kicker.text = model.kicker
        time.text = model.timeText
        if (updateScale) scaleTypeForCard(view)
    }

    private fun scaleTypeForCard(view: View) {
        val chrono = view.findViewById<TextView>(R.id.rest_overlay_time)
        val kicker = view.findViewById<TextView>(R.id.rest_overlay_kicker)
        val sizePx = cardSizePx.coerceAtLeast(1)
        val sizeDp = (sizePx / view.resources.displayMetrics.density).roundToInt()
        val scale = (sizeDp.toFloat() / RestOverlayLayoutStore.DEFAULT_SIZE_DP).coerceIn(0.75f, 1.45f)
        chrono.textSize = 34f * scale
        kicker.textSize = 11f * scale
        kicker.setTextColor(OVERLAY_VOLT_COLOR)
    }

    private fun applyCardSize(root: View, sizePx: Int) {
        cardSizePx = sizePx
        val card = root.findViewById<FrameLayout>(R.id.rest_overlay_card)
        val lp = card.layoutParams ?: FrameLayout.LayoutParams(sizePx, sizePx)
        lp.width = sizePx
        lp.height = sizePx
        card.layoutParams = lp
    }

    private fun safeUpdateViewLayout(
        wm: WindowManager,
        view: View,
        params: WindowManager.LayoutParams,
    ) {
        if (!view.isAttachedToWindow) return
        runCatching { wm.updateViewLayout(view, params) }
            .onFailure { AppLog.w(TAG, "Overlay layout update failed", it) }
    }

    private fun showDismissZone(appContext: Context) {
        val wm = windowManager ?: return
        if (dismissZoneView?.isAttachedToWindow == true) {
            updateDismissZoneBounds()
            return
        }
        @SuppressLint("InflateParams")
        val zone = dismissZoneView ?: LayoutInflater.from(appContext)
            .inflate(R.layout.overlay_dismiss_zone, null)
            .also { dismissZoneView = it }
        val sizePx = DISMISS_ZONE_SIZE_DP.dpToPx(appContext)
        val params = dismissZoneParams ?: WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = DISMISS_ZONE_BOTTOM_MARGIN_DP.dpToPx(appContext)
        }.also { dismissZoneParams = it }
        params.width = sizePx
        params.height = sizePx
        runCatching { wm.addView(zone, params) }
            .onFailure { AppLog.w(TAG, "Dismiss zone add failed", it) }
        zone.post { updateDismissZoneBounds() }
    }

    private fun hideDismissZone() {
        dismissZoneHot = false
        val wm = windowManager ?: return
        val zone = dismissZoneView ?: return
        if (zone.isAttachedToWindow) {
            runCatching { wm.removeView(zone) }
        }
    }

    private fun updateDismissZoneBounds() {
        val zone = dismissZoneView ?: return
        if (!zone.isAttachedToWindow) return
        val loc = IntArray(2)
        zone.getLocationOnScreen(loc)
        dismissZoneBounds.set(
            loc[0],
            loc[1],
            loc[0] + zone.width,
            loc[1] + zone.height,
        )
        zone.alpha = if (dismissZoneHot) 1f else 0.85f
    }

    private fun updateDismissHotspot(rawX: Float, rawY: Float) {
        val target = AndroidRect(dismissZoneBounds)
        target.inset(-32, -32)
        val hot = target.contains(rawX.toInt(), rawY.toInt())
        if (hot != dismissZoneHot) {
            dismissZoneHot = hot
            dismissZoneView?.alpha = if (hot) 1f else 0.85f
        }
    }

    private fun pillAnchorOnScreen(params: WindowManager.LayoutParams): Pair<Float, Float> {
        val half = cardSizePx / 2f
        return (params.x + half) to (params.y + half)
    }

    private fun dismissOverlayForCurrentRest(appContext: Context) {
        val timerId = lastSnapshot?.timerId ?: return
        RestOverlayDismissStore.dismissForRest(appContext, timerId)
        ++syncGeneration
        detachFromWindow(syncGeneration)
    }

    @SuppressLint("ClickableViewAccessibility")
    private val cardDragListener = View.OnTouchListener { view, event ->
        val params = layoutParams ?: return@OnTouchListener false
        val wm = windowManager ?: return@OnTouchListener false
        val overlay = pillView ?: return@OnTouchListener false
        val appContext = view.context.applicationContext
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragMoved = false
                dismissZoneHot = false
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
                    showDismissZone(appContext)
                }
                params.x = nextX
                params.y = nextY
                safeUpdateViewLayout(wm, overlay, params)
                if (dragMoved) {
                    updateDismissZoneBounds()
                    val anchor = pillAnchorOnScreen(params)
                    updateDismissHotspot(anchor.first, anchor.second)
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (dragMoved) {
                    val anchor = pillAnchorOnScreen(params)
                    updateDismissHotspot(anchor.first, anchor.second)
                }
                if (dragMoved && dismissZoneHot) {
                    dismissOverlayForCurrentRest(appContext)
                } else if (!dragMoved) {
                    view.performClick()
                } else {
                    RestOverlayLayoutStore.savePosition(appContext, params.x, params.y)
                }
                hideDismissZone()
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                hideDismissZone()
                true
            }
            else -> false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val resizeListener = View.OnTouchListener { handle, event ->
        val root = pillView ?: return@OnTouchListener false
        val wm = windowManager ?: return@OnTouchListener false
        val params = layoutParams ?: return@OnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                resizeStartSizePx = cardSizePx
                resizeStartRawX = event.rawX
                resizeStartRawY = event.rawY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val delta = ((event.rawX - resizeStartRawX) + (event.rawY - resizeStartRawY)) / 2f
                val minPx = RestOverlayLayoutStore.MIN_SIZE_DP.dpToPx(handle.context)
                val maxPx = RestOverlayLayoutStore.MAX_SIZE_DP.dpToPx(handle.context)
                val nextPx = (resizeStartSizePx + delta).toInt().coerceIn(minPx, maxPx)
                applyCardSize(root, nextPx)
                params.width = nextPx
                params.height = nextPx
                safeUpdateViewLayout(wm, root, params)
                lastSnapshot?.let { bindContent(root, it, updateScale = true) }
                true
            }
            MotionEvent.ACTION_UP -> {
                val sizeDp = (cardSizePx / handle.resources.displayMetrics.density).roundToInt()
                RestOverlayLayoutStore.saveSize(handle.context.applicationContext, sizeDp)
                true
            }
            else -> false
        }
    }

    private fun defaultLayoutParams(
        context: Context,
        saved: RestOverlayLayoutStore.SavedLayout,
        sizePx: Int,
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
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
