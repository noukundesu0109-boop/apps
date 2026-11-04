package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundVolumeTest {
    private val filter = FilterConfig(
        ownPackage = "com.nossam.smahobutton",
        launcherPackages = setOf(
            "com.google.android.apps.nexuslauncher",
            "com.example.myhome",
        ),
        imePackages = setOf("com.google.android.inputmethod.latin"),
    )

    @Test
    fun noisePackagesAreIgnoredAndLaunchersAreReal() {
        assertTrue(PackageFilter.isNoise("com.android.systemui", filter))
        assertTrue(PackageFilter.isNoise("com.nossam.smahobutton", filter))
        assertTrue(PackageFilter.isNoise("com.google.android.inputmethod.latin", filter))
        assertTrue(PackageFilter.isNoise("com.example.inputmethod.pinyin", filter))
        assertTrue(PackageFilter.isNoise(null, filter))
        assertTrue(PackageFilter.isNoise("  ", filter))
        assertTrue(!PackageFilter.isNoise("com.google.android.apps.nexuslauncher", filter))
        assertTrue(!PackageFilter.isNoise("com.google.android.youtube", filter))
    }

    @Test
    fun debounceConfirmsAStablePackageAndIgnoresRepeats() {
        val debounce = ForegroundDebounce(400)
        assertNull(debounce.onEvent("com.google.android.youtube", 0))
        assertNull(debounce.onEvent("com.google.android.youtube", 100))
        assertEquals("com.google.android.youtube", debounce.poll(400))
        assertNull(debounce.poll(900))
        assertNull(debounce.onEvent("com.google.android.youtube", 1000))
    }

    @Test
    fun switchingAppsWaitsOutTheNewWindow() {
        val debounce = ForegroundDebounce(400)
        assertEquals("a", debounce.onEvent("a", 0).let { debounce.poll(400) })
        assertNull(debounce.onEvent("b", 1000))
        assertNull(debounce.poll(1300))
        assertEquals("b", debounce.poll(1400))
    }

    @Test
    fun savedAppSetsVolumeAndHomeRestoresTheBaseline() {
        val coord = ForegroundVolumeCoordinator(windowMs = 400)
        val saved = mapOf(
            "com.google.android.youtube" to 3,
            "com.amazon.avod.thirdpartyclient" to 8,
        )
        var volume = 10

        assertNull(coord.onWindow("com.android.systemui", 0, volume, saved, true, filter).setVolume)
        assertNull(coord.onWindow("com.google.android.youtube", 1000, volume, saved, true, filter).setVolume)
        assertNull(coord.onWindow("com.google.android.inputmethod.latin", 1200, volume, saved, true, filter).setVolume)
        assertNull(coord.onWindow("com.nossam.smahobutton", 1300, volume, saved, true, filter).setVolume)

        val entered = coord.poll(1400, volume, saved, true)
        assertEquals("com.google.android.youtube", entered.changedPackage)
        assertEquals(3, entered.setVolume)
        assertEquals(10, coord.memory.baseline)
        volume = 3

        assertNull(coord.onWindow("com.amazon.avod.thirdpartyclient", 2000, volume, saved, true, filter).setVolume)
        val prime = coord.poll(2400, volume, saved, true)
        assertEquals(8, prime.setVolume)
        assertEquals(10, coord.memory.baseline)
        volume = 8

        assertNull(coord.onWindow("com.google.android.apps.nexuslauncher", 3000, volume, saved, true, filter).setVolume)
        val home = coord.poll(3400, volume, saved, true)
        assertEquals(10, home.setVolume)
        assertNull(coord.memory.managedPackage)
        assertNull(coord.memory.baseline)
    }

    @Test
    fun aFlickerOfTheLauncherDoesNotLeaveTheSavedApp() {
        val coord = ForegroundVolumeCoordinator(windowMs = 400)
        val saved = mapOf("com.google.android.youtube" to 3)
        val enter = coord.onWindow("com.google.android.youtube", 0, 10, saved, true, filter)
        assertNull(enter.setVolume)
        val confirmed = coord.poll(400, 10, saved, true)
        assertEquals(3, confirmed.setVolume)

        assertNull(coord.onWindow("com.google.android.apps.nexuslauncher", 1000, 3, saved, true, filter).setVolume)
        assertNull(coord.onWindow("com.google.android.youtube", 1100, 3, saved, true, filter).setVolume)
        assertNull(coord.poll(1600, 3, saved, true).setVolume)
        assertEquals("com.google.android.youtube", coord.confirmedPackage)
        assertEquals("com.google.android.youtube", coord.memory.managedPackage)
    }

    @Test
    fun restoreOffLeavesTheVolumeWhereTheUserPutIt() {
        val coord = ForegroundVolumeCoordinator(windowMs = 100)
        val saved = mapOf("com.google.android.youtube" to 2)
        val entered = coord.pollAfter("com.google.android.youtube", 0, 9, saved, false)
        assertEquals(2, entered.setVolume)
        val left = coord.pollAfter("com.android.chrome", 500, 2, saved, false)
        assertNull(left.setVolume)
        assertNull(coord.memory.managedPackage)
    }

    @Test
    fun unsavedAppDoesNotChangeVolume() {
        val change = applyForeground(VolumeMemory(), "com.android.chrome", 6, emptyMap(), true)
        assertNull(change.setVolume)
        assertNull(change.memory.managedPackage)
    }

    @Test
    fun theSameAppCanBeForcedToApplyAgainWithoutRecapturingTheBaseline() {
        val saved = mapOf("com.google.android.youtube" to 3)
        val first = applyForeground(VolumeMemory(), "com.google.android.youtube", 10, saved, true, outputKey = "mac:AA")
        assertEquals(3, first.setVolume)
        val quiet = applyForeground(first.memory, "com.google.android.youtube", 9, saved, true, outputKey = "mac:AA")
        assertNull(quiet.setVolume)
        val again = applyForeground(
            first.memory,
            "com.google.android.youtube",
            9,
            saved,
            true,
            outputKey = "mac:AA",
            force = true,
        )
        assertEquals(3, again.setVolume)
        assertEquals(10, again.memory.baseline)
        assertEquals("com.google.android.youtube", again.memory.managedPackage)
    }

    @Test
    fun openingTheSavedAppAgainAppliesAgain() {
        val coord = ForegroundVolumeCoordinator(windowMs = 100)
        val saved = mapOf("com.google.android.youtube" to 3)
        assertEquals(3, coord.pollAfter("com.google.android.youtube", 0, 10, saved, true).setVolume)
        val home = coord.pollAfter("com.google.android.apps.nexuslauncher", 500, 3, saved, true)
        assertEquals(10, home.setVolume)
        assertEquals(3, coord.pollAfter("com.google.android.youtube", 1000, 10, saved, true).setVolume)
    }

    @Test
    fun stayingInTheSameAppDoesNotRecaptureTheBaseline() {
        val first = applyForeground(VolumeMemory(), "com.google.android.youtube", 10, mapOf("com.google.android.youtube" to 3), true)
        val again = applyForeground(first.memory, "com.google.android.youtube", 5, mapOf("com.google.android.youtube" to 3), true)
        assertEquals(first.memory, again.memory)
        assertNull(again.setVolume)
    }

    @Test
    fun switchingToTheCarKeepsTheBaselineAndUsesTheCarLevel() {
        val entered = applyForeground(
            VolumeMemory(),
            "com.google.android.youtube",
            10,
            mapOf("com.google.android.youtube" to 3),
            true,
        )
        assertEquals(3, entered.setVolume)
        val car = applyForeground(
            entered.memory,
            "com.google.android.youtube",
            3,
            mapOf("com.google.android.youtube" to 12),
            true,
            outputKey = "mac:AA:BB:CC:DD:EE:FF",
        )
        assertEquals(12, car.setVolume)
        assertEquals(10, car.memory.baseline)
        assertEquals("mac:AA:BB:CC:DD:EE:FF", car.memory.outputKey)
    }

    @Test
    fun aKnownDeviceBeatsTheOldBluetoothFallback() {
        val devices = listOf(
            DeviceVolume(LEGACY_BLUETOOTH_KEY, LEGACY_BLUETOOTH_NAME, 9),
            DeviceVolume("mac:AA:BB:CC:DD:EE:FF", "Pixel Buds", 4),
        )
        val buds = OutputSlot("mac:AA:BB:CC:DD:EE:FF", "Pixel Buds", bluetooth = true)
        val car = OutputSlot("mac:11:22:33:44:55:66", "Car", bluetooth = true)
        assertEquals(4, volumeForDevice(8, devices, buds))
        assertEquals(9, volumeForDevice(8, devices, car))
        assertEquals(8, volumeForDevice(8, devices, PHONE_SLOT))
        assertEquals(8, volumeForDevice(8, emptyList(), car))
    }

    @Test
    fun aRenamedBluetoothKeyStillMatchesTheSavedName() {
        val saved = listOf(DeviceVolume("mac:11:22:33:44:55:66", "EarFun Clip", 12))
        val now = OutputSlot("mac:AA:BB:CC:DD:EE:FF", "EarFun Clip", bluetooth = true)
        assertEquals(12, volumeForDevice(4, saved, now))
        val only = OutputSlot("name:Other", "Other", bluetooth = true)
        assertEquals(12, volumeForDevice(4, saved, only))
        val two = listOf(
            DeviceVolume("mac:11:22:33:44:55:66", "EarFun Clip", 12),
            DeviceVolume("mac:AA:BB:CC:DD:EE:FF", "Car", 3),
        )
        val unknown = OutputSlot("mac:01:02:03:04:05:06", "Other", bluetooth = true)
        assertEquals(4, volumeForDevice(4, two, unknown))
    }

    @Test
    fun connectedOutputsPreferTheSavedBluetoothDevice() {
        val car = RawAudioDevice(DEVICE_BLUETOOTH_A2DP, "11:11:11:11:11:11", "Car")
        val buds = RawAudioDevice(DEVICE_BLE_HEADSET, "aa:bb:cc:dd:ee:ff", "EarFun Clip")
        val picked = resolveOutput(listOf(car, buds), knownKeys = setOf("mac:AA:BB:CC:DD:EE:FF"))
        assertEquals("mac:AA:BB:CC:DD:EE:FF", picked.key)
        assertEquals("EarFun Clip", picked.name)
        assertEquals("mac:11:11:11:11:11:11", resolveOutput(listOf(buds, car)).key)
        val byName = resolveOutput(listOf(car, buds), knownNames = setOf("EarFun Clip"))
        assertEquals("EarFun Clip", byName.name)
    }

    @Test
    fun theVolumeScreenLineShowsTheLastApply() {
        assertEquals("さいごに合わせた: まだありません", volumeApplyLine(null))
        assertEquals(
            "さいごに合わせた: Prime Video / EarFun Clip / 12 / 19:42:01",
            volumeApplyLine(VolumeApplyNote("Prime Video", "EarFun Clip", 12, "19:42:01")),
        )
        assertEquals(longArrayOf(500L, 1500L, 3000L).toList(), VOLUME_REINFORCE_AT_MS.toList())
    }

    @Test
    fun rememberNamesTheCurrentDevice() {
        val buds = OutputSlot("mac:AA:BB:CC:DD:EE:FF", "Pixel Buds", bluetooth = true)
        val existing = listOf(AppVolumeSpec("com.google.android.youtube", "YouTube", 4))
        val saved = rememberVolume(
            "com.google.android.youtube",
            "YouTube",
            7,
            existing,
            filter,
            output = buds,
        )
        assertEquals("YouTube：Pixel Buds の音量 7 を覚えました", saved.message)
        assertEquals(4, saved.updated.single().volume)
        assertEquals(7, saved.updated.single().devices.single().volume)
        assertEquals("Pixel Buds", saved.updated.single().devices.single().name)
    }

    @Test
    fun earbudsUseTheMacAndANamelessDeviceUsesOneBluetoothSlot() {
        val named = resolveOutput(listOf(RawAudioDevice(DEVICE_BLUETOOTH_A2DP, "aa:bb:cc:dd:ee:ff", "Pixel Buds")))
        assertEquals("mac:AA:BB:CC:DD:EE:FF", named.key)
        assertEquals("Pixel Buds", named.name)
        val byName = resolveOutput(listOf(RawAudioDevice(DEVICE_BLE_HEADSET, "02:00:00:00:00:00", "Car Audio")))
        assertEquals("name:Car Audio", byName.key)
        val generic = resolveOutput(listOf(RawAudioDevice(DEVICE_BLE_SPEAKER, "", "Bluetooth")))
        assertEquals(GENERIC_BLUETOOTH_KEY, generic.key)
        assertEquals(GENERIC_BLUETOOTH_NAME, generic.name)
        val broadcast = resolveOutput(listOf(RawAudioDevice(DEVICE_BLE_BROADCAST, null, null)))
        assertEquals(GENERIC_BLUETOOTH_KEY, broadcast.key)
        assertEquals(PHONE_SLOT, resolveOutput(listOf(RawAudioDevice(4, null, "Wired"))))
    }

    @Test
    fun rememberVolumeSavesAndReplaces() {
        val existing = listOf(AppVolumeSpec("com.google.android.youtube", "YouTube", 1))
        val saved = rememberVolume(
            "com.google.android.youtube",
            "YouTube",
            7,
            existing,
            filter,
        )
        assertTrue(saved.ok)
        assertEquals("YouTube：スマホ の音量 7 を覚えました", saved.message)
        assertEquals(7, saved.updated.single().volume)

        val blocked = rememberVolume("com.android.systemui", "System", 4, existing, filter)
        assertTrue(!blocked.ok)
        assertEquals("この画面のアプリは覚えられません", blocked.message)
        assertEquals(existing, blocked.updated)
    }

    @Test
    fun rememberRejectsTheLauncherAndDoesNotSaveIt() {
        val existing = listOf(AppVolumeSpec("com.google.android.youtube", "YouTube", 4))
        val blocked = rememberVolume(
            "com.google.android.apps.nexuslauncher",
            "Pixel Launcher",
            21,
            existing,
            filter,
        )
        assertTrue(!blocked.ok)
        assertEquals("いま開いているアプリが分かりませんでした", blocked.message)
        assertEquals(existing, blocked.updated)

        val custom = rememberVolume("com.example.myhome", "Home", 9, existing, filter)
        assertTrue(!custom.ok)
        assertEquals("いま開いているアプリが分かりませんでした", custom.message)
        assertEquals(existing, custom.updated)

        val unknown = rememberVolume(null, null, 9, existing, filter)
        assertTrue(!unknown.ok)
        assertEquals("いま開いているアプリが分かりませんでした", unknown.message)
    }

    @Test
    fun aSavedLauncherVolumeIsNeverApplied() {
        val coord = ForegroundVolumeCoordinator(windowMs = 100)
        val saved = mapOf(
            "com.google.android.youtube" to 3,
            "com.google.android.apps.nexuslauncher" to 21,
            "com.example.myhome" to 21,
        )
        val entered = coord.pollAfter("com.google.android.youtube", 0, 10, saved, true)
        assertEquals(3, entered.setVolume)
        val home = coord.pollAfter("com.google.android.apps.nexuslauncher", 500, 3, saved, true)
        assertEquals(10, home.setVolume)
        assertNull(coord.memory.managedPackage)

        val again = coord.pollAfter("com.google.android.youtube", 1000, 10, saved, true)
        assertEquals(3, again.setVolume)
        val customHome = coord.pollAfter("com.example.myhome", 1500, 3, saved, true)
        assertEquals(10, customHome.setVolume)
        assertNull(coord.memory.managedPackage)
    }

    private fun ForegroundVolumeCoordinator.pollAfter(
        packageName: String,
        at: Long,
        volume: Int,
        saved: Map<String, Int>,
        restore: Boolean,
    ): VolumeOutcome {
        onWindow(packageName, at, volume, saved, restore, filter)
        return poll(at + windowMs, volume, saved, restore)
    }
}
