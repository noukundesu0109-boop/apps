package com.nossam.smahobutton.logic

import kotlin.math.max
import kotlin.math.min

/** How much to enlarge a crop before OCR. At least 2×, and enough that the short side reaches 1000px. */
fun upscaleFactor(width: Int, height: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val short = min(width, height).toFloat()
    val long = max(width, height).toFloat()
    val wanted = when {
        short * 2f >= 1000f -> 2f
        short * 3f >= 1000f -> 3f
        else -> (1000f / short).coerceAtMost(12f)
    }
    val cap = 4096f / long
    return if (cap < 1f) 1f else wanted.coerceAtMost(cap).coerceAtLeast(1f)
}

data class ReadPass(val text: String, val confidence: Float)

/**
 * Picks one string for the edit box.
 * Empty or one-character OCR passes are dropped. When both passes remain, the higher
 * score (readable characters plus recognition confidence) wins. Accessibility text replaces
 * that only when it is clearly longer and cleaner.
 */
fun pickReadText(color: ReadPass, gray: ReadPass, accessibility: String): String? {
    val passes = listOf(color, gray).filter { it.text.trim().length >= 2 }
    val ocr = when {
        passes.isEmpty() -> ""
        passes.size == 1 -> passes[0].text.trim()
        else -> passes.maxBy { readScore(it) }.text.trim()
    }
    val a11y = accessibility.trim()
    val ocrQuality = readQuality(ocr)
    val a11yQuality = readQuality(a11y)
    return when {
        ocrQuality <= 0f && a11yQuality <= 0f -> null
        ocrQuality <= 0f -> a11y
        a11yQuality <= 0f -> ocr
        a11yQuality >= ocrQuality * 1.35f && a11y.length > ocr.length -> a11y
        else -> ocr
    }
}

fun readScore(pass: ReadPass): Float {
    val quality = readQuality(pass.text)
    if (quality <= 0f) return 0f
    return quality * (1f + pass.confidence.coerceAtLeast(0f))
}

fun readQuality(text: String): Float {
    val trimmed = text.trim()
    if (trimmed.length < 2) return 0f
    var useful = 0
    var odd = 0
    for (ch in trimmed) {
        if (ch.isWhitespace()) continue
        val code = ch.code
        val readable = ch.isLetterOrDigit() ||
            code in 0x3040..0x30FF ||
            code in 0x4E00..0x9FFF ||
            code in 0xFF00..0xFFEF ||
            ch in "、。！？「」『』（）()・ー—…．，"
        if (readable) useful++ else odd++
    }
    return useful - odd * 0.35f
}
