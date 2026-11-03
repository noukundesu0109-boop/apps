package dev.nossam.zangyo

import dev.nossam.zangyo.drive.DriveFile
import dev.nossam.zangyo.drive.DriveMessages
import dev.nossam.zangyo.drive.DrivePlan
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

internal class DriveFailure(val notice: dev.nossam.zangyo.drive.DriveNotice) : Exception(notice.message)

internal object DriveClient {
    private const val FILES = "https://www.googleapis.com/drive/v3/files"
    private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3/files"

    fun save(token: String, name: String, content: String, namePrefix: Regex): JSONObject {
        val folder = grokFolder(token)
        val uploaded = upload(token, folder, name, content)
        val id = uploaded.optString("id")
        val result = JSONObject()
            .put("ok", true)
            .put("name", uploaded.optString("name").ifBlank { name })
        if (id.isBlank()) return result.put("cleaned", 0).put("unverified", true)
        return try {
            val trash = DrivePlan.idsToTrash(listFiles(token, folder), namePrefix, id)
            var cleaned = 0
            var failed = false
            for (old in trash) {
                if (trashFile(token, old)) cleaned += 1 else failed = true
            }
            result.put("cleaned", cleaned)
            if (failed) result.put("cleanErr", true)
            result
        } catch (_: Exception) {
            result.put("cleaned", 0).put("cleanErr", true)
        }
    }

    private fun grokFolder(token: String): String {
        val query = "name='Grok' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val listed = getJson(token, "$FILES?q=${enc(query)}&fields=files(id,name)&spaces=drive&pageSize=10")
        val files = listed.optJSONArray("files")
        if (files != null && files.length() > 0) {
            val id = files.optJSONObject(0)?.optString("id").orEmpty()
            if (id.isNotBlank()) return id
        }
        val created = postJson(
            token,
            FILES,
            JSONObject()
                .put("name", "Grok")
                .put("mimeType", "application/vnd.google-apps.folder")
                .toString(),
        )
        val id = created.optString("id")
        if (id.isBlank()) throw DriveFailure(DriveMessages.noticeForStatus(-1))
        return id
    }

    private fun upload(token: String, folderId: String, name: String, content: String): JSONObject {
        val boundary = "drive_${UUID.randomUUID()}"
        val body = DrivePlan.multipartBody(name, folderId, content, boundary)
        val text = call(
            token,
            "$UPLOAD?uploadType=multipart&fields=id,name,parents",
            "POST",
            body,
            DrivePlan.contentType(boundary),
            null,
        )
        return JSONObject(text.ifBlank { "{}" })
    }

    private fun listFiles(token: String, folderId: String): List<DriveFile> {
        val query = "'$folderId' in parents and trashed=false"
        val out = ArrayList<DriveFile>()
        var page: String? = null
        repeat(20) {
            val url = buildString {
                append(FILES)
                append("?q=").append(enc(query))
                append("&orderBy=createdTime%20desc&fields=nextPageToken,files(id,name)&spaces=drive&pageSize=100")
                if (!page.isNullOrBlank()) append("&pageToken=").append(enc(page!!))
            }
            val listed = getJson(token, url)
            val files = listed.optJSONArray("files")
            if (files != null) {
                for (i in 0 until files.length()) {
                    val item = files.optJSONObject(i) ?: continue
                    out += DriveFile(id = item.optString("id"), name = item.optString("name"))
                }
            }
            page = listed.optString("nextPageToken").takeIf { it.isNotBlank() } ?: return out
        }
        return out
    }

    private fun trashFile(token: String, id: String): Boolean {
        return try {
            call(
                token,
                "$FILES/${enc(id)}",
                "POST",
                """{"trashed":true}""".toByteArray(Charsets.UTF_8),
                "application/json",
                "PATCH",
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getJson(token: String, url: String): JSONObject = JSONObject(call(token, url, "GET", null, null, null).ifBlank { "{}" })

    private fun postJson(token: String, url: String, json: String): JSONObject {
        val text = call(token, url, "POST", json.toByteArray(Charsets.UTF_8), "application/json", null)
        return JSONObject(text.ifBlank { "{}" })
    }

    private fun call(
        token: String,
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        override: String?,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (override != null) setRequestProperty("X-HTTP-Method-Override", override)
            if (body != null) {
                doOutput = true
                if (contentType != null) setRequestProperty("Content-Type", contentType)
            }
        }
        try {
            if (body != null) conn.outputStream.use { it.write(body) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) throw DriveFailure(DriveMessages.httpFailure(code, text))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
