package com.nossam.smahobutton.logic

/**
 * One window from [android.accessibilityservice.AccessibilityService.getWindows].
 * The list is front to back: index 0 is the topmost window.
 */
data class AppWindow(
    val packageName: String,
    val typeApplication: Boolean,
    val focused: Boolean,
    val active: Boolean,
)

val KNOWN_LAUNCHERS: Set<String> = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.google.android.apps.pixel.launcher",
    "com.android.launcher",
    "com.android.launcher2",
    "com.android.launcher3",
    "com.sec.android.app.launcher",
    "com.miui.home",
    "com.huawei.android.launcher",
    "com.oppo.launcher",
    "com.nothing.launcher",
    "com.teslacoilsw.launcher",
    "com.microsoft.launcher",
    "com.sonyericsson.home",
    "com.htc.launcher",
    "com.asus.launcher",
    "com.lge.launcher2",
    "com.lge.launcher3",
    "com.anddoes.launcher",
    "com.actionlauncher.playstore",
    "ch.deletescape.lawnchair.plah",
    "app.lawnchair",
)

val PRIME_PACKAGES: Set<String> = setOf(
    "com.amazon.avod.thirdpartyclient",
    "com.amazon.avod",
    "com.amazon.amazonvideo.livingroom",
)

fun isLauncherPackage(
    packageName: String?,
    extra: Set<String> = emptySet(),
    label: String? = null,
): Boolean {
    val pkg = packageName?.trim().orEmpty()
    if (pkg.isEmpty()) return false
    if (pkg in extra || pkg in KNOWN_LAUNCHERS) return true
    val lower = pkg.lowercase()
    if (
        lower.endsWith(".launcher") ||
        lower.endsWith(".launcher2") ||
        lower.endsWith(".launcher3") ||
        "nexuslauncher" in lower
    ) {
        return true
    }
    return label?.trim().equals("Pixel Launcher", ignoreCase = true)
}

fun isPrimeVideo(packageName: String?, label: String? = null): Boolean {
    val pkg = packageName?.trim().orEmpty()
    val lower = pkg.lowercase()
    if (pkg in PRIME_PACKAGES) return true
    // Playback stays in the app package (the :player process is not a new package).
    // Sibling ids such as a living-room or regional build still start with these prefixes.
    if (lower.startsWith("com.amazon.avod.") || lower.startsWith("com.amazon.amazonvideo.")) return true
    val name = label?.trim().orEmpty()
    if (name.contains("プライムビデオ")) return true
    if (name.contains("prime video", ignoreCase = true)) return true
    return false
}

/**
 * Chooses the app the user is actually looking at.
 *
 * Fullscreen players (Prime Video in particular) often emit a window event whose
 * package is still the launcher. Prefer a real [AppWindow] of TYPE_APPLICATION,
 * then a Prime window of any type (picture-in-picture or a player overlay), then
 * the event package and the active-window root. Noise and the launcher are skipped
 * in that search. If the only real window is the launcher, return it so leaving
 * an app still restores the previous volume — callers must not save or apply a
 * volume for that package.
 */
fun pickForegroundPackage(
    windows: List<AppWindow>,
    eventPackage: String?,
    rootPackage: String?,
    config: FilterConfig,
    labelOf: (String) -> String? = { null },
): String? {
    fun labelFor(pkg: String): String? {
        if (isLauncherPackage(pkg, config.launcherPackages) || isPrimeVideo(pkg)) return null
        return labelOf(pkg)
    }

    fun usable(pkg: String?): String? {
        val name = pkg?.trim().orEmpty()
        if (name.isEmpty() || PackageFilter.isNoise(name, config)) return null
        if (isLauncherPackage(name, config.launcherPackages, labelFor(name))) return null
        return name
    }

    fun launcher(pkg: String?): String? {
        val name = pkg?.trim().orEmpty()
        if (name.isEmpty() || PackageFilter.isNoise(name, config)) return null
        return if (isLauncherPackage(name, config.launcherPackages, labelFor(name))) name else null
    }

    fun firstUsable(candidates: List<AppWindow>): String? {
        for (window in candidates) {
            usable(window.packageName)?.let { return it }
        }
        return null
    }

    val apps = windows.filter { it.typeApplication && it.packageName.isNotBlank() }
    firstUsable(apps.filter { it.focused })?.let { return it }
    firstUsable(apps.filter { it.active })?.let { return it }
    firstUsable(apps)?.let { return it }

    val primeOverlays = windows.filter { window ->
        !window.typeApplication &&
            window.packageName.isNotBlank() &&
            isPrimeVideo(window.packageName, labelFor(window.packageName))
    }
    firstUsable(primeOverlays.filter { it.focused })?.let { return it }
    firstUsable(primeOverlays.filter { it.active })?.let { return it }
    firstUsable(primeOverlays)?.let { return it }

    usable(eventPackage)?.let { return it }
    usable(rootPackage)?.let { return it }

    apps.firstOrNull { it.focused }?.let { launcher(it.packageName) }?.let { return it }
    apps.firstOrNull { it.active }?.let { launcher(it.packageName) }?.let { return it }
    for (window in apps) launcher(window.packageName)?.let { return it }
    launcher(eventPackage)?.let { return it }
    launcher(rootPackage)?.let { return it }
    return null
}
