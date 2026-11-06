package com.nossam.smahobutton.logic

import kotlin.math.hypot

sealed interface GestureSignal {
    data class WatchLongPress(val fireAt: Long) : GestureSignal
    data object CancelLongPress : GestureSignal
    data class WatchSingleTap(val fireAt: Long) : GestureSignal
    data object CancelSingleTap : GestureSignal
    data object Tap : GestureSignal
    data object DoubleTap : GestureSignal
    data object LongPress : GestureSignal
    data class Drag(val dx: Float, val dy: Float) : GestureSignal
    data object DragFinished : GestureSignal
}

/**
 * Turns a stream of pointer events into tap, double tap, long press, or drag.
 * A single tap is reported only after the double-tap window closes, so a double
 * tap does not also fire a tap. Moving past the touch slop becomes a drag and
 * cancels the click.
 */
class ButtonGestureMachine(
    private val touchSlopPx: Int,
    private val longPressMs: Long = 450L,
    private val doubleTapMs: Long = 280L,
) {
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var longFired = false
    private var down = false
    private var awaitingSecond = false
    private var firstUpAt = 0L

    fun onDown(x: Float, y: Float, now: Long): List<GestureSignal> {
        down = true
        dragging = false
        longFired = false
        downX = x
        downY = y
        lastX = x
        lastY = y
        val signals = mutableListOf<GestureSignal>(GestureSignal.WatchLongPress(now + longPressMs))
        if (awaitingSecond) signals += GestureSignal.CancelSingleTap
        return signals
    }

    fun onMove(x: Float, y: Float): List<GestureSignal> {
        if (!down) return emptyList()
        val signals = mutableListOf<GestureSignal>()
        if (!dragging && hypot(x - downX, y - downY) > touchSlopPx) {
            dragging = true
            awaitingSecond = false
            signals += GestureSignal.CancelLongPress
            signals += GestureSignal.CancelSingleTap
            lastX = x
            lastY = y
            return signals
        }
        if (dragging) {
            signals += GestureSignal.Drag(x - lastX, y - lastY)
            lastX = x
            lastY = y
        }
        return signals
    }

    fun onUp(now: Long): List<GestureSignal> {
        if (!down) return emptyList()
        down = false
        if (dragging) {
            dragging = false
            awaitingSecond = false
            return listOf(GestureSignal.DragFinished, GestureSignal.CancelLongPress, GestureSignal.CancelSingleTap)
        }
        if (longFired) {
            longFired = false
            awaitingSecond = false
            return listOf(GestureSignal.CancelLongPress)
        }
        if (awaitingSecond && now - firstUpAt <= doubleTapMs) {
            awaitingSecond = false
            return listOf(
                GestureSignal.CancelLongPress,
                GestureSignal.CancelSingleTap,
                GestureSignal.DoubleTap,
            )
        }
        awaitingSecond = true
        firstUpAt = now
        return listOf(GestureSignal.CancelLongPress, GestureSignal.WatchSingleTap(now + doubleTapMs))
    }

    fun onLongPressTimeout(now: Long): List<GestureSignal> {
        if (!down || dragging || longFired) return emptyList()
        longFired = true
        awaitingSecond = false
        return listOf(GestureSignal.LongPress, GestureSignal.CancelSingleTap)
    }

    fun onSingleTapTimeout(now: Long): List<GestureSignal> {
        if (!awaitingSecond || down) return emptyList()
        if (now - firstUpAt < doubleTapMs) return emptyList()
        awaitingSecond = false
        return listOf(GestureSignal.Tap)
    }

    fun cancel(): List<GestureSignal> {
        val wasDragging = dragging
        down = false
        dragging = false
        longFired = false
        awaitingSecond = false
        return if (wasDragging) {
            listOf(GestureSignal.DragFinished, GestureSignal.CancelLongPress, GestureSignal.CancelSingleTap)
        } else {
            listOf(GestureSignal.CancelLongPress, GestureSignal.CancelSingleTap)
        }
    }
}
