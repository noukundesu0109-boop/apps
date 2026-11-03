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
    fun menuFansAwayFromTheEdge() {
        val right = menuOffsets(3, ScreenEdge.Right, 100f)
        assertTrue(right.all { it.first < 0f })
        val left = menuOffsets(3, ScreenEdge.Left, 100f)
        assertTrue(left.all { it.first > 0f })
        assertEquals(1, menuOffsets(1, ScreenEdge.Right, 80f).size)
    }
}
