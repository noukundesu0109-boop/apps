package com.nossam.smahobutton.logic

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
    val NAME_PREFIX = Regex("^smahobutton-backup-")

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

    fun success(name: String, cleaned: Int, unverified: Boolean, cleanErr: Boolean): String = buildString {
        append("Googleドライブの「Grok」に保存しました")
        if (name.isNotBlank()) append("：").append(name)
        when {
            unverified -> append("（確認ができなかったため、古いバックアップは削除していません）")
            cleanErr -> append("。古いバックアップの整理はできませんでした。")
            cleaned > 0 -> append("。古いバックアップを").append(cleaned).append("件ゴミ箱へ移しました。")
        }
    }
}

object DrivePlan {
    /** Keep the file just uploaded. Trash every other backup of this app. */
    fun idsToTrash(files: List<DriveFile>, namePrefix: Regex, keepId: String): List<String> {
        if (keepId.isBlank()) return emptyList()
        return files.mapNotNull { file ->
            if (file.id.isBlank() || file.id == keepId) return@mapNotNull null
            if (!namePrefix.containsMatchIn(file.name)) return@mapNotNull null
            file.id
        }
    }

    fun multipartBody(name: String, folderId: String, content: String, boundary: String): ByteArray {
        val meta = buildString {
            append("{\"name\":").append(jsonString(name))
            append(",\"mimeType\":\"application/json\"")
            append(",\"parents\":[").append(jsonString(folderId)).append("]}")
        }
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

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(char)
            }
        }
        append('"')
    }
}
