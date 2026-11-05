package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPrepTest {
    @Test
    fun smallCropsGrowUntilTheShortSideIsLarge() {
        assertEquals(2f, upscaleFactor(800, 1200))
        assertEquals(4f, upscaleFactor(400, 700))
        assertEquals(4f, upscaleFactor(80, 120))
        assertEquals(1f, upscaleFactor(2000, 2400))
        val wide = upscaleFactor(1000, 1400)
        assertTrue(wide * 1000 >= 1200f)
        assertTrue(wide <= 4f)
        assertTrue(upscaleFactor(3000, 4000) * 4000 <= 4096f)
    }

    @Test
    fun linesReadTopToBottomAndVerticalColumnsStayTogether() {
        val horizontal = joinLines(
            listOf(
                OcrLine("SAPPORO", 10, 100, 200, 140, 0.9f),
                OcrLine("静岡工場謹製", 10, 40, 180, 80, 0.8f),
                OcrLine("生", 200, 40, 240, 80, 0.7f),
            ),
        )
        assertEquals("静岡工場謹製生\nSAPPORO", horizontal.text)
        val vertical = joinLines(
            listOf(
                OcrLine("麦", 100, 110, 130, 160, 1f),
                OcrLine("静", 100, 10, 130, 60, 1f),
                OcrLine("岡", 102, 60, 132, 110, 1f),
            ),
        )
        assertEquals("静岡麦", vertical.text)
        assertEquals(1, vertical.verticalColumns)
        val stacked = joinLines(
            listOf(
                OcrLine("生", 10, 200, 70, 280, 0.9f),
                OcrLine("SAPPORO", 90, 320, 360, 370, 0.9f),
            ),
        )
        assertEquals("生\nSAPPORO", stacked.text)
    }

    @Test
    fun digitLookalikesChangeOnlyNextToDigits() {
        assertEquals("麦芽1009%ビール", tidyDigitLookalikes("麦芽!009%ビール"))
        assertEquals("100", tidyDigitLookalikes("1O0"))
        assertEquals("SHIZUOKA", tidyDigitLookalikes("SHIZUOKA"))
        assertEquals("謹製", tidyDigitLookalikes("謹製"))
    }

    @Test
    fun aSharperLatinLineReplacesOnlyThatLine() {
        val base = listOf(
            OcrLine("静岡工場謹製", 10, 20, 200, 60, 0.8f),
            OcrLine("BAKUUsYU", 10, 80, 240, 120, 0.4f),
        )
        val donor = listOf(OcrLine("BAKUSHU", 12, 82, 230, 118, 0.9f))
        val merged = spliceLatinLines(base, donor)
        assertEquals("静岡工場謹製", merged[0].text)
        assertEquals("BAKUSHU", merged[1].text)
        val digits = spliceDigitLines(
            listOf(OcrLine("麦芽!009%ビール", 10, 10, 300, 50, 0.4f)),
            listOf(OcrLine("麦芽100%ビール", 10, 12, 300, 48, 0.8f)),
        )
        assertEquals("麦芽100%ビール", digits.single().text)
    }

    @Test
    fun theClearerPassWinsAndAShortPassIsDropped() {
        val color = ReadPass("こんにちは世界", confidence = 1.2f)
        val gray = ReadPass("こ", confidence = 5f)
        val chosen = pickReadText(color, gray, accessibility = "")
        assertEquals("こんにちは世界", chosen)
        val grayBetter = pickReadText(
            ReadPass("設", 0.1f),
            ReadPass("設定を開く", 2f),
            accessibility = "",
        )
        assertEquals("設定を開く", grayBetter)
    }

    @Test
    fun aPhotoReadingBeatsTheGalleryDate() {
        val shown = shownRead("いっぱい\nたべや〜", "2025年11月29日")
        assertEquals("いっぱい\nたべや〜", shown.text)
        assertEquals("画像の文字", shown.source)
        assertNull(shownRead("", "20251月29日").text)
        assertEquals("画面の設定", shownRead("あ", "画面の設定").text)
        assertEquals("画面の文字", shownRead("あ", "画面の設定").source)
    }

    @Test
    fun accessibilityReplacesAWeakReadWhenItIsClearlyBetter() {
        val weak = ReadPass("あ", 0.2f)
        assertEquals("画面の設定", pickReadText(weak, ReadPass("", 0f), "画面の設定"))
        assertNull(pickReadText(ReadPass("", 0f), ReadPass("x", 1f), "  "))
        val photo = pickReadText(
            ReadPass("営業時間は10時から19時までです", 3f),
            ReadPass("営業時間は10時から19時までです", 2f),
            accessibility = "戻る",
        )
        assertEquals("営業時間は10時から19時までです", photo)
    }
}
