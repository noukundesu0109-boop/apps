package com.nossam.smahobutton.logic

data class ShutterNode(
    val text: String? = null,
    val description: String? = null,
    val viewId: String? = null,
    val clickable: Boolean = false,
    val children: List<ShutterNode> = emptyList(),
)

data class ShutterHit(val node: ShutterNode, val score: Int)

private val KNOWN_CAMERAS = setOf(
    "com.google.android.GoogleCamera",
    "com.android.camera",
    "com.android.camera2",
    "com.sec.android.app.camera",
    "com.huawei.camera",
    "com.hihonor.camera",
    "com.oneplus.camera",
    "com.oppo.camera",
    "com.mediatek.camera",
    "org.codeaurora.snapcam",
    "com.motorola.camera2",
    "com.sonyericsson.android.camera",
)

fun isCameraPackage(packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    if (packageName in KNOWN_CAMERAS) return true
    val lower = packageName.lowercase()
    return "camera" in lower && "smahobutton" !in lower
}

fun shutterScore(text: String?, description: String?, viewId: String?, clickable: Boolean): Int {
    val id = viewId?.lowercase().orEmpty()
    val label = "${text.orEmpty()} ${description.orEmpty()}"
    val lower = label.lowercase()
    val switchesCamera = "切り替え" in label || ("switch" in lower && "shutter" !in id)
    if (switchesCamera) return 0
    var score = 0
    if ("shutter" in id || "capture_button" in id) score += 100
    if (listOf("シャッター", "写真を撮る", "写真を撮影", "shutter", "capture").any { it in lower || it in id }) {
        score += 40
    }
    if ("撮影" in label && "モード" !in label) score += 30
    if (clickable && score > 0) score += 20
    return score
}

fun findShutter(root: ShutterNode): ShutterHit? {
    var best: ShutterHit? = null
    fun walk(node: ShutterNode) {
        val score = shutterScore(node.text, node.description, node.viewId, node.clickable)
        if (score >= 40 && (best == null || score > best!!.score)) {
            best = ShutterHit(node, score)
        }
        node.children.forEach(::walk)
    }
    walk(root)
    return best
}

fun orientationKey(portrait: Boolean): String = if (portrait) "portrait" else "landscape"

fun shutterFractions(
    spots: List<ShutterSpot>,
    packageName: String,
    portrait: Boolean,
): Pair<Float, Float> {
    val key = orientationKey(portrait)
    val saved = spots.find { it.packageName == packageName && it.orientation == key }
    if (saved != null) return saved.x to saved.y
    return if (portrait) 0.50f to 0.88f else 0.88f to 0.50f
}

fun normalizeShutterSpots(spots: List<ShutterSpot>): List<ShutterSpot> {
    val map = linkedMapOf<String, ShutterSpot>()
    for (spot in spots) {
        val pkg = spot.packageName.trim()
        val orient = if (spot.orientation == "landscape") "landscape" else "portrait"
        if (pkg.isEmpty()) continue
        val x = if (spot.x.isNaN()) 0.5f else spot.x.coerceIn(0.02f, 0.98f)
        val y = if (spot.y.isNaN()) 0.88f else spot.y.coerceIn(0.02f, 0.98f)
        map["$pkg:$orient"] = ShutterSpot(pkg, orient, x, y)
    }
    return map.values.toList()
}
