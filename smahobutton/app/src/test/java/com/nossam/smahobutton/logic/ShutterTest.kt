package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutterTest {
    @Test
    fun pixelCameraIsACameraAndYouTubeIsNot() {
        assertTrue(isCameraPackage("com.google.android.GoogleCamera"))
        assertTrue(isCameraPackage("com.android.camera2"))
        assertTrue(!isCameraPackage("com.google.android.youtube"))
        assertTrue(!isCameraPackage("com.nossam.smahobutton"))
    }

    @Test
    fun findsTheShutterByJapaneseLabelOrViewId() {
        val root = ShutterNode(
            description = "カメラを切り替え",
            clickable = true,
            children = listOf(
                ShutterNode(text = "動画", clickable = true),
                ShutterNode(
                    description = "シャッター",
                    viewId = "com.google.android.GoogleCamera:id/shutter_button",
                    clickable = true,
                ),
            ),
        )
        val hit = findShutter(root)
        assertEquals("com.google.android.GoogleCamera:id/shutter_button", hit?.node?.viewId)
        assertTrue((hit?.score ?: 0) >= 100)
    }

    @Test
    fun ignoresTheCameraSwitchButton() {
        val root = ShutterNode(description = "カメラを切り替え", viewId = "com.google.android.GoogleCamera:id/camera_switch_button", clickable = true)
        assertNull(findShutter(root))
    }

    @Test
    fun portraitDefaultIsBottomCenterUntilCalibrated() {
        val defaults = shutterFractions(emptyList(), "com.google.android.GoogleCamera", portrait = true)
        assertEquals(0.50f, defaults.first)
        assertEquals(0.88f, defaults.second)
        val saved = listOf(ShutterSpot("com.google.android.GoogleCamera", "portrait", 0.42f, 0.80f))
        assertEquals(0.42f to 0.80f, shutterFractions(saved, "com.google.android.GoogleCamera", portrait = true))
        val landscape = shutterFractions(saved, "com.google.android.GoogleCamera", portrait = false)
        assertEquals(0.88f, landscape.first)
    }

    @Test
    fun backupKeepsTheShutterSpot() {
        val original = SettingsSpec(
            shutterOnOpen = true,
            shutterSpots = listOf(ShutterSpot("com.google.android.GoogleCamera", "portrait", 0.5f, 0.75f)),
            buttons = listOf(ButtonSpec(tap = ActionSpec(TYPE_SHUTTER))),
        )
        val again = readBackup(encodeBackup(original))
        assertTrue(again is BackupRead.Ok)
        val restored = (again as BackupRead.Ok).settings
        assertTrue(restored.shutterOnOpen)
        assertEquals(original.shutterSpots, restored.shutterSpots)
        assertEquals(TYPE_SHUTTER, restored.buttons.single().tap.type)
    }
}
