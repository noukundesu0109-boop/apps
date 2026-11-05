package com.nossam.smahobutton.logic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class Selection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun normalized(): Selection = Selection(
        left = min(left, right),
        top = min(top, bottom),
        right = max(left, right),
        bottom = max(top, bottom),
    )

    fun bigEnough(minPx: Float): Boolean {
        val box = normalized()
        return box.width >= minPx && box.height >= minPx
    }
}

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = (right - left).coerceAtLeast(0)
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    fun isEmpty(): Boolean = width < 1 || height < 1
}

enum class Corner { NW, NE, SW, SE }

fun clampSelection(raw: Selection, viewWidth: Int, viewHeight: Int): Selection {
    if (viewWidth <= 0 || viewHeight <= 0) return Selection(0f, 0f, 0f, 0f)
    val box = raw.normalized()
    val maxX = viewWidth.toFloat()
    val maxY = viewHeight.toFloat()
    return Selection(
        left = box.left.coerceIn(0f, maxX),
        top = box.top.coerceIn(0f, maxY),
        right = box.right.coerceIn(0f, maxX),
        bottom = box.bottom.coerceIn(0f, maxY),
    )
}

fun moveSelection(raw: Selection, dx: Float, dy: Float, viewWidth: Int, viewHeight: Int): Selection {
    val box = raw.normalized()
    val maxDx = viewWidth - box.right
    val minDx = -box.left
    val maxDy = viewHeight - box.bottom
    val minDy = -box.top
    val cx = dx.coerceIn(minDx, maxDx)
    val cy = dy.coerceIn(minDy, maxDy)
    return Selection(box.left + cx, box.top + cy, box.right + cx, box.bottom + cy)
}

fun resizeSelection(raw: Selection, corner: Corner, x: Float, y: Float, viewWidth: Int, viewHeight: Int): Selection {
    val box = raw.normalized()
    val next = when (corner) {
        Corner.NW -> box.copy(left = x, top = y)
        Corner.NE -> box.copy(right = x, top = y)
        Corner.SW -> box.copy(left = x, bottom = y)
        Corner.SE -> box.copy(right = x, bottom = y)
    }
    return clampSelection(next, viewWidth, viewHeight)
}

fun cornerHit(raw: Selection, x: Float, y: Float, radius: Float): Corner? {
    val box = raw.normalized()
    val points = listOf(
        Corner.NW to (box.left to box.top),
        Corner.NE to (box.right to box.top),
        Corner.SW to (box.left to box.bottom),
        Corner.SE to (box.right to box.bottom),
    )
    return points.firstOrNull { (_, point) ->
        abs(point.first - x) <= radius && abs(point.second - y) <= radius
    }?.first
}

fun containsPoint(raw: Selection, x: Float, y: Float): Boolean {
    val box = raw.normalized()
    return x in box.left..box.right && y in box.top..box.bottom
}

/**
 * Maps a rectangle drawn on the overlay onto the screenshot bitmap.
 * The overlay and the bitmap can differ in size; both axes scale on their own.
 * The result is clamped inside the bitmap. A box that shrinks to nothing is empty.
 */
fun mapSelectionToBitmap(
    raw: Selection,
    viewWidth: Int,
    viewHeight: Int,
    bitmapWidth: Int,
    bitmapHeight: Int,
): PixelRect {
    if (viewWidth <= 0 || viewHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
        return PixelRect(0, 0, 0, 0)
    }
    val box = clampSelection(raw, viewWidth, viewHeight)
    val left = (box.left / viewWidth * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth)
    val top = (box.top / viewHeight * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight)
    val right = (box.right / viewWidth * bitmapWidth).roundToInt().coerceIn(0, bitmapWidth)
    val bottom = (box.bottom / viewHeight * bitmapHeight).roundToInt().coerceIn(0, bitmapHeight)
    return PixelRect(left, top, right, bottom)
}

fun buttonRowTop(
    rectTop: Int,
    rectBottom: Int,
    rowHeight: Int,
    viewHeight: Int,
    insetTop: Int,
    insetBottom: Int,
    gap: Int,
): Int {
    val below = rectBottom + gap
    if (below + rowHeight <= viewHeight - insetBottom) return below
    val above = rectTop - gap - rowHeight
    if (above >= insetTop) return above
    return (viewHeight - insetBottom - rowHeight).coerceAtLeast(insetTop)
}

data class TextSnippet(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val text: String,
)

fun snippetsTouching(snippets: List<TextSnippet>, rect: PixelRect): String {
    if (rect.isEmpty()) return ""
    val lines = snippets.mapNotNull { snippet ->
        val text = snippet.text.trim()
        if (text.isEmpty()) return@mapNotNull null
        val hits = snippet.left < rect.right &&
            rect.left < snippet.right &&
            snippet.top < rect.bottom &&
            rect.top < snippet.bottom
        if (hits) text else null
    }
    return lines.distinct().joinToString("\n")
}
