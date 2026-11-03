package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundVolumeTest {
    private val filter = FilterConfig(
        ownPackage = "com.nossam.smahobutton",
        launcherPackages = setOf("com.google.android.apps.nexuslauncher"),
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
    fun stayingInTheSameAppDoesNotRecaptureTheBaseline() {
        val first = applyForeground(VolumeMemory(), "com.google.android.youtube", 10, mapOf("com.google.android.youtube" to 3), true)
        val again = applyForeground(first.memory, "com.google.android.youtube", 5, mapOf("com.google.android.youtube" to 3), true)
        assertEquals(first.memory, again.memory)
        assertNull(again.setVolume)
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
        assertEquals("YouTube の音量を覚えました", saved.message)
        assertEquals(7, saved.updated.single().volume)

        val blocked = rememberVolume("com.android.systemui", "System", 4, existing, filter)
        assertTrue(!blocked.ok)
        assertEquals(existing, blocked.updated)
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
