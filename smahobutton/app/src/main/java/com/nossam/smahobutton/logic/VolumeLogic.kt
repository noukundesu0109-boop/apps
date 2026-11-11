package com.nossam.smahobutton.logic

data class FilterConfig(
    val ownPackage: String,
    val launcherPackages: Set<String> = emptySet(),
    val imePackages: Set<String> = emptySet(),
)

/**
 * Packages that flicker over the real app (shade, permission dialogs, our own overlay).
 * Launchers are not in this set: going home is a real leave, and a short debounce
 * absorbs the brief launcher flashes people call "launcher noise".
 */
object PackageFilter {
    val transient: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.intentresolver",
    )

    fun isNoise(packageName: String?, config: FilterConfig): Boolean {
        if (packageName.isNullOrBlank()) return true
        if (packageName == config.ownPackage) return true
        if (packageName in transient) return true
        if (packageName in config.imePackages) return true
        val lower = packageName.lowercase()
        if ("inputmethod" in lower || ".ime" in lower || lower.endsWith(".ime")) return true
        return false
    }
}

class ForegroundDebounce(val windowMs: Long = 450L) {
    var pending: String? = null
        private set
    private var pendingAt: Long = 0L
    private var confirmed: String? = null

    fun onEvent(packageName: String, now: Long): String? {
        if (pending != packageName) {
            pending = packageName
            pendingAt = now
        }
        return confirmIfReady(now)
    }

    fun poll(now: Long): String? = confirmIfReady(now)

    fun remainingMs(now: Long): Long {
        val start = if (pending == null) return windowMs else pendingAt
        return (start + windowMs - now).coerceAtLeast(0L)
    }

    private fun confirmIfReady(now: Long): String? {
        val packageName = pending ?: return null
        if (now - pendingAt < windowMs) return null
        if (confirmed == packageName) return null
        confirmed = packageName
        return packageName
    }
}

data class VolumeMemory(
    val managedPackage: String? = null,
    val baseline: Int? = null,
    val outputKey: String = "",
)

data class VolumeChange(
    val memory: VolumeMemory,
    val setVolume: Int? = null,
)

/**
 * Entering a saved app captures the current media volume as the baseline (only from an
 * unsaved app) and applies the saved level. Leaving the last saved app restores that
 * baseline when [restoreOnLeave] is on. Switching from one saved app to another keeps
 * the original baseline.
 */
fun applyForeground(
    memory: VolumeMemory,
    packageName: String,
    currentVolume: Int,
    saved: Map<String, Int>,
    restoreOnLeave: Boolean,
    outputKey: String = "",
    launcherPackages: Set<String> = emptySet(),
    force: Boolean = false,
): VolumeChange {
    // Home is a real leave, but a saved launcher row must never be applied.
    val leaving = isLauncherPackage(packageName, launcherPackages)
    if (!leaving && packageName == memory.managedPackage) {
        // Same package and same output already applied. force is for the retries after
        // playback starts, when the app or Bluetooth absolute volume has put the old level back.
        if (!force && outputKey == memory.outputKey) return VolumeChange(memory)
        val switched = saved[packageName]
        return if (switched != null) {
            VolumeChange(memory.copy(outputKey = outputKey), switched)
        } else {
            VolumeChange(memory.copy(outputKey = outputKey))
        }
    }
    val target = if (leaving) null else saved[packageName]
    if (target != null) {
        val baseline = if (memory.managedPackage == null) currentVolume else memory.baseline
        return VolumeChange(VolumeMemory(packageName, baseline, outputKey), target)
    }
    if (memory.managedPackage != null && restoreOnLeave && memory.baseline != null) {
        return VolumeChange(VolumeMemory(), memory.baseline)
    }
    return VolumeChange(VolumeMemory())
}

data class VolumeOutcome(
    val changedPackage: String? = null,
    val setVolume: Int? = null,
)

/** Re-apply after the app is foreground, measured from the moment it settled. */
val VOLUME_REINFORCE_AT_MS: LongArray = longArrayOf(500L, 1500L, 3000L)

data class VolumeApplyNote(
    val appName: String,
    val deviceName: String,
    val volume: Int,
    val clock: String,
    val gradual: Boolean = false,
    val kind: String = "",
)

/** Gap between volume-key steps when a car ignores a single jump. */
const val VOLUME_STEP_GAP_MS = 50L

/** true means raise one notch. Empty when [current] is already [target]. */
fun volumeStepPlan(current: Int, target: Int): List<Boolean> {
    if (current == target) return emptyList()
    val up = target > current
    return List(kotlin.math.abs(target - current)) { up }
}

fun volumeApplyLine(note: VolumeApplyNote?): String {
    if (note == null) return "さいごに合わせた: まだありません"
    val method = if (note.gradual) "じわじわ" else "いっきに"
    val kind = note.kind.ifBlank { if (note.deviceName == "スマホ") "スマホ" else "Bluetooth" }
    return "さいごに合わせた: ${note.appName} / ${note.deviceName}（$kind） / ${note.volume} / $method / ${note.clock}"
}

class ForegroundVolumeCoordinator(val windowMs: Long = 450L) {
    private val debounce = ForegroundDebounce(windowMs)
    private var launcherPackages: Set<String> = emptySet()
    var memory: VolumeMemory = VolumeMemory()
    var confirmedPackage: String? = null
        private set

    fun onWindow(
        packageName: String?,
        now: Long,
        currentVolume: Int,
        saved: Map<String, Int>,
        restoreOnLeave: Boolean,
        filter: FilterConfig,
        outputKey: String = "",
    ): VolumeOutcome {
        launcherPackages = filter.launcherPackages
        if (PackageFilter.isNoise(packageName, filter)) return VolumeOutcome()
        val confirmed = debounce.onEvent(packageName!!, now) ?: return VolumeOutcome()
        return commit(confirmed, currentVolume, saved, restoreOnLeave, outputKey)
    }

    fun poll(
        now: Long,
        currentVolume: Int,
        saved: Map<String, Int>,
        restoreOnLeave: Boolean,
        outputKey: String = "",
    ): VolumeOutcome {
        val confirmed = debounce.poll(now) ?: return VolumeOutcome()
        return commit(confirmed, currentVolume, saved, restoreOnLeave, outputKey)
    }

    /** Milliseconds until [poll] might confirm a new package, or null if nothing is pending. */
    fun millisUntilPoll(now: Long): Long? {
        val pending = debounce.pending ?: return null
        if (pending == confirmedPackage) return null
        return debounce.remainingMs(now)
    }

    private fun commit(
        packageName: String,
        currentVolume: Int,
        saved: Map<String, Int>,
        restoreOnLeave: Boolean,
        outputKey: String,
    ): VolumeOutcome {
        confirmedPackage = packageName
        val change = applyForeground(
            memory,
            packageName,
            currentVolume,
            saved,
            restoreOnLeave,
            outputKey,
            launcherPackages,
        )
        memory = change.memory
        return VolumeOutcome(packageName, change.setVolume)
    }
}
