package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CropRectTest {
    @Test
    fun anUpsideDownDragBecomesANormalBox() {
        val box = clampSelection(Selection(80f, 200f, 10f, 40f), 100, 300)
        assertEquals(10f, box.left)
        assertEquals(40f, box.top)
        assertEquals(80f, box.right)
        assertEquals(200f, box.bottom)
    }

    @Test
    fun theBoxIsClampedToTheView() {
        val box = clampSelection(Selection(-20f, -5f, 500f, 900f), 100, 200)
        assertEquals(0f, box.left)
        assertEquals(0f, box.top)
        assertEquals(100f, box.right)
        assertEquals(200f, box.bottom)
    }

    @Test
    fun movingStopsAtTheScreenEdge() {
        val moved = moveSelection(Selection(10f, 10f, 40f, 30f), -100f, 500f, 100, 80)
        assertEquals(0f, moved.left)
        assertEquals(30f, moved.width)
        assertEquals(60f, moved.top)
        assertEquals(80f, moved.bottom)
    }

    @Test
    fun aCornerDragResizesThatCorner() {
        val next = resizeSelection(Selection(10f, 20f, 80f, 90f), Corner.SE, 60f, 40f, 100, 100)
        assertEquals(Selection(10f, 20f, 60f, 40f), next)
        assertEquals(Corner.NW, cornerHit(Selection(10f, 20f, 80f, 90f), 12f, 18f, 16f))
        assertNull(cornerHit(Selection(10f, 20f, 80f, 90f), 40f, 50f, 8f))
        assertTrue(containsPoint(Selection(10f, 20f, 80f, 90f), 40f, 50f))
    }

    @Test
    fun viewPixelsMapOntoALargerScreenshotAndStayInside() {
        val rect = mapSelectionToBitmap(
            Selection(10f, 20f, 60f, 80f),
            viewWidth = 100,
            viewHeight = 200,
            bitmapWidth = 1000,
            bitmapHeight = 2000,
        )
        assertEquals(PixelRect(100, 200, 600, 800), rect)
        assertEquals(500, rect.width)
        assertEquals(600, rect.height)
        val outside = mapSelectionToBitmap(
            Selection(-50f, -50f, 400f, 900f),
            viewWidth = 100,
            viewHeight = 200,
            bitmapWidth = 200,
            bitmapHeight = 400,
        )
        assertEquals(PixelRect(0, 0, 200, 400), outside)
        assertTrue(
            mapSelectionToBitmap(Selection(0f, 0f, 0f, 10f), 100, 100, 50, 50).isEmpty(),
        )
    }

    @Test
    fun theActionRowSitsBelowTheBoxUnlessThatWouldLeaveTheScreen() {
        assertEquals(220, buttonRowTop(40, 200, 80, 800, 30, 20, 20))
        assertEquals(60, buttonRowTop(160, 700, 80, 800, 30, 20, 20))
    }

    @Test
    fun onlyTextInsideTheBoxIsKept() {
        val snippets = listOf(
            TextSnippet(0, 0, 10, 10, "外"),
            TextSnippet(40, 40, 80, 70, "囲み"),
            TextSnippet(70, 60, 120, 100, "  続き  "),
            TextSnippet(40, 40, 50, 50, "   "),
        )
        assertEquals("囲み\n続き", snippetsTouching(snippets, PixelRect(30, 30, 90, 90)))
        assertEquals("", snippetsTouching(snippets, PixelRect(0, 0, 0, 0)))
        val photo = PixelRect(200, 800, 700, 1100)
        val nodes = listOf(
            TextSnippet(40, 80, 500, 160, "2025年11月29日"),
            TextSnippet(0, 0, 1080, 80, "時刻"),
            TextSnippet(0, 2200, 1080, 2400, "ナビ"),
            TextSnippet(0, 0, 1080, 2400, "写真全体"),
            TextSnippet(200, 800, 700, 1100, "いっぱい"),
        )
        assertEquals("いっぱい", snippetsInside(nodes, photo, statusBarBottom = 120, navBarTop = 2200))
        val mapped = mapSelectionToBitmap(Selection(100f, 400f, 400f, 900f), 1080, 2400, 1080, 2400)
        assertTrue(mapped.width < 1080)
        assertTrue(mapped.height < 2400)
    }

    @Test
    fun theRegionActionSurvivesBackup() {
        val spec = SettingsSpec(
            buttons = listOf(ButtonSpec(tap = ActionSpec(TYPE_REGION), menu = listOf(ActionSpec(TYPE_REGION)))),
        )
        val restored = SettingsJson.decode(SettingsJson.encode(spec))
        assertEquals(TYPE_REGION, restored.buttons.single().tap.type)
        assertEquals(TYPE_REGION, restored.buttons.single().menu.single().type)
        assertEquals("囲ってスクショ・コピー", actionTitle(restored.buttons.single().tap))
        assertEquals("囲む", actionShort(restored.buttons.single().tap))
    }
}
