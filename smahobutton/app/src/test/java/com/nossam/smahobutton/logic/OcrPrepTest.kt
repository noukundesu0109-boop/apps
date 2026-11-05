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
