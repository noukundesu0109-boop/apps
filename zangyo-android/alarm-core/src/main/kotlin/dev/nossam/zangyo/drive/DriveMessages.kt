package dev.nossam.zangyo.drive

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

data class DriveFile(val id: String, val name: String, val modifiedTime: String = "")

data class SavePlan(val updateId: String?, val trashIds: List<String>)

data class DriveNotice(val reason: String, val message: String)

object DriveMessages {
    const val CANCEL = "Googleへのログインをキャンセルしました"
    const val UNREGISTERED = "Google Cloud に Android の登録がまだです"
    const val OFFLINE = "いまはオフラインのため、Googleドライブに保存できません。通信できる場所でもう一度試してください。"
    const val GENERIC = "Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。"
    const val PLAY = "Google Play開発者サービスを更新してから、もう一度試してください。"
    const val DRIVE_API = "Google Cloud で Drive API がまだ有効になっていません"
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"
    const val NONE = "このアプリでドライブに保存したバックアップがありません。先に「ドライブに保存」をしてください。"
    const val RESTORE_OFFLINE = "いまはオフラインのため、Googleドライブから戻せません。通信できる場所でもう一度試してください。"
    const val RESTORE_GENERIC = "Googleドライブから戻せませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。"
    const val RESTORE_BAD = "このバックアップは読み取れません。このアプリのファイルか確認してください。"
    const val CONFIRM = "今のデータをこのバックアップで置きかえます。よろしいですか？"

    fun noticeForStatus(code: Int): DriveNotice = when (code) {
        16, 12501 -> DriveNotice("cancel", CANCEL)
        10, 12500 -> DriveNotice("unregistered", UNREGISTERED)
        7 -> DriveNotice("offline", OFFLINE)
        1, 2, 3, 9 -> DriveNotice("play", PLAY)
        else -> DriveNotice("error", GENERIC)
    }

    fun noticeForRestore(code: Int): DriveNotice = when (code) {
        16, 12501 -> DriveNotice("cancel", CANCEL)
        10, 12500 -> DriveNotice("unregistered", UNREGISTERED)
        7 -> DriveNotice("offline", RESTORE_OFFLINE)
        1, 2, 3, 9 -> DriveNotice("play", PLAY)
        else -> DriveNotice("error", RESTORE_GENERIC)
    }

    fun httpFailure(code: Int, body: String, restoring: Boolean = false): DriveNotice {
        val lower = body.lowercase()
        if (
            "accessnotconfigured" in lower ||
            "drive api has not been used" in lower ||
            "service_disabled" in lower
        ) {
            return DriveNotice("drive-api", DRIVE_API)
        }
        return DriveNotice("error", if (restoring) RESTORE_GENERIC else GENERIC)
    }
}

object DrivePlan {
    /** Keep the file just uploaded. Trash every other file of this app. */
    fun idsToTrash(files: List<DriveFile>, namePrefix: Regex, keepId: String): List<String> {
        if (keepId.isBlank()) return emptyList()
        return files.mapNotNull { file ->
            if (file.id.isBlank() || file.id == keepId) return@mapNotNull null
            if (!namePrefix.containsMatchIn(file.name)) return@mapNotNull null
            file.id
        }
    }

    fun multipartBody(name: String, folderId: String, content: String, boundary: String): ByteArray {
        val meta = JSONObject()
            .put("name", name)
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put(folderId))
            .toString()
        return multipart(meta, content, boundary)
    }

    fun contentType(boundary: String) = "multipart/related; boundary=$boundary"

    /** Update this file if one already exists. Trash every other backup of this app. */
    fun savePlan(files: List<DriveFile>, namePrefix: Regex): SavePlan {
        val keep = newest(files, namePrefix) ?: return SavePlan(null, emptyList())
        return SavePlan(keep.id, idsToTrash(files, namePrefix, keep.id))
    }

    fun newest(files: List<DriveFile>, namePrefix: Regex): DriveFile? {
        return files
            .filter { it.id.isNotBlank() && namePrefix.containsMatchIn(it.name) }
            .maxWithOrNull(compareBy<DriveFile> { it.modifiedTime }.thenBy { it.name }.thenBy { it.id })
    }

    /** 「2026年11月4日 19:21」 in Japan time. The file name is the fallback. */
    fun savedLabel(modifiedTime: String?, name: String = ""): String {
        if (!modifiedTime.isNullOrBlank()) {
            try {
                val zoned = Instant.parse(modifiedTime).atZone(ZONE)
                return "${zoned.year}年${zoned.monthValue}月${zoned.dayOfMonth}日 ${zoned.hour}:${zoned.minute.toString().padStart(2, '0')}"
            } catch (_: Exception) {
            }
        }
        val match = NAME_DATE.find(name) ?: return "日時不明"
        val month = match.groupValues[2].toIntOrNull() ?: return "日時不明"
        val day = match.groupValues[3].toIntOrNull() ?: return "日時不明"
        if (month !in 1..12 || day !in 1..31) return "日時不明"
        return "${match.groupValues[1].toInt()}年${month}月${day}日"
    }

    fun replaceBody(name: String, content: String, boundary: String): ByteArray {
        val meta = JSONObject()
            .put("name", name)
            .put("mimeType", "application/json")
            .toString()
        return multipart(meta, content, boundary)
    }

    /** A zangyo backup has a days object and an adjust object. */
    fun looksLikeBackup(text: String): Boolean {
        if (text.isBlank() || text.length > 8_000_000) return false
        val root = try {
            JSONObject(text)
        } catch (_: Exception) {
            return false
        }
        return root.opt("days") is JSONObject && root.opt("adjust") is JSONObject
    }

    private fun multipart(meta: String, content: String, boundary: String): ByteArray {
        val text = buildString {
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(meta).append("\r\n")
            append("--").append(boundary).append("\r\n")
            append("Content-Type: application/json\r\n\r\n")
            append(content).append("\r\n")
            append("--").append(boundary).append("--")
        }
        return text.toByteArray(Charsets.UTF_8)
    }

    private val ZONE: ZoneId = ZoneId.of("Asia/Tokyo")
    private val NAME_DATE = Regex("""(\d{4})(\d{2})(\d{2})""")
}
