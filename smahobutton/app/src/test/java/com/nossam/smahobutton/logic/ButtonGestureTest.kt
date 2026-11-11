package com.nossam.smahobutton.logic

import org.junit.Assert.assertTrue
import org.junit.Test

class ButtonGestureTest {
    private fun machine() = ButtonGestureMachine(touchSlopPx = 10, longPressMs = 450, doubleTapMs = 280)

    @Test
    fun singleTapWaitsOutTheDoubleTapWindow() {
        val machine = machine()
        machine.onDown(0f, 0f, 0)
        val up = machine.onUp(40)
        assertTrue(up.any { it is GestureSignal.WatchSingleTap })
        assertTrue(up.none { it is GestureSignal.Tap })
        val fired = machine.onSingleTapTimeout(40 + 280)
        assertTrue(fired.single() is GestureSignal.Tap)
    }

    @Test
    fun doubleTapDoesNotAlsoFireASingleTap() {
        val machine = machine()
        machine.onDown(0f, 0f, 0)
        machine.onUp(40)
        machine.onDown(2f, 1f, 120)
        val second = machine.onUp(160)
        assertTrue(second.any { it is GestureSignal.DoubleTap })
        assertTrue(machine.onSingleTapTimeout(500).isEmpty())
    }

    @Test
    fun longPressCancelsTheTap() {
        val machine = machine()
        machine.onDown(0f, 0f, 0)
        val fired = machine.onLongPressTimeout(450)
        assertTrue(fired.any { it is GestureSignal.LongPress })
        val up = machine.onUp(600)
        assertTrue(up.none { it is GestureSignal.Tap || it is GestureSignal.WatchSingleTap || it is GestureSignal.DoubleTap })
    }

    @Test
    fun dragCancelsTheClickAndReportsMovement() {
        val machine = machine()
        machine.onDown(0f, 0f, 0)
        val start = machine.onMove(30f, 0f)
        assertTrue(start.any { it is GestureSignal.CancelLongPress })
        val move = machine.onMove(45f, 4f)
        val drag = move.filterIsInstance<GestureSignal.Drag>().single()
        assertEqualsRoughly(15f, drag.dx)
        assertEqualsRoughly(4f, drag.dy)
        val up = machine.onUp(200)
        assertTrue(up.any { it is GestureSignal.DragFinished })
        assertTrue(up.none { it is GestureSignal.WatchSingleTap })
    }

    private fun assertEqualsRoughly(expected: Float, actual: Float) {
        assertTrue("expected $expected but was $actual", kotlin.math.abs(expected - actual) < 0.01f)
    }
}
