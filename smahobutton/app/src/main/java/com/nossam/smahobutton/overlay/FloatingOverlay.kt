package com.nossam.smahobutton.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.nossam.smahobutton.dp
import com.nossam.smahobutton.logic.ActionSpec
import com.nossam.smahobutton.logic.ButtonGestureMachine
import com.nossam.smahobutton.logic.ButtonSpec
import com.nossam.smahobutton.logic.GESTURE_DOUBLE
import com.nossam.smahobutton.logic.GESTURE_LONG
import com.nossam.smahobutton.logic.GESTURE_TAP
import com.nossam.smahobutton.logic.GestureSignal
import com.nossam.smahobutton.logic.ScreenEdge
import com.nossam.smahobutton.logic.TYPE_TALK
import com.nossam.smahobutton.logic.actionIcon
import com.nossam.smahobutton.logic.actionShort
import com.nossam.smahobutton.logic.actionTitle
import com.nossam.smahobutton.logic.holdsToTalk
import com.nossam.smahobutton.logic.PLACE_FREE
import com.nossam.smahobutton.logic.centerFraction
import com.nossam.smahobutton.logic.clampFraction
import com.nossam.smahobutton.logic.placeButtons
import com.nossam.smahobutton.logic.placeMenuItems
import com.nossam.smahobutton.logic.sizeDp
import com.nossam.smahobutton.logic.snapEdge
import kotlin.math.roundToInt

