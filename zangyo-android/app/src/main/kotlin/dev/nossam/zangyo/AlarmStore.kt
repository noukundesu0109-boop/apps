package dev.nossam.zangyo

import android.content.Context
import org.json.JSONObject
import java.io.File

internal data class StoredSnooze(
    val id: String,
    val atMillis: Long,
    val title: String,
    val detail: String,
)

internal class AlarmStore(context: Context) {
    private val dir = context.filesDir

    fun readWork(): String? = read(file("work-state.json"))

    fun writeWork(json: String) = write(file("work-state.json"), json)

    fun readSnoozes(): List<StoredSnooze> {
        val text = read(file("snoozes.json")) ?: return emptyList()
        return try {
            val root = JSONObject(text)
            val array = root.optJSONArray("items") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    val at = item.optLong("at", 0L)
                    if (id.isBlank() || at <= 0L) continue
                    add(
                        StoredSnooze(
                            id = id,
                            atMillis = at,
                            title = item.optString("title").ifBlank { "アラーム" },
                            detail = item.optString("detail"),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun writeSnoozes(items: List<StoredSnooze>) {
        val array = org.json.JSONArray()
        for (item in items) {
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("at", item.atMillis)
                    .put("title", item.title)
                    .put("detail", item.detail),
            )
        }
        write(file("snoozes.json"), JSONObject().put("items", array).toString())
    }

    fun readCodes(): Map<String, Int> {
        val text = read(file("alarm-codes.json")) ?: return emptyMap()
        return try {
            val ids = JSONObject(text).optJSONObject("ids") ?: return emptyMap()
            buildMap {
                val keys = ids.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val code = ids.optInt(key, 0)
                    if (code > 0) put(key, code)
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun writeCodes(codes: Map<String, Int>) {
        val ids = JSONObject()
        for ((id, code) in codes) ids.put(id, code)
        write(file("alarm-codes.json"), JSONObject().put("ids", ids).toString())
    }

    private fun file(name: String) = File(dir, name)

    private fun read(file: File): String? = if (file.isFile) file.readText() else null

    private fun write(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }
}
