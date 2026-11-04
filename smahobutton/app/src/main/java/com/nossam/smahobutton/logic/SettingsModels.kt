package com.nossam.smahobutton.logic

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class ActionSpec(
    val type: String = TYPE_NONE,
    val packageName: String? = null,
    val label: String? = null,
)

@Serializable
data class ButtonSpec(
    val id: String = "btn-1",
    val color: String = "blue",
    val size: String = "m",
    val alpha: Float = 0.72f,
    val icon: String = "dot",
    val label: String = "",
    val edge: String = "right",
    val yFraction: Float = 0.58f,
    val shape: String = SHAPE_CIRCLE,
    val useMenu: Boolean = false,
    val tap: ActionSpec = ActionSpec(),
    val doubleTap: ActionSpec = ActionSpec(),
    val longPress: ActionSpec = ActionSpec(),
    val menu: List<ActionSpec> = emptyList(),
)

@Serializable
data class DeviceVolume(
    val key: String,
    val name: String = "",
    val volume: Int = 0,
)

@Serializable
data class AppVolumeSpec(
    val packageName: String,
    val label: String = "",
    val volume: Int = 0,
    val carVolume: Int? = null,
    val devices: List<DeviceVolume> = emptyList(),
)

@Serializable
data class ShutterSpot(
    val packageName: String,
    val orientation: String = "portrait",
    val x: Float = 0.5f,
    val y: Float = 0.88f,
)

@Serializable
data class SettingsSpec(
    val enabled: Boolean = false,
    val restoreVolumeOnLeave: Boolean = true,
    val shutterOnOpen: Boolean = false,
    val shutterSpots: List<ShutterSpot> = emptyList(),
    val buttons: List<ButtonSpec> = listOf(ButtonSpec()),
    val appVolumes: List<AppVolumeSpec> = emptyList(),
)

data class PaletteColor(val key: String, val argb: Long, val name: String)

val PALETTE = listOf(
    PaletteColor("ink", 0xFF000000, "くろ"),
    PaletteColor("brown", 0xFF8B5E3C, "ちゃ"),
    PaletteColor("hot", 0xFFF0283C, "あか"),
    PaletteColor("flame", 0xFFFF6A0A, "あか橙"),
    PaletteColor("amber", 0xFFFFA000, "きいろ"),
    PaletteColor("mint", 0xFF14C873, "みどり"),
    PaletteColor("aqua", 0xFF14B4AA, "みず"),
    PaletteColor("sky", 0xFF1E82F0, "あお"),
    PaletteColor("violet", 0xFF9B5AF5, "すみれ"),
    PaletteColor("magenta", 0xFFF03CA0, "ピンク"),
    PaletteColor("silver", 0xFF828282, "はいいろ"),
    PaletteColor("blue", 0xFF1E88E5, "青"),
    PaletteColor("red", 0xFFE53935, "赤"),
    PaletteColor("green", 0xFF43A047, "緑"),
    PaletteColor("orange", 0xFFFB8C00, "オレンジ"),
    PaletteColor("purple", 0xFF8E24AA, "むらさき"),
    PaletteColor("teal", 0xFF00897B, "青緑"),
    PaletteColor("pink", 0xFFD81B60, "もも"),
    PaletteColor("gray", 0xFF546E7A, "グレー"),
)

data class IconChoice(val key: String, val name: String)

val ICONS = listOf(
    IconChoice("dot", "まる"),
    IconChoice("home", "ホーム"),
    IconChoice("back", "戻る"),
    IconChoice("volume", "音量"),
    IconChoice("camera", "カメラ"),
    IconChoice("flash", "ライト"),
    IconChoice("bell", "通知"),
    IconChoice("lock", "ロック"),
    IconChoice("app", "アプリ"),
    IconChoice("menu", "メニュー"),
)

data class ActionChoice(val type: String, val title: String)

const val TYPE_NONE = "none"
const val TYPE_SCREENSHOT = "screenshot"
const val TYPE_OPEN_APP = "open_app"
const val TYPE_TORCH = "torch"
const val TYPE_VOLUME_UP = "volume_up"
const val TYPE_VOLUME_DOWN = "volume_down"
const val TYPE_VOLUME_PANEL = "volume_panel"
const val TYPE_RINGER = "ringer"
const val TYPE_BACK = "back"
const val TYPE_HOME = "home"
const val TYPE_RECENTS = "recents"
const val TYPE_NOTIFICATIONS = "notifications"
const val TYPE_LOCK = "lock"
const val TYPE_REMEMBER_VOLUME = "remember_volume"
const val TYPE_SHUTTER = "shutter"