class FloatingOverlay(
    private val context: Context,
    private val windowType: Int,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onGesture(button: ButtonSpec, kind: String)
        fun onMoved(id: String, edge: String, xFraction: Float, yFraction: Float)
        fun onMenuAction(action: ActionSpec)
        fun onTalkDown(): Boolean
        fun onTalkUp()
    }

    fun hostContext(): Context = context

    private val wm = context.getSystemService(WindowManager::class.java)
    private val views = linkedMapOf<String, Holder>()
    private var specs: List<ButtonSpec> = emptyList()
    private var hidden = false
    private var folded = false
    private var menuRoot: View? = null
    private var menuTalking = false

    fun render(buttons: List<ButtonSpec>) {
        specs = buttons
        hidden = false
        if (wm == null) return
        val alive = buttons.map { it.id }.toSet()
        views.keys.filter { it !in alive }.toList().forEach { id ->
            views.remove(id)?.let { detach(it) }
        }
        val bounds = screenBounds()
        val margin = context.dp(8)
        val placed = placeButtons(
            buttons = buttons,
            screenWidth = bounds.width(),
            screenHeight = bounds.height(),
            sizePx = ::sizePx,
            topPx = bar("status_bar_height", 24),
            bottomPx = bar("navigation_bar_height", 24),
            marginPx = margin,
            tucked = folded,
        ).associateBy { it.id }
        for (spec in buttons) {
            val spot = placed[spec.id] ?: continue
            val holder = views[spec.id]
            if (holder == null) {
                addButton(spec, spot.xPx, spot.yPx, spot.sizePx)
            } else {
                holder.spec = spec
                holder.view.spec = spec
                holder.view.alpha = spec.alpha
                if (!holder.dragging) {
                    holder.params.width = spot.sizePx
                    holder.params.height = spot.sizePx
                    holder.params.x = spot.xPx
                    holder.params.y = spot.yPx
                    runCatching { wm.updateViewLayout(holder.view, holder.params) }
                }
            }
        }
    }

    /** Park free buttons on the nearest edge without forgetting where they were dropped. */
    fun setFolded(folded: Boolean) {
        if (this.folded == folded) return
        this.folded = folded
        if (!hidden) render(specs)
    }

    fun hide() {
        hidden = true
        dismissMenu()
        views.values.toList().forEach { detach(it) }
        views.clear()
    }

    fun relayout() {
        if (!hidden) render(specs)
    }

    fun showMenu(button: ButtonSpec) {
        if (button.menu.isEmpty() || wm == null) return
        dismissMenu()
        val holder = views[button.id] ?: return
        val location = IntArray(2)
        holder.view.getLocationOnScreen(location)
        val cx = location[0] + holder.view.width / 2f
        val cy = location[1] + holder.view.height / 2f
        val bounds = screenBounds()
        val itemSize = context.dp(64)
        val spots = placeMenuItems(
            count = button.menu.size,
            anchorX = cx,
            anchorY = cy,
            screenWidth = bounds.width(),
            screenHeight = bounds.height(),
            itemSize = itemSize,
            radius = holder.view.width * 1.45f,
            margin = context.dp(8),
        )
        val root = FrameLayout(context)
        root.setBackgroundColor(0x33000000)
        root.setOnClickListener { dismissMenu() }
        root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismissMenu()
                true
            } else {
                false
            }
        }
        button.menu.forEachIndexed { index, action ->
            val (x, y) = spots.getOrElse(index) { context.dp(8) to context.dp(8) }
            val item = RoundButtonView(context).apply {
                val icon = actionIcon(action)
                spec = if (icon != null) {
                    button.copy(label = "", icon = icon, alpha = 1f, shape = "circle")
                } else {
                    button.copy(label = actionShort(action), alpha = 1f, shape = "circle")
                }
                alpha = 1f
                contentDescription = actionTitle(action)
                if (action.type == TYPE_TALK) {
                    setOnTouchListener { view, event ->
                        val buttonView = view as RoundButtonView
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                menuTalking = true
                                buttonView.listening = true
                                callbacks.onTalkDown()
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                buttonView.listening = false
                                val wasTalking = menuTalking
                                menuTalking = false
                                if (wasTalking) callbacks.onTalkUp()
                                if (event.actionMasked == MotionEvent.ACTION_UP) dismissMenu()
                                true
                            }
                            else -> true
                        }
                    }
                } else {
                    setOnClickListener {
                        dismissMenu()
                        callbacks.onMenuAction(action)
                    }
                }
            }
            val captionAbove = y + itemSize + context.dp(28) > bounds.height() - context.dp(8)
            root.addView(item, FrameLayout.LayoutParams(itemSize, itemSize).apply {
                leftMargin = x
                topMargin = y
            })
            val caption = TextView(context).apply {
                text = actionShort(action)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 12f
                background = GradientDrawable().apply {
                    cornerRadius = context.dp(10).toFloat()
                    setColor(0xCC222222.toInt())
                }
                val pad = context.dp(6)
                setPadding(pad, context.dp(2), pad, context.dp(2))
            }
            root.addView(caption, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = x.coerceAtMost((bounds.width() - context.dp(72)).coerceAtLeast(0))
                topMargin = if (captionAbove) (y - context.dp(28)).coerceAtLeast(context.dp(4)) else y + itemSize + context.dp(2)
            })
        }
        val params = baseParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT).apply {
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        }
        try {
            wm.addView(root, params)
            menuRoot = root
            root.requestFocus()
        } catch (_: Exception) {
            menuRoot = null
        }
    }

    fun dismissMenu() {
        if (menuTalking) {
            menuTalking = false
            callbacks.onTalkUp()
        }
        val root = menuRoot ?: return
        menuRoot = null
        runCatching { wm?.removeView(root) }
    }

    private fun addButton(
        spec: ButtonSpec,
        xPx: Int,
        yPx: Int,
        size: Int,
    ) {
        val windowManager = wm ?: return
        val view = RoundButtonView(context).apply {
            this.spec = spec
            alpha = spec.alpha
        }
        val params = baseParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.START
            x = xPx
            y = yPx
        }
        val holder = Holder(
            view = view,
            params = params,
            machine = ButtonGestureMachine(touchSlop()),
            spec = spec,
        )
        view.setOnTouchListener { _, event ->
            val outside = event.actionMasked == MotionEvent.ACTION_DOWN && !view.hit(event.x, event.y)
            if (outside) {
                false
            } else {
                onTouch(holder, event)
                true
            }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                installTouchableRegion(v) { (v as RoundButtonView).touchPath() }
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
        try {
            windowManager.addView(view, params)
            views[spec.id] = holder
        } catch (_: Exception) {
            // Permission missing or the window type is not allowed yet.
        }
    }

    private fun onTouch(holder: Holder, event: MotionEvent) {
        if (holdsToTalk(holder.spec)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!holder.talking) {
                        holder.talking = true
                        holder.view.listening = true
                        callbacks.onTalkDown()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishTalk(holder)
            }
            return
        }
        val now = SystemClock.uptimeMillis()
        val signals = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> holder.machine.onDown(event.rawX, event.rawY, now)
            MotionEvent.ACTION_MOVE -> holder.machine.onMove(event.rawX, event.rawY)
            MotionEvent.ACTION_UP -> holder.machine.onUp(now)
            MotionEvent.ACTION_CANCEL -> holder.machine.cancel()
            else -> emptyList()
        }
        dispatch(holder, signals)
    }

    private fun dispatch(holder: Holder, signals: List<GestureSignal>) {
        for (signal in signals) {
            when (signal) {
                is GestureSignal.WatchLongPress -> {
                    val runnable = Runnable {
                        dispatch(holder, holder.machine.onLongPressTimeout(SystemClock.uptimeMillis()))
                    }
                    holder.longRunnable = runnable
                    holder.view.postDelayed(runnable, (signal.fireAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                }
                GestureSignal.CancelLongPress -> holder.longRunnable?.let { holder.view.removeCallbacks(it) }
                is GestureSignal.WatchSingleTap -> {
                    val runnable = Runnable {
                        dispatch(holder, holder.machine.onSingleTapTimeout(SystemClock.uptimeMillis()))
                    }
                    holder.tapRunnable = runnable
                    holder.view.postDelayed(runnable, (signal.fireAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
                }
                GestureSignal.CancelSingleTap -> holder.tapRunnable?.let { holder.view.removeCallbacks(it) }
                GestureSignal.Tap -> callbacks.onGesture(holder.spec, GESTURE_TAP)
                GestureSignal.DoubleTap -> callbacks.onGesture(holder.spec, GESTURE_DOUBLE)
                GestureSignal.LongPress -> {
                    holder.view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    callbacks.onGesture(holder.spec, GESTURE_LONG)
                }
                is GestureSignal.Drag -> drag(holder, signal.dx, signal.dy)
                GestureSignal.DragFinished -> snap(holder)
            }
        }
    }

    private fun drag(holder: Holder, dx: Float, dy: Float) {
        holder.dragging = true
        dismissMenu()
        holder.params.x += dx.roundToInt()
        holder.params.y += dy.roundToInt()
        runCatching { wm?.updateViewLayout(holder.view, holder.params) }
    }

    private fun snap(holder: Holder) {
        holder.dragging = false
        val bounds = screenBounds()
        val size = holder.view.width.coerceAtLeast(1)
        val margin = context.dp(8)
        val centerX = holder.params.x + size / 2
        val centerY = holder.params.y + size / 2
        val edge = snapEdge(centerX, bounds.width())
        val edgeName = if (edge == ScreenEdge.Left) "left" else "right"
        val dropped = if (holder.spec.placement == PLACE_FREE) {
            holder.spec.copy(
                edge = edgeName,
                xFraction = centerFraction(centerX, bounds.width()),
                yFraction = centerFraction(centerY, bounds.height()),
            )
        } else {
            holder.spec.copy(
                edge = edgeName,
                yFraction = clampFraction(centerY, bounds.height()),
                xFraction = if (edge == ScreenEdge.Left) 0.08f else 0.92f,
            )
        }
        val others = specs.map { if (it.id == holder.spec.id) dropped else it }
        val placed = placeButtons(
            buttons = others,
            screenWidth = bounds.width(),
            screenHeight = bounds.height(),
            sizePx = { button -> if (button.id == holder.spec.id) size else sizePx(button) },
            topPx = bar("status_bar_height", 24),
            bottomPx = bar("navigation_bar_height", 24),
            marginPx = margin,
            tucked = folded,
        ).first { it.id == holder.spec.id }
        holder.params.x = placed.xPx
        holder.params.y = placed.yPx
        holder.spec = dropped
        runCatching { wm?.updateViewLayout(holder.view, holder.params) }
        val savedX = centerFraction(placed.xPx + size / 2, bounds.width())
        val savedY = if (dropped.placement == PLACE_FREE) {
            centerFraction(placed.yPx + size / 2, bounds.height())
        } else {
            clampFraction(placed.yPx + size / 2, bounds.height())
        }
        callbacks.onMoved(holder.spec.id, edgeName, savedX, savedY)
    }

    private fun finishTalk(holder: Holder) {
        if (!holder.talking) return
        holder.talking = false
        holder.view.listening = false
        callbacks.onTalkUp()
    }

    private fun detach(holder: Holder) {
        finishTalk(holder)
        holder.longRunnable?.let { holder.view.removeCallbacks(it) }
        holder.tapRunnable?.let { holder.view.removeCallbacks(it) }
        runCatching { wm?.removeView(holder.view) }
    }

    private fun baseParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun sizePx(spec: ButtonSpec): Int = context.dp(sizeDp(spec.size))

    private fun touchSlop(): Int = android.view.ViewConfiguration.get(context).scaledTouchSlop

    private fun bar(name: String, fallbackDp: Int): Int {
        val id = context.resources.getIdentifier(name, "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id) else context.dp(fallbackDp)
    }

    private fun screenBounds(): android.graphics.Rect {
        val windowManager = wm ?: return android.graphics.Rect(0, 0, 1080, 2400)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            android.graphics.Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
    }

    private class Holder(
        val view: RoundButtonView,
        val params: WindowManager.LayoutParams,
        val machine: ButtonGestureMachine,
        var spec: ButtonSpec,
        var dragging: Boolean = false,
        var talking: Boolean = false,
        var longRunnable: Runnable? = null,
        var tapRunnable: Runnable? = null,
    )
}
