package com.nossam.smahobutton.logic

import kotlin.math.max
import kotlin.math.min

/**
 * Enlarge a crop until its short side is about 1600px (at least 1200 when 4× is enough).
 * Scale is capped at 4×, and the long side is kept within 4096px.
 */
fun upscaleFactor(width: Int, height: Int): Float {
    if (width <= 0 || height <= 0) return 1f
    val short = min(width, height).toFloat()
    val long = max(width, height).toFloat()
    if (short >= 1600f) return 1f
    val wanted = (1600f / short).coerceAtMost(4f)
    val cap = 4096f / long
    return if (cap < 1f) 1f else wanted.coerceAtMost(cap).coerceAtLeast(1f)
}

data class ReadPass(
    val text: String,
    val confidence: Float,
    val verticalColumns: Int = 0,
)

data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Float,
)

data class JoinedRead(val text: String, val verticalColumns: Int)

/**
 * Picks one string for the edit box.
 * Empty or one-character OCR passes are dropped. When both passes remain, the higher
 * score (readable characters plus recognition confidence) wins. Accessibility text replaces
 * that only when it is clearly longer and cleaner.
 */
fun pickReadText(color: ReadPass, gray: ReadPass, accessibility: String): String? =
    pickReadText(listOf(color, gray), accessibility)

fun pickReadText(passes: List<ReadPass>, accessibility: String): String? {
    val usable = passes.filter { it.text.trim().length >= 2 }
    val ocr = when {
        usable.isEmpty() -> ""
        usable.size == 1 -> usable[0].text.trim()
        else -> usable.maxBy { readScore(it) }.text.trim()
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
    val japanese = japaneseRatio(pass.text)
    return quality * (1f + pass.confidence.coerceAtLeast(0f)) * (1f + 0.35f * japanese) +
        pass.verticalColumns * 2f
}

/** Share of kana and kanji among letters. Latin-only labels stay at 0 and are still usable. */
fun japaneseRatio(text: String): Float {
    var japanese = 0
    var letters = 0
    for (ch in text.trim()) {
        if (ch.isWhitespace()) continue
        val code = ch.code
        val kanaOrKanji = code in 0x3040..0x30FF || code in 0x4E00..0x9FFF
        if (kanaOrKanji || ch.isLetter()) letters++
        if (kanaOrKanji) japanese++
    }
    if (letters == 0) return 0f
    return japanese.toFloat() / letters
}

/**
 * Reads lines from top to bottom, and left to right inside a row.
 * Tall single-character boxes in one column are joined, so vertical label text stays together.
 */
fun joinLines(lines: List<OcrLine>): JoinedRead {
    val usable = lines.filter { it.text.isNotBlank() }
    if (usable.isEmpty()) return JoinedRead("", 0)
    val vertical = usable.filter { isVerticalPiece(it) }
    val horizontal = usable.filter { !isVerticalPiece(it) }
    val columns = clusterColumns(vertical)
    val tokens = horizontal.map { lineToken(it) } + columns
    val rows = mutableListOf<MutableList<LineToken>>()
    for (token in tokens.sortedWith(compareBy({ it.top }, { it.left }))) {
        val row = rows.lastOrNull()
        val height = (token.bottom - token.top).coerceAtLeast(1)
        if (row != null && token.top <= row.minOf { it.top } + height * 0.55f) {
            row += token
        } else {
            rows += mutableListOf(token)
        }
    }
    val text = rows.joinToString("\n") { row ->
        joinRow(row.sortedBy { it.left }.map { it.text })
    }.trim()
    return JoinedRead(text, columns.size)
}

private data class LineToken(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

private fun lineToken(line: OcrLine) = LineToken(
    line.text.trim(),
    line.left,
    line.top,
    line.right,
    line.bottom,
)

private fun isVerticalPiece(line: OcrLine): Boolean {
    val width = (line.right - line.left).coerceAtLeast(1)
    val height = (line.bottom - line.top).coerceAtLeast(1)
    val chars = line.text.trim().length
    return height > width * 1.2f && chars in 1..8
}

private fun clusterColumns(lines: List<OcrLine>): List<LineToken> {
    val sorted = lines.sortedBy { (it.left + it.right) / 2 }
    val groups = mutableListOf<MutableList<OcrLine>>()
    for (line in sorted) {
        val center = (line.left + line.right) / 2
        val group = groups.lastOrNull()
        val anchor = group?.map { (it.left + it.right) / 2 }?.average()?.toInt()
        val width = (line.right - line.left).coerceAtLeast(1)
        if (group != null && anchor != null && kotlin.math.abs(center - anchor) <= width) {
            group += line
        } else {
            groups += mutableListOf(line)
        }
    }
    return groups.map { group ->
        val ordered = group.sortedBy { it.top }
        LineToken(
            text = ordered.joinToString("") { it.text.trim() },
            left = ordered.minOf { it.left },
            top = ordered.minOf { it.top },
            right = ordered.maxOf { it.right },
            bottom = ordered.maxOf { it.bottom },
        )
    }
}

private fun joinRow(parts: List<String>): String {
    val out = StringBuilder()
    for (part in parts) {
        val piece = part.trim()
        if (piece.isEmpty()) continue
        if (out.isNotEmpty()) {
            val prev = out.last()
            val next = piece.first()
            if (prev.code < 128 && next.code < 128 && prev.isLetterOrDigit() && next.isLetterOrDigit()) {
                out.append(' ')
            }
        }
        out.append(piece)
    }
    return out.toString()
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