val ACTION_CHOICES = listOf(
    ActionChoice(TYPE_NONE, "なし"),
    ActionChoice(TYPE_SCREENSHOT, "スクリーンショット"),
    ActionChoice(TYPE_OPEN_APP, "アプリを開く"),
    ActionChoice(TYPE_TORCH, "ライト ON/OFF"),
    ActionChoice(TYPE_VOLUME_UP, "音量を上げる"),
    ActionChoice(TYPE_VOLUME_DOWN, "音量を下げる"),
    ActionChoice(TYPE_VOLUME_PANEL, "音量パネルを出す"),
    ActionChoice(TYPE_RINGER, "マナーモード切替"),
    ActionChoice(TYPE_BACK, "戻る"),
    ActionChoice(TYPE_HOME, "ホーム"),
    ActionChoice(TYPE_RECENTS, "最近のアプリ"),
    ActionChoice(TYPE_NOTIFICATIONS, "通知を出す"),
    ActionChoice(TYPE_LOCK, "画面ロック"),
    ActionChoice(TYPE_REMEMBER_VOLUME, "この画面の音量を覚える"),
    ActionChoice(TYPE_SHUTTER, "カメラのシャッター"),
)

const val GESTURE_TAP = "tap"
const val GESTURE_DOUBLE = "double"
const val GESTURE_LONG = "long"
const val SHAPE_CIRCLE = "circle"
const val SHAPE_ROBOT = "robot"
const val SHAPE_ORANGE = "b14"

data class ShapeChoice(val key: String, val name: String)

val SHAPE_CHOICES = listOf(
    ShapeChoice(SHAPE_CIRCLE, "まる"),
    ShapeChoice(SHAPE_ROBOT, "ロボット"),
    ShapeChoice("b1", "たま"),
    ShapeChoice("b2", "くも"),
    ShapeChoice("b3", "しかく"),
    ShapeChoice("b4", "ほし"),
    ShapeChoice("b5", "はな"),
    ShapeChoice("b6", "むにゅ"),
    ShapeChoice("b7", "おはな"),
    ShapeChoice("b8", "しずく"),
    ShapeChoice("b9", "まめ"),
    ShapeChoice("b10", "さんかく"),
    ShapeChoice("b11", "ごかく"),
    ShapeChoice("b12", "とげ"),
    ShapeChoice("b13", "はちかく"),
    ShapeChoice(SHAPE_ORANGE, "みかん"),
)

fun defaultButton(index: Int): ButtonSpec {
    val spots = listOf(0.58f, 0.40f, 0.74f, 0.26f, 0.88f)
    return ButtonSpec(
        id = "btn-${index + 1}",
        yFraction = spots.getOrElse(index) { 0.5f },
    )
}

fun sizeDp(key: String): Int = when (key) {
    "s" -> 52
    "l" -> 88
    else -> 68
}

fun actionTitle(action: ActionSpec): String {
    if (action.type == TYPE_OPEN_APP) {
        val name = action.label?.takeIf { it.isNotBlank() } ?: "アプリ"
        return "開く: $name"
    }
    return ACTION_CHOICES.firstOrNull { it.type == action.type }?.title ?: "なし"
}

fun actionShort(action: ActionSpec): String = when (action.type) {
    TYPE_SCREENSHOT -> "撮影"
    TYPE_OPEN_APP -> action.label?.take(4)?.ifBlank { null } ?: "アプリ"
    TYPE_TORCH -> "ライト"
    TYPE_VOLUME_UP -> "大きく"
    TYPE_VOLUME_DOWN -> "小さく"
    TYPE_VOLUME_PANEL -> "音量"
    TYPE_RINGER -> "マナー"
    TYPE_BACK -> "戻る"
    TYPE_HOME -> "ホーム"
    TYPE_RECENTS -> "履歴"
    TYPE_NOTIFICATIONS -> "通知"
    TYPE_LOCK -> "ロック"
    TYPE_REMEMBER_VOLUME -> "覚える"
    TYPE_SHUTTER -> "カメラ"
    else -> "なし"
}

fun paletteArgb(key: String): Long =
    PALETTE.firstOrNull { it.key == key }?.argb ?: PALETTE.first { it.key == "blue" }.argb

private val KNOWN_TYPES = ACTION_CHOICES.map { it.type }.toSet()
private val KNOWN_ICONS = ICONS.map { it.key }.toSet()

