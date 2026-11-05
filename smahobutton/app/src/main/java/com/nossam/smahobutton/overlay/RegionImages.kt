package com.nossam.smahobutton.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.nossam.smahobutton.logic.upscaleFactor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object RegionImages {
    const val CONTRAST = 1.3f

    fun upscale(source: Bitmap): Bitmap = scale(source, upscaleFactor(source.width, source.height), null)

    /** Enlarge, then a light unsharp mask so small label strokes stay crisp. */
    fun prepareBase(source: Bitmap): Bitmap {
        val big = upscale(source)
        val sharp = sharpen(big)
        if (big !== source && big !== sharp && !big.isRecycled) big.recycle()
        return sharp
    }

    fun sharpen(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        if (width < 3 || height < 3) return source
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = pixels.copyOf()
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = y * width + x
                var r = 0f
                var g = 0f
                var b = 0f
                val neighbors = intArrayOf(
                    pixels[index - width],
                    pixels[index - 1],
                    pixels[index + 1],
                    pixels[index + width],
                )
                val center = pixels[index]
                for (pixel in neighbors) {
                    r -= Color.red(pixel) * 0.15f
                    g -= Color.green(pixel) * 0.15f
                    b -= Color.blue(pixel) * 0.15f
                }
                r += Color.red(center) * 1.6f
                g += Color.green(center) * 1.6f
                b += Color.blue(center) * 1.6f
                out[index] = Color.argb(
                    Color.alpha(center),
                    r.roundToInt().coerceIn(0, 255),
                    g.roundToInt().coerceIn(0, 255),
                    b.roundToInt().coerceIn(0, 255),
                )
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(out, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun equalize(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val histogram = IntArray(256)
        val luma = IntArray(pixels.size)
        for (index in pixels.indices) {
            luma[index] = luminance(pixels[index])
            histogram[luma[index]]++
        }
        val cdf = IntArray(256)
        var acc = 0
        for (i in 0..255) {
            acc += histogram[i]
            cdf[i] = acc
        }
        val floor = (0..255).firstOrNull { histogram[it] > 0 } ?: 0
        val denom = (pixels.size - histogram[floor]).coerceAtLeast(1)
        val lut = IntArray(256) { i ->
            ((cdf[i] - histogram[floor]) * 255f / denom).roundToInt().coerceIn(0, 255)
        }
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val from = luma[index].coerceAtLeast(1)
            val scale = lut[luma[index]].toFloat() / from
            pixels[index] = Color.argb(
                Color.alpha(pixel),
                (Color.red(pixel) * scale).roundToInt().coerceIn(0, 255),
                (Color.green(pixel) * scale).roundToInt().coerceIn(0, 255),
                (Color.blue(pixel) * scale).roundToInt().coerceIn(0, 255),
            )
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun gammaBright(source: Bitmap): Bitmap {
        val lut = IntArray(256) { i ->
            (255.0 * (i / 255.0).pow(0.8)).roundToInt().coerceIn(0, 255)
        }
        return remapRgb(source, lut)
    }

    /** Local threshold so glare on a can does not wash the whole label out. */
    fun adaptiveBinary(source: Bitmap, radius: Int = 16, bias: Int = 10): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val gray = IntArray(pixels.size) { luminance(pixels[it]) }
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 1..height) {
            var row = 0L
            for (x in 1..width) {
                row += gray[(y - 1) * width + (x - 1)]
                integral[y * (width + 1) + x] = integral[(y - 1) * (width + 1) + x] + row
            }
        }
        val out = IntArray(pixels.size)
        for (y in 0 until height) {
            val y0 = max(0, y - radius)
            val y1 = min(height - 1, y + radius)
            for (x in 0 until width) {
                val x0 = max(0, x - radius)
                val x1 = min(width - 1, x + radius)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = rectSum(integral, width, x0, y0, x1, y1)
                val mean = (sum / area).toInt()
                val black = gray[y * width + x] < mean - bias
                out[y * width + x] = if (black) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(out, 0, width, 0, 0, width, height)
        return bitmap
    }

    fun grayContrast(source: Bitmap): Bitmap {
        val gray = ColorMatrix().apply { setSaturation(0f) }
        gray.postConcat(contrastMatrix(CONTRAST))
        return scale(source, 1f, gray)
    }

    private fun scale(source: Bitmap, factor: Float, filter: ColorMatrix?): Bitmap {
        val width = max(1, (source.width * factor).roundToInt())
        val height = max(1, (source.height * factor).roundToInt())
        if (factor == 1f && filter == null) return source
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            isFilterBitmap = true
            isDither = true
            if (filter != null) colorFilter = ColorMatrixColorFilter(filter)
        }
        Canvas(out).drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(0, 0, width, height),
            paint,
        )
        return out
    }

    private fun luminance(pixel: Int): Int =
        (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f).roundToInt()
            .coerceIn(0, 255)

    private fun rectSum(integral: LongArray, width: Int, left: Int, top: Int, right: Int, bottom: Int): Long {
        val stride = width + 1
        val a = integral[top * stride + left]
        val b = integral[top * stride + (right + 1)]
        val c = integral[(bottom + 1) * stride + left]
        val d = integral[(bottom + 1) * stride + (right + 1)]
        return d - b - c + a
    }

    private fun remapRgb(source: Bitmap, lut: IntArray): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            pixels[i] = Color.argb(
                Color.alpha(pixel),
                lut[Color.red(pixel)],
                lut[Color.green(pixel)],
                lut[Color.blue(pixel)],
            )
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun contrastMatrix(contrast: Float): ColorMatrix {
        val translate = (0.5f - 0.5f * contrast) * 255f
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }
}
