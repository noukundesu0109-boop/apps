package com.nossam.smahobutton.logic

import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ScreenEdge { Left, Right }

data class PlacedButton(
    val id: String,
    val edge: ScreenEdge,
    val yPx: Int,
    val sizePx: Int,
)

fun parseEdge(raw: String): ScreenEdge =
    if (raw.equals("left", ignoreCase = true)) ScreenEdge.Left else ScreenEdge.Right

fun snapEdge(centerX: Int, screenWidth: Int): ScreenEdge =
    if (centerX < screenWidth / 2) ScreenEdge.Left else ScreenEdge.Right

fun clampFraction(centerY: Int, screenHeight: Int): Float {
    if (screenHeight <= 0) return 0.5f
    return (centerY.toFloat() / screenHeight).coerceIn(0.08f, 0.92f)
}

fun xFor(edge: ScreenEdge, screenWidth: Int, sizePx: Int, marginPx: Int): Int {
    val room = (screenWidth - sizePx - marginPx).coerceAtLeast(marginPx)
    return if (edge == ScreenEdge.Left) marginPx else room
}

fun placeButtons(
    buttons: List<ButtonSpec>,
    screenWidth: Int,
    screenHeight: Int,
    sizePx: (ButtonSpec) -> Int,
    topPx: Int,
    bottomPx: Int,
    marginPx: Int,
): List<PlacedButton> {
    if (screenWidth <= 0 || screenHeight <= 0) {
        return buttons.map { PlacedButton(it.id, parseEdge(it.edge), 0, 1) }
    }
    val minY = topPx + marginPx
    val raw = buttons.map { spec ->
        val size = sizePx(spec).coerceAtLeast(1)
        val desired = (spec.yFraction * screenHeight - size / 2f).roundToInt()
        val maxY = (screenHeight - bottomPx - marginPx - size).coerceAtLeast(minY)
        PlacedButton(spec.id, parseEdge(spec.edge), desired.coerceIn(minY, maxY), size)
    }
    val adjusted = raw.toMutableList()
    for (edge in listOf(ScreenEdge.Left, ScreenEdge.Right)) {
        val group = adjusted.withIndex().filter { it.value.edge == edge }.sortedBy { it.value.yPx }
        var nextY = minY
        for (item in group) {
            val maxY = (screenHeight - bottomPx - marginPx - item.value.sizePx).coerceAtLeast(minY)
            val y = item.value.yPx.coerceAtLeast(nextY).coerceAtMost(maxY)
            adjusted[item.index] = item.value.copy(yPx = y)
            nextY = y + item.value.sizePx + marginPx
        }
    }
    return adjusted
}

/** Offsets from the button center. Right-edge buttons fan toward the left, and the reverse. */
fun menuOffsets(count: Int, edge: ScreenEdge, radiusPx: Float): List<Pair<Float, Float>> {
    if (count <= 0) return emptyList()
    val centerDeg = if (edge == ScreenEdge.Right) 180.0 else 0.0
    val span = when {
        count == 1 -> 0.0
        count == 2 -> 70.0
        else -> 120.0
    }
    return (0 until count).map { index ->
        val t = if (count == 1) 0.5 else index / (count - 1.0)
        val deg = centerDeg - span / 2.0 + span * t
        val rad = Math.toRadians(deg)
        (cos(rad) * radiusPx).toFloat() to (-sin(rad) * radiusPx).toFloat()
    }
}