fun normalizeAction(action: ActionSpec): ActionSpec {
    if (action.type !in KNOWN_TYPES) return ActionSpec()
    if (action.type == TYPE_OPEN_APP) {
        val pkg = action.packageName?.trim().orEmpty()
        if (pkg.isEmpty()) return ActionSpec()
        val label = action.label?.trim()?.take(40)?.ifEmpty { null }
        return ActionSpec(TYPE_OPEN_APP, pkg, label)
    }
    return ActionSpec(action.type)
}

fun normalize(spec: SettingsSpec): SettingsSpec {
    val source = if (spec.buttons.isEmpty()) listOf(ButtonSpec()) else spec.buttons.take(5)
    val used = mutableSetOf<String>()
    val buttons = source.mapIndexed { index, raw ->
        var id = raw.id.trim().ifEmpty { "btn-${index + 1}" }
        if (!used.add(id)) {
            var n = 2
            var next = "btn-${index + 1}"
            while (!used.add(next)) {
                next = "btn-${index + 1}-$n"
                n++
            }
            id = next
        }
        raw.copy(
            id = id,
            color = if (PALETTE.any { it.key == raw.color }) raw.color else "blue",
            size = if (raw.size == "s" || raw.size == "m" || raw.size == "l") raw.size else "m",
            alpha = raw.alpha.safeAlpha(),
            icon = if (raw.icon in KNOWN_ICONS) raw.icon else "dot",
            label = raw.label.trim().take(4),
            edge = if (raw.edge == "left") "left" else "right",
            yFraction = if (raw.yFraction.isNaN()) 0.58f else raw.yFraction.coerceIn(0.08f, 0.92f),
            shape = if (SHAPE_CHOICES.any { it.key == raw.shape }) raw.shape else SHAPE_CIRCLE,
            tap = normalizeAction(raw.tap),
            doubleTap = normalizeAction(raw.doubleTap),
            longPress = normalizeAction(raw.longPress),
            menu = raw.menu.map(::normalizeAction).filter { it.type != TYPE_NONE }.take(6),
        )
    }
    return spec.copy(
        buttons = buttons,
        appVolumes = normalizeVolumes(spec.appVolumes),
        shutterSpots = normalizeShutterSpots(spec.shutterSpots),
    )
}

fun normalizeVolumes(list: List<AppVolumeSpec>): List<AppVolumeSpec> {
    val map = linkedMapOf<String, AppVolumeSpec>()
    for (item in list) {
        val pkg = item.packageName.trim()
        if (pkg.isEmpty()) continue
        if (isLauncherPackage(pkg, label = item.label)) continue
        map[pkg] = item.copy(
            packageName = pkg,
            label = item.label.trim().ifEmpty { pkg }.take(40),
            volume = item.volume.coerceIn(0, 200),
            carVolume = null,
            devices = normalizeDevices(item.devices, item.carVolume),
        )
    }
    return map.values.toList()
}

private fun Float.safeAlpha(): Float = if (isNaN()) 0.72f else coerceIn(0.35f, 1f)

object SettingsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(spec: SettingsSpec): String = json.encodeToString(normalize(spec))

    fun decode(raw: String?): SettingsSpec {
        if (raw.isNullOrBlank()) return normalize(SettingsSpec())
        return try {
            normalize(json.decodeFromString<SettingsSpec>(raw))
        } catch (_: Exception) {
            normalize(SettingsSpec())
        }
    }
}

data class InstalledApp(val packageName: String, val label: String)

data class AppSuggestion(val packageName: String, val label: String)

private val TVER_PACKAGES = listOf(
    "jp.hamitv.hamiand1",
    "jp.co.tver.mobile",
    "jp.co.tver.tvapp",
)

fun isTver(app: InstalledApp): Boolean {
    if (app.packageName in TVER_PACKAGES) return true
    val label = app.label.trim()
    return label.equals("TVer", ignoreCase = true) ||
        label.startsWith("TVer", ignoreCase = true) ||
        label.equals("ティーバー", ignoreCase = true)
}

