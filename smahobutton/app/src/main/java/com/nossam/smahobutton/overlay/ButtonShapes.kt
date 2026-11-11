package com.nossam.smahobutton.overlay

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import com.nossam.smahobutton.logic.SHAPE_ORANGE
import kotlin.math.cos
import kotlin.math.sin

/** Flat character silhouettes in a square. Coordinates are fractions of the button size. */
internal object ButtonShapes {
    fun isBlob(shape: String): Boolean = shape.startsWith("b")

    fun silhouette(shape: String, size: Float): Path {
        val path = Path()
        when (shape) {
            "b1" -> path.circle(size, 0.50f, 0.52f, 0.40f)
            "b2" -> cloud(path, size)
            "b3" -> {
                path.roundRect(size, 0.18f, 0.18f, 0.82f, 0.82f, 0.18f)
                path.rotate(size, 15f)
            }
            "b4" -> fatStar(path, size)
            "b5" -> cluster(path, size)
            "b6" -> lump(path, size)
            "b7" -> petals(path, size, 8, 0.20f, 0.30f)
            "b8" -> drop(path, size)
            "b9" -> {
                path.roundRect(size, 0.10f, 0.32f, 0.90f, 0.70f, 0.19f)
                path.rotate(size, -12f)
            }
            "b10" -> softPolygon(path, size, 3, 0.42f, -8f)
            "b11" -> softPolygon(path, size, 5, 0.40f, 12f)
            "b12" -> petals(path, size, 7, 0.16f, 0.34f)
            "b13" -> {
                softPolygon(path, size, 8, 0.40f, 18f)
            }
            SHAPE_ORANGE -> {
                path.circle(size, 0.50f, 0.56f, 0.36f)
                path.roundRect(size, 0.46f, 0.08f, 0.54f, 0.24f, 0.03f)
                path.addOval(rect(size, 0.52f, 0.08f, 0.74f, 0.20f), Path.Direction.CW)
            }
            else -> path.circle(size, 0.50f, 0.50f, 0.46f)
        }
        return path
    }

    fun eyeShift(shape: String): Pair<Float, Float> = when (shape) {
        "b2" -> -0.05f to 0f
        "b3" -> 0.04f to 0f
        "b6" -> -0.07f to -0.02f
        "b7" -> 0.04f to 0f
        "b8" -> -0.08f to 0.06f
        "b10" -> 0f to 0.08f
        "b13" -> -0.03f to 0f
        SHAPE_ORANGE -> 0f to 0.04f
        else -> 0f to 0f
    }

    private fun cloud(path: Path, s: Float) {
        path.circle(s, 0.34f, 0.58f, 0.24f)
        path.circle(s, 0.58f, 0.50f, 0.28f)
        path.circle(s, 0.74f, 0.62f, 0.20f)
        path.circle(s, 0.48f, 0.66f, 0.18f)
    }

    private fun fatStar(path: Path, s: Float) {
        path.circle(s, 0.50f, 0.50f, 0.22f)
        path.circle(s, 0.50f, 0.22f, 0.20f)
        path.circle(s, 0.78f, 0.50f, 0.20f)
        path.circle(s, 0.50f, 0.78f, 0.20f)
        path.circle(s, 0.22f, 0.50f, 0.20f)
    }

    private fun cluster(path: Path, s: Float) {
        path.circle(s, 0.50f, 0.52f, 0.20f)
        path.circle(s, 0.32f, 0.40f, 0.20f)
        path.circle(s, 0.68f, 0.40f, 0.20f)
        path.circle(s, 0.36f, 0.66f, 0.18f)
        path.circle(s, 0.64f, 0.66f, 0.18f)
    }

    private fun lump(path: Path, s: Float) {
        path.circle(s, 0.38f, 0.40f, 0.26f)
        path.circle(s, 0.64f, 0.46f, 0.20f)
        path.circle(s, 0.52f, 0.66f, 0.24f)
    }

    private fun petals(path: Path, s: Float, count: Int, petal: Float, orbit: Float) {
        path.circle(s, 0.50f, 0.52f, petal * 0.85f)
        for (i in 0 until count) {
            val angle = Math.toRadians(-90.0 + i * 360.0 / count)
            val cx = 0.50f + cos(angle).toFloat() * orbit
            val cy = 0.52f + sin(angle).toFloat() * orbit
            path.circle(s, cx, cy, petal)
        }
    }

    private fun drop(path: Path, s: Float) {
        path.moveTo(0.30f * s, 0.24f * s)
        path.quadTo(0.16f * s, 0.48f * s, 0.36f * s, 0.72f * s)
        path.quadTo(0.50f * s, 0.92f * s, 0.70f * s, 0.74f * s)
        path.quadTo(0.92f * s, 0.50f * s, 0.68f * s, 0.32f * s)
        path.quadTo(0.52f * s, 0.14f * s, 0.30f * s, 0.24f * s)
        path.close()
    }

    private fun softPolygon(path: Path, s: Float, sides: Int, radius: Float, tilt: Float) {
        val cx = 0.50f * s
        val cy = 0.52f * s
        val r = radius * s
        val rot = Math.toRadians(tilt.toDouble())
        val pts = (0 until sides).map { i ->
            val angle = rot + (i * 2.0 * Math.PI / sides) - Math.PI / 2.0
            (cx + cos(angle) * r).toFloat() to (cy + sin(angle) * r).toFloat()
        }
        fun mid(a: Pair<Float, Float>, b: Pair<Float, Float>) =
            (a.first + b.first) / 2f to (a.second + b.second) / 2f
        val start = mid(pts.last(), pts.first())
        path.moveTo(start.first, start.second)
        for (i in pts.indices) {
            val curr = pts[i]
            val next = pts[(i + 1) % pts.size]
            val midPoint = mid(curr, next)
            path.quadTo(curr.first, curr.second, midPoint.first, midPoint.second)
        }
        path.close()
    }

    private fun Path.circle(s: Float, cx: Float, cy: Float, r: Float) {
        addCircle(cx * s, cy * s, r * s, Path.Direction.CW)
    }

    private fun Path.roundRect(s: Float, left: Float, top: Float, right: Float, bottom: Float, rad: Float) {
        addRoundRect(
            RectF(left * s, top * s, right * s, bottom * s),
            rad * s,
            rad * s,
            Path.Direction.CW,
        )
    }

    private fun Path.rotate(s: Float, degrees: Float) {
        val matrix = Matrix()
        matrix.setRotate(degrees, s / 2f, s / 2f)
        transform(matrix)
    }

    private fun rect(s: Float, left: Float, top: Float, right: Float, bottom: Float) =
        RectF(left * s, top * s, right * s, bottom * s)
}
