package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionAndLayoutTest {
    @Test
    fun suggestsOnlyInstalledKnownAppsInOrder() {
        val installed = listOf(
            InstalledApp("com.spotify.music", "Spotify"),
            InstalledApp("com.google.android.apps.youtube.music", "YouTube Music"),
            InstalledApp("com.amazon.avod.thirdpartyclient", "Prime Video"),
            InstalledApp("jp.hamitv.hamiand1", "TVer"),
            InstalledApp("com.google.android.youtube", "YouTube"),
        )
        val suggestions = suggestApps(installed, emptySet())
        assertEquals(
            listOf(
                "com.google.android.youtube",
                "jp.hamitv.hamiand1",
                "com.amazon.avod.thirdpartyclient",
                "com.google.android.apps.youtube.music",
            ),
            suggestions.map { it.packageName },
        )
    }

    @Test
    fun tverIsDetectedByLabelWhenThePackageIsUnknown() {
        val installed = listOf(InstalledApp("com.example.player", "TVer(ティーバー) 民放公式"))
        val suggestions = suggestApps(installed, emptySet())
        assertEquals("com.example.player", suggestions.single().packageName)
    }

    @Test
    fun knownTverPackageWinsOverAnotherLabel() {
        val installed = listOf(
            InstalledApp("com.example.tver.fake", "TVer extra"),
            InstalledApp("jp.co.tver.mobile", "TVer"),
        )
        assertEquals("jp.co.tver.mobile", suggestApps(installed, emptySet()).single().packageName)
    }

    @Test
    fun savedAppsAreNotSuggestedAgain() {
        val installed = listOf(InstalledApp("com.google.android.youtube", "YouTube"))
        assertTrue(suggestApps(installed, setOf("com.google.android.youtube")).isEmpty())
    }

    @Test
    fun primeIsDetectedByLabelAndByTheOtherPackages() {
        val byLabel = suggestApps(listOf(InstalledApp("com.example.video", "プライムビデオ")), emptySet())
        assertEquals("com.example.video", byLabel.single().packageName)
        val packages = suggestApps(
            listOf(
                InstalledApp("com.amazon.avod", "Amazon Video"),
                InstalledApp("com.amazon.amazonvideo.livingroom", "Prime Video"),
            ),
            emptySet(),
        )
        assertEquals(
            listOf("com.amazon.avod", "com.amazon.amazonvideo.livingroom"),
            packages.map { it.packageName },
        )
    }

    @Test
    fun missingAppsAreNotInvented() {
        assertTrue(suggestApps(listOf(InstalledApp("com.android.chrome", "Chrome")), emptySet()).isEmpty())
    }

    @Test
    fun snapChoosesTheNearestEdge() {
        assertEquals(ScreenEdge.Left, snapEdge(100, 1000))
        assertEquals(ScreenEdge.Right, snapEdge(800, 1000))
        assertEquals(ScreenEdge.Right, snapEdge(500, 1000))
    }

    @Test
    fun buttonsOnTheSameEdgeArePushedApart() {
        val buttons = listOf(
            ButtonSpec(id = "a", edge = "right", yFraction = 0.5f),
            ButtonSpec(id = "b", edge = "right", yFraction = 0.5f),
            ButtonSpec(id = "c", edge = "left", yFraction = 0.2f),
        )
        val placed = placeButtons(
            buttons = buttons,
            screenWidth = 1000,
            screenHeight = 2000,
            sizePx = { 100 },
            topPx = 50,
            bottomPx = 50,
            marginPx = 10,
        )
        val right = placed.filter { it.edge == ScreenEdge.Right }
        assertEquals(2, right.size)
        assertTrue(right[1].yPx >= right[0].yPx + 100)
        assertEquals(ScreenEdge.Left, placed.first { it.id == "c" }.edge)
    }

    @Test
    fun aFreeButtonStaysWhereItWasDropped() {
        val placed = placeButtons(
            buttons = listOf(
                ButtonSpec(id = "free", placement = PLACE_FREE, xFraction = 0.4f, yFraction = 0.35f),
            ),
            screenWidth = 1000,
            screenHeight = 2000,
            sizePx = { 100 },
            topPx = 80,
            bottomPx = 60,
            marginPx = 10,
        )
        val button = placed.single()
        assertTrue(button.free)
        assertEquals(350, button.xPx)
        assertEquals(650, button.yPx)
        assertTrue(button.xPx > 10)
        assertTrue(button.yPx >= 80)
    }

    @Test
    fun freeButtonsStayClearOfTheStatusAndNavigationBars() {
        val placed = placeButtons(
            buttons = listOf(
                ButtonSpec(id = "high", placement = PLACE_FREE, xFraction = 0.02f, yFraction = 0.02f),
                ButtonSpec(id = "low", placement = PLACE_FREE, xFraction = 0.98f, yFraction = 0.98f),
            ),
            screenWidth = 1000,
            screenHeight = 2000,
            sizePx = { 100 },
            topPx = 80,
            bottomPx = 60,
            marginPx = 10,
        )
        val high = placed.first { it.id == "high" }
        val low = placed.first { it.id == "low" }
        assertTrue(high.yPx >= 90)
        assertTrue(high.xPx >= 10)
        assertTrue(low.yPx + low.sizePx <= 2000 - 60 - 10)
        assertTrue(low.xPx + low.sizePx <= 1000 - 10)
    }

    @Test
    fun onlyExactOverlapIsSeparated() {
        val same = placeButtons(
            buttons = listOf(
                ButtonSpec(id = "a", placement = PLACE_FREE, xFraction = 0.5f, yFraction = 0.5f),
                ButtonSpec(id = "b", placement = PLACE_FREE, xFraction = 0.5f, yFraction = 0.5f),
            ),
            screenWidth = 1000,
            screenHeight = 2000,
            sizePx = { 100 },
            topPx = 0,
            bottomPx = 0,
            marginPx = 8,
        )
        assertTrue(kotlin.math.abs(same[0].yPx - same[1].yPx) > 4 || kotlin.math.abs(same[0].xPx - same[1].xPx) > 4)
        val near = placeButtons(
            buttons = listOf(
                ButtonSpec(id = "a", placement = PLACE_FREE, xFraction = 0.40f, yFraction = 0.40f),
                ButtonSpec(id = "b", placement = PLACE_FREE, xFraction = 0.48f, yFraction = 0.46f),
            ),
            screenWidth = 1000,
            screenHeight = 2000,
            sizePx = { 80 },
            topPx = 0,
            bottomPx = 0,
            marginPx = 8,
        )
        assertEquals((0.48f * 1000 - 40).toInt(), near.first { it.id == "b" }.xPx)
    }

    @Test
    fun foldingParksAFreeButtonOnTheNearestEdgeAndRestoresIt() {
        val button = ButtonSpec(id = "free", placement = PLACE_FREE, xFraction = 0.3f, yFraction = 0.6f)
        val args = listOf(button)
        val parked = placeButtons(args, 1000, 2000, { 100 }, 40, 40, 10, tucked = true).single()
        assertEquals(ScreenEdge.Left, parked.edge)
        assertEquals(xFor(ScreenEdge.Left, 1000, 100, 10), parked.xPx)
        val back = placeButtons(args, 1000, 2000, { 100 }, 40, 40, 10, tucked = false).single()
        assertEquals((0.3f * 1000 - 50).toInt(), back.xPx)
        assertTrue(back.free)
    }

    @Test
    fun theMenuOpensTowardTheOpenSideAndStaysOnScreen() {
        val fromLeft = menuFan(1, menuCenterDegrees(120f, 1000f, 1000f, 2000f), 100f)
        assertTrue(fromLeft.single().first > 0f)
        val fromTopRight = menuFan(1, menuCenterDegrees(900f, 80f, 1000f, 2000f), 100f)
        assertTrue(fromTopRight.single().first < 0f)
        assertTrue(fromTopRight.single().second > 0f)
        val clamped = placeMenuItems(
            count = 3,
            anchorX = 12f,
            anchorY = 12f,
            screenWidth = 400,
            screenHeight = 800,
            itemSize = 64,
            radius = 220f,
            margin = 8,
        )
        assertEquals(3, clamped.size)
        assertTrue(clamped.all { it.first in 8..328 && it.second in 8..728 })
    }

    @Test
    fun menuFansAwayFromTheEdge() {
        val right = menuOffsets(3, ScreenEdge.Right, 100f)
        assertTrue(right.all { it.first < 0f })
        val left = menuOffsets(3, ScreenEdge.Left, 100f)
        assertTrue(left.all { it.first > 0f })
        assertEquals(1, menuOffsets(1, ScreenEdge.Right, 80f).size)
    }
}