fun suggestApps(installed: List<InstalledApp>, savedPackages: Set<String>): List<AppSuggestion> {
    val byPkg = installed.associateBy { it.packageName }
    val out = mutableListOf<AppSuggestion>()
    fun pushKnown(pkg: String) {
        if (pkg in savedPackages || out.any { it.packageName == pkg }) return
        val app = byPkg[pkg] ?: return
        out += AppSuggestion(pkg, app.label.ifBlank { pkg })
    }
    pushKnown("com.google.android.youtube")
    val tver = TVER_PACKAGES.firstNotNullOfOrNull { pkg ->
        installed.find { it.packageName == pkg && pkg !in savedPackages }
    } ?: installed.firstOrNull { it.packageName !in savedPackages && isTver(it) }
    if (tver != null && out.none { it.packageName == tver.packageName }) {
        out += AppSuggestion(tver.packageName, tver.label.ifBlank { "TVer" })
    }
    pushKnown("com.amazon.avod.thirdpartyclient")
    pushKnown("com.amazon.avod")
    pushKnown("com.amazon.amazonvideo.livingroom")
    val prime = installed.firstOrNull { app ->
        app.packageName !in savedPackages &&
            isPrimeVideo(app.packageName, app.label) &&
            out.none { it.packageName == app.packageName }
    }
    if (prime != null) out += AppSuggestion(prime.packageName, prime.label.ifBlank { "Prime Video" })
    pushKnown("com.google.android.apps.youtube.music")
    return out
}

fun rawHasLauncherVolume(raw: String?): Boolean {
    if (raw.isNullOrBlank()) return false
    return try {
        val loose = Json { ignoreUnknownKeys = true; isLenient = true }
        loose.decodeFromString<SettingsSpec>(raw).appVolumes.any {
            isLauncherPackage(it.packageName, label = it.label)
        }
    } catch (_: Exception) {
        false
    }
}

data class RememberResult(
    val ok: Boolean,
    val message: String,
    val updated: List<AppVolumeSpec>,
)

fun normalizeDevices(devices: List<DeviceVolume>, legacyCarVolume: Int?): List<DeviceVolume> {
    val cleaned = linkedMapOf<String, DeviceVolume>()
    for (device in devices) {
        val key = device.key.trim()
        if (key.isEmpty()) continue
        val name = device.name.trim().ifEmpty {
            if (key == LEGACY_BLUETOOTH_KEY) LEGACY_BLUETOOTH_NAME else key
        }.take(40)
        cleaned[key] = DeviceVolume(key, name, device.volume.coerceIn(0, 200))
    }
    if (legacyCarVolume != null && LEGACY_BLUETOOTH_KEY !in cleaned) {
        cleaned[LEGACY_BLUETOOTH_KEY] = DeviceVolume(
            LEGACY_BLUETOOTH_KEY,
            LEGACY_BLUETOOTH_NAME,
            legacyCarVolume.coerceIn(0, 200),
        )
    }
    return cleaned.values.toList()
}

fun volumeForDevice(phone: Int, devices: List<DeviceVolume>, output: OutputSlot): Int {
    if (!output.bluetooth) return phone
    devices.firstOrNull { it.key == output.key }?.let { return it.volume }
    if (output.key != LEGACY_BLUETOOTH_KEY) {
        devices.firstOrNull { it.key == LEGACY_BLUETOOTH_KEY }?.let { return it.volume }
    }
    return phone
}

/** Light button colors get dark eyes so the face stays readable. */
fun prefersDarkInk(argb: Long): Boolean {
    val color = argb.toInt()
    val r = ((color ushr 16) and 0xFF) / 255.0
    val g = ((color ushr 8) and 0xFF) / 255.0
    val b = (color and 0xFF) / 255.0
    val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return luminance >= 0.55
}

fun rememberVolume(
    packageName: String?,
    label: String?,
    currentVolume: Int,
    existing: List<AppVolumeSpec>,
    filter: FilterConfig,
    output: OutputSlot = PHONE_SLOT,
): RememberResult {
    if (packageName == null || isLauncherPackage(packageName, filter.launcherPackages, label)) {
        return RememberResult(false, "いま開いているアプリが分かりませんでした", existing)
    }
    if (PackageFilter.isNoise(packageName, filter)) {
        return RememberResult(false, "この画面のアプリは覚えられません", existing)
    }
    val name = (label?.trim()?.takeIf { it.isNotEmpty() } ?: packageName).take(18)
    val level = currentVolume.coerceIn(0, 200)
    val previous = existing.find { it.packageName == packageName }
    val device = DeviceVolume(output.key, output.name, level)
    val saved = when {
        previous == null && output.bluetooth ->
            AppVolumeSpec(packageName, name, level, devices = listOf(device))
        previous == null ->
            AppVolumeSpec(packageName, name, level)
        output.bluetooth ->
            previous.copy(
                label = name,
                devices = previous.devices.filter { it.key != output.key } + device,
            )
        else -> previous.copy(label = name, volume = level)
    }
    val next = existing.filter { it.packageName != packageName } + saved
    val message = "$name：${output.name} の音量 $level を覚えました"
    return RememberResult(true, message, next)
}
