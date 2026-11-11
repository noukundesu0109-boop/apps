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
    val lines: List<OcrLine> = emptyList(),
    val digitFriendly: Boolean = false,
    val latinStrong: Boolean = false,
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

data class ShownRead(val text: String?, val source: String)

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
    return shownRead(ocr, accessibility).text
}

/**
 * The cropped photo wins whenever it has a real reading.
 * Accessibility text is only the fallback, and a gallery date header is not that fallback.
 */
fun shownRead(ocr: String, accessibility: String): ShownRead {
    val ocrText = ocr.trim()
    val screenText = accessibility.trim()
    val screenUsable = if (isGalleryDate(screenText) && screenText != ocrText) "" else screenText
    return when {
        isMeaningfulRead(ocrText) -> ShownRead(ocrText, "画像の文字")
        screenUsable.length >= 2 && readQuality(screenUsable) > 0f -> ShownRead(screenUsable, "画面の文字")
        ocrText.length >= 2 -> ShownRead(ocrText, "画像の文字")
        else -> ShownRead(null, "")
    }
}

fun isMeaningfulRead(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.length < 2 || isGalleryDate(trimmed)) return false
    var japanese = 0
    var letters = 0
    for (ch in trimmed) {
        if (ch.isWhitespace()) continue
        val code = ch.code
        if (code in 0x3040..0x30FF || code in 0x4E00..0x9FFF) japanese++
        if (ch.isLetter()) letters++
    }
    return japanese >= 2 || letters >= 2
}

fun isGalleryDate(text: String): Boolean {
    val compact = text.trim().replace(" ", "").replace("\n", "")
    if (compact.length > 24) return false
    return GALLERY_DATE.matches(compact)
}

private val GALLERY_DATE = Regex("""^\d{4}年?\d{1,2}月\d{1,2}日?$|^\d{4}[./-]\d{1,2}[./-]\d{1,2}$""")

fun readScore(pass: ReadPass): Float {
    val quality = readQuality(pass.text)
    if (quality <= 0f) return 0f
    val japanese = japaneseRatio(pass.text)
    val digitBoost = if (pass.digitFriendly && digitScore(pass.text) > 0) 6f else 0f
    val latinBoost = if (pass.latinStrong) latinLetters(pass.text) * 0.2f else 0f
    return quality * (1f + pass.confidence.coerceAtLeast(0f)) * (1f + 0.35f * japanese) +
        pass.verticalColumns * 2f + digitBoost + latinBoost
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
        if (row != null && row.any { sameBand(it, token) }) {
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

private fun sameBand(a: LineToken, b: LineToken): Boolean {
    val overlap = min(a.bottom, b.bottom) - max(a.top, b.top)
    val minHeight = min(a.bottom - a.top, b.bottom - b.top).coerceAtLeast(1)
    if (overlap < minHeight * 0.35f) return false
    val centerA = (a.top + a.bottom) / 2f
    val centerB = (b.top + b.bottom) / 2f
    return kotlin.math.abs(centerA - centerB) <= minHeight * 0.5f
}

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
            if (isLatinToken(prev) != isLatinToken(next) || (isLatinToken(prev) && isLatinToken(next))) {
                out.append(' ')
            }
        }
        out.append(piece)
    }
    return out.toString()
}

private fun isLatinToken(ch: Char): Boolean = ch.code < 128 && ch.isLetterOrDigit()

/**
 * Replaces a mostly-Latin line with the same line from a sharper Latin pass
 * when that pass read at least as many letters.
 */
fun spliceLatinLines(base: List<OcrLine>, donors: List<OcrLine>): List<OcrLine> {
    if (base.isEmpty() || donors.isEmpty()) return base
    val latinDonors = donors.filter { latinRatio(it.text) >= 0.5f }
    return base.map { line ->
        if (latinRatio(line.text) < 0.45f) return@map line
        val match = latinDonors.minByOrNull { kotlin.math.abs(centerY(it) - centerY(line)) } ?: return@map line
        val close = kotlin.math.abs(centerY(match) - centerY(line)) <= lineHeight(line) * 0.8f
        val lengthClose = kotlin.math.abs(match.text.trim().length - line.text.trim().length) <= 2
        val clearer = match.confidence > line.confidence + 0.2f && lengthClose
        val richer = latinLetters(match.text) > latinLetters(line.text)
        if (close && (richer || clearer)) line.copy(text = match.text.trim()) else line
    }
}

/** Prefers a high-contrast read of a line that already looks like a number or a percent. */
fun spliceDigitLines(base: List<OcrLine>, donors: List<OcrLine>): List<OcrLine> {
    if (base.isEmpty() || donors.isEmpty()) return base
    val digitDonors = donors.filter { digitScore(it.text) > 0 }
    return base.map { line ->
        if (digitScore(line.text) == 0 && '!' !in line.text) return@map line
        val match = digitDonors.minByOrNull { kotlin.math.abs(centerY(it) - centerY(line)) } ?: return@map line
        val close = kotlin.math.abs(centerY(match) - centerY(line)) <= lineHeight(line) * 0.8f
        if (close && digitScore(match.text) > digitScore(line.text)) line.copy(text = match.text.trim()) else line
    }
}

fun refineRead(passes: List<ReadPass>, accessibility: String): ShownRead {
    val usable = passes.filter { it.text.trim().length >= 2 }
    val winner = usable.maxByOrNull { readScore(it) }
    val ocr = if (winner == null) {
        ""
    } else {
        val latinDonors = passes.filter { it.latinStrong }.flatMap { it.lines }
        val digitDonors = passes.filter { it.digitFriendly }.flatMap { it.lines }
        val merged = spliceDigitLines(spliceLatinLines(winner.lines, latinDonors), digitDonors)
        val joined = if (merged.isEmpty()) winner.text else joinLines(merged).text
        tidyDigitLookalikes(joined.ifBlank { winner.text })
    }
    return shownRead(ocr, accessibility)
}

/**
 * Only fixes symbols that sit against digits. Kanji and ordinary words are left alone.
 * "100" read as "!009" becomes "1009"; an O between digits becomes 0.
 */
fun tidyDigitLookalikes(text: String): String {
    if (text.isEmpty()) return text
    val chars = text.toCharArray()
    for (index in chars.indices) {
        val prev = chars.getOrNull(index - 1)
        val next = chars.getOrNull(index + 1)
        val prevDigit = prev != null && (prev.isDigit() || prev == '%' || prev == '％')
        val nextDigit = next != null && (next.isDigit() || next == '%' || next == '％')
        when (chars[index]) {
            '!' -> if (prevDigit || nextDigit) chars[index] = '1'
            'l', 'I' -> if (prevDigit && nextDigit) chars[index] = '1'
            'O' -> if (prevDigit && nextDigit) chars[index] = '0'
        }
    }
    return String(chars)
}

private fun centerY(line: OcrLine) = (line.top + line.bottom) / 2

private fun lineHeight(line: OcrLine) = (line.bottom - line.top).coerceAtLeast(1)

private fun latinLetters(text: String) = text.count { it in 'A'..'Z' || it in 'a'..'z' }

private fun latinRatio(text: String): Float {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return 0f
    return latinLetters(trimmed).toFloat() / trimmed.count { !it.isWhitespace() }.coerceAtLeast(1)
}

private fun digitScore(text: String): Int {
    val digits = text.count { it.isDigit() }
    val percent = if ('%' in text || '％' in text) 2 else 0
    val bangs = text.count { it == '!' }
    return digits * 2 + percent - bangs * 3
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
