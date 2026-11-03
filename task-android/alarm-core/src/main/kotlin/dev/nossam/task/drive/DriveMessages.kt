package dev.nossam.task.drive

import org.json.JSONArray
import org.json.JSONObject

data class DriveFile(val id: String, val name: String)

data class DriveNotice(val reason: String, val message: String)

object DriveMessages {
    const val CANCEL = "Googleへのログインをキャンセルしました"
    const val UNREGISTERED = "Google Cloud に Android の登録がまだです"
    const val OFFLINE = "いまはオフラインのため、Googleドライブに保存できません。通信できる場所でもう一度試してください。"
    const val GENERIC = "Googleドライブに保存できませんでした。通信状態やGoogleの許可を確認して、もう一度試してください。だめなときは「バックアップを保存」を使ってください。"
    const val PLAY = "Google Play開発者サービスを更新してから、もう一度試してください。"
    const val DRIVE_API = "Google Cloud で Drive API がまだ有効になっていません"
    const val SCOPE = "https://www.googleapis.com/auth/drive.file"

    fun noticeForStatus(code: Int): DriveNotice = when (code) {
        16, 12501 -> DriveNotice("cancel", CANCEL)
        10, 12500 -> DriveNotice("unregistered", UNREGISTERED)
        7 -> DriveNotice("offline", OFFLINE)
        1, 2, 3, 9 -> DriveNotice("play", PLAY)
        else -> DriveNotice("error", GENERIC)
    }

    fun httpFailure(code: Int, body: String): DriveNotice {
        val lower = body.lowercase()
        if (
            "accessnotconfigured" in lower ||
            "drive api has not been used" in lower ||
            "service_disabled" in lower
        ) {
            return DriveNotice("drive-api", DRIVE_API)
        }
        return DriveNotice("error", GENERIC)
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

    fun contentType(boundary: String) = "multipart/related; boundary=$boundary"
}
