package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {
    @Test
    fun emptyObjectUsesFriendlyDefaults() {
        val spec = SettingsJson.decode("{}")
        assertFalse(spec.enabled)
        assertTrue(spec.restoreVolumeOnLeave)
        assertEquals(1, spec.buttons.size)
        val button = spec.buttons.single()
        assertEquals("right", button.edge)
        assertEquals("blue", button.color)
        assertEquals("m", button.size)
        assertEquals(TYPE_NONE, button.tap.type)
    }

    @Test
    fun roundTripKeepsActionsAndVolumes() {
        val original = SettingsSpec(
            enabled = true,
            restoreVolumeOnLeave = false,
            buttons = listOf(
                ButtonSpec(
                    id = "btn-1",
                    color = "pink",
                    size = "l",
                    alpha = 0.5f,
                    icon = "flash",
                    label = "光",
                    edge = "left",
                    yFraction = 0.25f,
                    useMenu = true,
                    tap = ActionSpec(TYPE_HOME),
                    doubleTap = ActionSpec(TYPE_BACK),
                    longPress = ActionSpec(TYPE_LOCK),
                    menu = listOf(
                        ActionSpec(TYPE_OPEN_APP, "com.google.android.youtube", "YouTube"),
                        ActionSpec(TYPE_TORCH),
                    ),
                ),
            ),
            appVolumes = listOf(AppVolumeSpec("com.google.android.youtube", "YouTube", 4)),
        )
        val again = SettingsJson.decode(SettingsJson.encode(original))
        assertEquals(original, again)
    }

    @Test
    fun unknownFieldsAndBadValuesFallBack() {
        val raw = """
            {
              "enabled": true,
              "futureFeature": 1,
              "buttons": [{
                "id": "a",
                "color": "nope",
                "size": "huge",
                "alpha": 9,
                "icon": "nope",
                "label": "ながい文字です",
                "edge": "middle",
                "yFraction": 4,
                "tap": {"type": "fly"},
                "menu": [{"type": "none"}, {"type": "home"}, {"type": "back"}]
              }]
            }
        """.trimIndent()
        val spec = SettingsJson.decode(raw)
        assertTrue(spec.enabled)
        val button = spec.buttons.single()
        assertEquals("blue", button.color)
        assertEquals("m", button.size)
        assertEquals(1f, button.alpha)
        assertEquals("dot", button.icon)
        assertEquals("ながい文", button.label)
        assertEquals("right", button.edge)
        assertEquals(0.92f, button.yFraction)
        assertEquals(PLACE_EDGE, button.placement)
        assertEquals(TYPE_NONE, button.tap.type)
        assertEquals(listOf(TYPE_HOME, TYPE_BACK), button.menu.map { it.type })
    }

    @Test
    fun oldBackupsStayOnTheEdgeAndFreePlacementRoundTrips() {
        val old = SettingsJson.decode("""{"buttons":[{"id":"a","edge":"left","yFraction":0.3}]}""")
        assertEquals(PLACE_EDGE, old.buttons.single().placement)
        assertEquals("left", old.buttons.single().edge)
        val free = SettingsSpec(
            buttons = listOf(
                ButtonSpec(id = "btn-1", placement = PLACE_FREE, xFraction = 0.33f, yFraction = 0.44f, edge = "left"),
            ),
        )
        val again = SettingsJson.decode(SettingsJson.encode(free))
        assertEquals(PLACE_FREE, again.buttons.single().placement)
        assertEquals(0.33f, again.buttons.single().xFraction, 0.001f)
        assertEquals(0.44f, again.buttons.single().yFraction, 0.001f)
    }

    @Test
    fun garbageJsonReturnsDefaults() {
        val spec = SettingsJson.decode("not json")
        assertEquals(normalize(SettingsSpec()), spec)
    }

    @Test
    fun buttonCountStaysBetweenOneAndFive() {
        val empty = normalize(SettingsSpec(buttons = emptyList()))
        assertEquals(1, empty.buttons.size)
        val many = normalize(SettingsSpec(buttons = List(6) { ButtonSpec(id = "b$it") }))
        assertEquals(5, many.buttons.size)
    }

    @Test
    fun openAppWithoutPackageBecomesNone() {
        val spec = normalize(SettingsSpec(buttons = listOf(ButtonSpec(tap = ActionSpec(TYPE_OPEN_APP)))))
        assertEquals(TYPE_NONE, spec.buttons.single().tap.type)
    }

    @Test
    fun oldBackupRestoresThePhoneSlotAndLeavesTheCarUnset() {
        val spec = SettingsJson.decode(
            """{"appVolumes":[{"packageName":"com.google.android.youtube","label":"YouTube","volume":4}]}""",
        )
        assertEquals(4, spec.appVolumes.single().volume)
        assertTrue(spec.appVolumes.single().devices.isEmpty())
    }

    @Test
    fun oldCarVolumeBecomesAGenericBluetoothFallback() {
        val spec = SettingsJson.decode(
            """{"appVolumes":[{"packageName":"com.a","label":"A","volume":4,"carVolume":9}]}""",
        )
        val device = spec.appVolumes.single().devices.single()
        assertEquals(LEGACY_BLUETOOTH_KEY, device.key)
        assertEquals(LEGACY_BLUETOOTH_NAME, device.name)
        assertEquals(9, device.volume)
        assertEquals(null, spec.appVolumes.single().carVolume)
    }

    @Test
    fun unknownShapeStaysACircle() {
        val spec = normalize(SettingsSpec(buttons = listOf(ButtonSpec(shape = "star"))))
        assertEquals(SHAPE_CIRCLE, spec.buttons.single().shape)
        assertEquals(SHAPE_CIRCLE, defaultButton(2).shape)
        assertTrue(prefersDarkInk(0xFFFB8C00))
        assertTrue(!prefersDarkInk(0xFF8E24AA))
    }

    @Test
    fun launcherVolumesAreRemoved() {
        val raw = """
            {"appVolumes":[
              {"packageName":"com.google.android.apps.nexuslauncher","label":"Pixel Launcher","volume":21},
              {"packageName":"com.google.android.youtube","label":"YouTube","volume":4},
              {"packageName":"com.custom.home","label":"Pixel Launcher","volume":2}
            ]}
        """.trimIndent()
        assertTrue(rawHasLauncherVolume(raw))
        assertTrue(!rawHasLauncherVolume("""{"appVolumes":[{"packageName":"com.google.android.youtube","label":"YouTube","volume":4}]}"""))
        val spec = SettingsJson.decode(raw)
        assertEquals(listOf("com.google.android.youtube"), spec.appVolumes.map { it.packageName })
        assertEquals(4, spec.appVolumes.single().volume)
    }

    @Test
    fun duplicateVolumesKeepTheLaterValue() {
        val volumes = normalizeVolumes(
            listOf(
                AppVolumeSpec("com.a", "A", 1),
                AppVolumeSpec(" ", "blank", 3),
                AppVolumeSpec("com.a", "A2", 5),
            ),
        )
        assertEquals(1, volumes.size)
        assertEquals(5, volumes.single().volume)
        assertEquals("A2", volumes.single().label)
    }

    @Test
    fun menuIsCappedAtSix() {
        val menu = List(8) { ActionSpec(TYPE_HOME) }
        val spec = normalize(SettingsSpec(buttons = listOf(ButtonSpec(menu = menu))))
        assertEquals(6, spec.buttons.single().menu.size)
    }
}
