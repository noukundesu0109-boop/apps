package com.nossam.smahobutton.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundPickTest {
    private val filter = FilterConfig(
        ownPackage = "com.nossam.smahobutton",
        launcherPackages = setOf("com.example.myhome"),
        imePackages = setOf("com.google.android.inputmethod.latin"),
    )
    private val prime = "com.amazon.avod.thirdpartyclient"
    private val launcher = "com.google.android.apps.nexuslauncher"

    @Test
    fun launcherPackagesAndPrimePackagesAreRecognized() {
        assertTrue(isLauncherPackage(launcher))
        assertTrue(isLauncherPackage("com.example.myhome", filter.launcherPackages))
        assertTrue(isLauncherPackage("com.custom.home", label = "Pixel Launcher"))
        assertTrue(isLauncherPackage("com.foo.launcher"))
        assertFalse(isLauncherPackage(prime, filter.launcherPackages, "Prime Video"))
        assertFalse(isLauncherPackage("com.google.android.youtube"))
        assertFalse(PackageFilter.isNoise(launcher, filter))

        assertTrue(isPrimeVideo(prime))
        assertTrue(isPrimeVideo("com.amazon.avod"))
        assertTrue(isPrimeVideo("com.amazon.amazonvideo.livingroom"))
        assertTrue(isPrimeVideo("com.example.video", "プライムビデオ"))
        assertTrue(isPrimeVideo("com.example.video", "Amazon Prime Video"))
        assertFalse(isPrimeVideo("com.amazon.mShop.android.shopping", "Amazon"))
        assertFalse(isPrimeVideo("com.google.android.youtube", "YouTube"))
    }

    @Test
    fun focusedPrimeBeatsALauncherEvent() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window(prime, focused = true, active = true),
                window(launcher),
                window("com.nossam.smahobutton", typeApplication = false, focused = true),
            ),
            eventPackage = launcher,
            rootPackage = launcher,
            config = filter,
        )
        assertEquals(prime, picked)
    }

    @Test
    fun anActivePrimeWindowBeatsAFocusedLauncher() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window(launcher, focused = true),
                window(prime, active = true),
            ),
            eventPackage = launcher,
            rootPackage = launcher,
            config = filter,
        )
        assertEquals(prime, picked)
    }

    @Test
    fun aPrimeWindowAnywhereBeatsTheLauncherCache() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window("com.android.systemui", typeApplication = false, focused = true),
                window("com.google.android.inputmethod.latin", focused = true),
                window(launcher, focused = true, active = true),
                window(prime),
            ),
            eventPackage = launcher,
            rootPackage = null,
            config = filter,
        )
        assertEquals(prime, picked)
    }

    @Test
    fun theEventPackageIsUsedWhenWindowsOnlyShowTheLauncher() {
        val picked = pickForegroundPackage(
            windows = listOf(window(launcher, focused = true, active = true)),
            eventPackage = prime,
            rootPackage = launcher,
            config = filter,
        )
        assertEquals(prime, picked)
    }

    @Test
    fun theActiveRootIsUsedWhenTheEventIsTheLauncher() {
        val picked = pickForegroundPackage(
            windows = emptyList(),
            eventPackage = launcher,
            rootPackage = "com.amazon.avod",
            config = filter,
        )
        assertEquals("com.amazon.avod", picked)
    }

    @Test
    fun aPrimeOverlayIsUsedWhenTheOnlyApplicationWindowIsTheLauncher() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window(launcher, focused = true, active = true),
                window("com.example.player", typeApplication = false, active = true),
            ),
            eventPackage = launcher,
            rootPackage = launcher,
            config = filter,
            labelOf = { pkg -> if (pkg == "com.example.player") "プライムビデオ" else null },
        )
        assertEquals("com.example.player", picked)
    }

    @Test
    fun theFocusedAppBeatsAnotherAppBehindIt() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window("com.google.android.youtube", focused = true, active = true),
                window(prime),
            ),
            eventPackage = prime,
            rootPackage = prime,
            config = filter,
        )
        assertEquals("com.google.android.youtube", picked)
    }

    @Test
    fun onlyTheLauncherMeansTheUserLeftTheApp() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window(launcher, focused = true, active = true),
                window("com.example.myhome"),
                window("com.android.systemui", typeApplication = false),
            ),
            eventPackage = launcher,
            rootPackage = "com.example.myhome",
            config = filter,
        )
        assertEquals(launcher, picked)
    }

    @Test
    fun noiseAloneIsNotAForegroundApp() {
        val picked = pickForegroundPackage(
            windows = listOf(
                window("com.nossam.smahobutton", focused = true, active = true),
                window("com.android.systemui"),
            ),
            eventPackage = "com.google.android.inputmethod.latin",
            rootPackage = "android",
            config = filter,
        )
        assertNull(picked)
    }

    private fun window(
        packageName: String,
        typeApplication: Boolean = true,
        focused: Boolean = false,
        active: Boolean = false,
    ) = AppWindow(packageName, typeApplication, focused, active)
}
