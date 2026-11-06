package com.nossam.smahobutton.logic

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class ScreenEdge { Left, Right }

data class PlacedButton(
    val id: String,
    val edge: ScreenEdge,
    val yPx: Int,
    val sizePx: Int,
    val xPx: Int = 0,
    val free: Boolean = false,
)

fun parseEdge(raw: String): ScreenEdge =
    if (raw.equals("left", ignoreCase = true)) ScreenEdge.Left else ScreenEdge.Right

fun snapEdge(centerX: Int, screenWidth: Int): ScreenEdge =
    if (centerX < screenWidth / 2) ScreenEdge.Left else ScreenEdge.Right

fun clampFraction(centerY: Int, screenHeight: Int): Float {
    if (screenHeight <= 0) return 0.5f
    return (centerY.toFloat() / screenHeight).coerceIn(0.08f, 0.92f)
}

fun centerFraction(center: Int, screen: Int): Float {
    if (screen <= 0) return 0.5f
    return (center.toFloat() / screen).coerceIn(0.02f, 0.98f)
}

fun xFor(edge: ScreenEdge, screenWidth: Int, sizePx: Int, marginPx: Int): Int {
    val room = (screenWidth - sizePx - marginPx).coerceAtLeast(marginPx)
    return if (edge == ScreenEdge.Left) marginPx else room
}

/**
 * While a free button is folded away, park it on the nearest edge.
 * The saved fractions are left alone so it can return to the same spot.
 */
fun tuckEdge(xFraction: Float): ScreenEdge =
    if (xFraction < 0.5f) ScreenEdge.Left else ScreenEdge.Right

fun placeButtons(
    buttons: List<ButtonSpec>,
    screenWidth: Int,
    screenHeight: Int,
    sizePx: (ButtonSpec) -> Int,
    topPx: Int,
    bottomPx: Int,
    marginPx: Int,
    tucked: Boolean = false,
): List<PlacedButton> {
    if (screenWidth <= 0 || screenHeight <= 0) {
        return buttons.map { PlacedButton(it.id, parseEdge(it.edge), 0, 1) }
    }
    val minY = topPx + marginPx
    val minX = marginPx
    val raw = buttons.map { spec ->
        val size = sizePx(spec).coerceAtLeast(1)
        val maxY = (screenHeight - bottomPx - marginPx - size).coerceAtLeast(minY)
        val maxX = (screenWidth - marginPx - size).coerceAtLeast(minX)
        val free = spec.placement == PLACE_FREE
        val y = (spec.yFraction * screenHeight - size / 2f).roundToInt().coerceIn(minY, maxY)
        if (free && !tucked) {
            val x = (spec.xFraction * screenWidth - size / 2f).roundToInt().coerceIn(minX, maxX)
            PlacedButton(spec.id, snapEdge(x + size / 2, screenWidth), y, size, x, free = true)
        } else {
            val edge = if (free) tuckEdge(spec.xFraction) else parseEdge(spec.edge)
            val x = xFor(edge, screenWidth, size, marginPx).coerceIn(minX, maxX)
            PlacedButton(spec.id, edge, y, size, x, free = false)
        }
    }
    val adjusted = raw.toMutableList()
    for (edge in listOf(ScreenEdge.Left, ScreenEdge.Right)) {
        val group = adjusted.withIndex().filter { !it.value.free && it.value.edge == edge }.sortedBy { it.value.yPx }
        var nextY = minY
        for (item in group) {
            val maxY = (screenHeight - bottomPx - marginPx - item.value.sizePx).coerceAtLeast(minY)
            val y = item.value.yPx.coerceAtLeast(nextY).coerceAtMost(maxY)
            adjusted[item.index] = item.value.copy(yPx = y)
            nextY = y + item.value.sizePx + marginPx
        }
    }
    separateExactOverlaps(adjusted, screenWidth, screenHeight, topPx, bottomPx, marginPx)
    return adjusted
}

/** Move a free button only when it sits on the same pixels as another button. */
private fun separateExactOverlaps(
    placed: MutableList<PlacedButton>,
    screenWidth: Int,
    screenHeight: Int,
    topPx: Int,
    bottomPx: Int,
    marginPx: Int,
) {
    for (index in placed.indices) {
        val button = placed[index]
        if (!button.free) continue
        val blocked = placed.any { other ->
            other.id != button.id && abs(other.xPx - button.xPx) <= 4 && abs(other.yPx - button.yPx) <= 4
        }
        if (!blocked) continue
        val minY = topPx + marginPx
        val minX = marginPx
        val maxY = (screenHeight - bottomPx - marginPx - button.sizePx).coerceAtLeast(minY)
        val maxX = (screenWidth - marginPx - button.sizePx).coerceAtLeast(minX)
        val nudgedY = (button.yPx + button.sizePx / 2).coerceIn(minY, maxY)
        val next = if (abs(nudgedY - button.yPx) > 4) {
            button.copy(yPx = nudgedY)
        } else {
            button.copy(xPx = (button.xPx + button.sizePx / 2).coerceIn(minX, maxX))
        }
        placed[index] = next
    }
}

/** Offsets from the button center. Right-edge buttons fan toward the left, and the reverse. */
fun menuOffsets(count: Int, edge: ScreenEdge, radiusPx: Float): List<Pair<Float, Float>> {
    val center = if (edge == ScreenEdge.Right) 180.0 else 0.0
    return menuFan(count, center, radiusPx)
}

/**
 * Fan toward the side, and the vertical half, that has more room.
 * Degrees follow the existing fan: 0 is to the right, 180 is to the left,
 * and positive degrees rise up the screen.
 */
fun menuCenterDegrees(centerX: Float, centerY: Float, screenWidth: Float, screenHeight: Float): Double {
    if (screenWidth <= 0f || screenHeight <= 0f) return 180.0
    val faceRight = centerX < screenWidth / 2f
    val face = if (faceRight) 0.0 else 180.0
    val roomBelow = centerY < screenHeight / 2f
    val tilt = if (faceRight) {
        if (roomBelow) -32.0 else 32.0
    } else {
        if (roomBelow) 32.0 else -32.0
    }
    return face + tilt
}

fun menuFan(count: Int, centerDeg: Double, radiusPx: Float): List<Pair<Float, Float>> {
    if (count <= 0) return emptyList()
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

fun placeMenuItems(
    count: Int,
    anchorX: Float,
    anchorY: Float,
    screenWidth: Int,
    screenHeight: Int,
    itemSize: Int,
    radius: Float,
    margin: Int,
): List<Pair<Int, Int>> {
    if (count <= 0 || screenWidth <= 0 || screenHeight <= 0) return emptyList()
    val degrees = menuCenterDegrees(anchorX, anchorY, screenWidth.toFloat(), screenHeight.toFloat())
    val minX = margin
    val minY = margin
    val maxX = (screenWidth - itemSize - margin).coerceAtLeast(minX)
    val maxY = (screenHeight - itemSize - margin).coerceAtLeast(minY)
    return menuFan(count, degrees, radius).map { (dx, dy) ->
        val x = (anchorX + dx - itemSize / 2f).roundToInt().coerceIn(minX, maxX)
        val y = (anchorY + dy - itemSize / 2f).roundToInt().coerceIn(minY, maxY)
        x to y
    }
}
