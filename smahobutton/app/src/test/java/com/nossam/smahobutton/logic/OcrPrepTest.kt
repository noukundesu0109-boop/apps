package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPrepTest {
    @Test
    fun smallCropsGrowUntilTheShortSideIsLarge() {
        assertEquals(2f, upscaleFactor(800, 1200))
        assertEquals(3f, upscaleFactor(400, 700))
        val tiny = upscaleFactor(80, 120)
        assertTrue(tiny * 80 >= 960f)
        assertTrue(tiny <= 12f)
        assertTrue(upscaleFactor(3000, 4000) * 4000 <= 4096f)
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
