package com.nossam.smahobutton.overlay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import com.nossam.smahobutton.logic.upscaleFactor
import kotlin.math.max
import kotlin.math.roundToInt

object RegionImages {
    const val CONTRAST = 1.3f

    fun upscale(source: Bitmap): Bitmap = scale(source, upscaleFactor(source.width, source.height), null)

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
