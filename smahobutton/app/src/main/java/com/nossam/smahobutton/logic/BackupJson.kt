package com.nossam.smahobutton.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull

const val BACKUP_SCHEMA = 1
const val BACKUP_APP = "smahobutton"

sealed class BackupRead {
    data class Ok(val settings: SettingsSpec) : BackupRead()
    data class Error(val message: String) : BackupRead()
}

private val backupJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
    prettyPrint = true
}

private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
private val dayStamp = DateTimeFormatter.ofPattern("yyyyMMdd")

fun backupFileName(time: LocalDateTime): String = "smahobutton-backup-${time.format(stamp)}.json"

fun backupDriveName(day: LocalDate): String = "smahobutton-backup-${day.format(dayStamp)}.json"

fun encodeBackup(spec: SettingsSpec): String {
    val root = JsonObject(
        mapOf(
            "schema" to JsonPrimitive(BACKUP_SCHEMA),
            "app" to JsonPrimitive(BACKUP_APP),
            "settings" to backupJson.encodeToJsonElement(normalize(spec)),
        ),
    )
    return backupJson.encodeToString(JsonObject.serializer(), root)
}

fun readBackup(raw: String?): BackupRead {
    if (raw.isNullOrBlank()) return BackupRead.Error("ファイルが空です")
    val root = try {
        backupJson.parseToJsonElement(raw) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return BackupRead.Error("スマホボタンの控えではありません")
    val app = (root["app"] as? JsonPrimitive)?.contentOrNull
    if (app != BACKUP_APP) return BackupRead.Error("スマホボタンの控えではありません")
    val schema = (root["schema"] as? JsonPrimitive)?.intOrNull
        ?: return BackupRead.Error("スマホボタンの控えではありません")
    if (schema != BACKUP_SCHEMA) return BackupRead.Error("この控えは、今のアプリでは読めません")
    val settingsElement = root["settings"] ?: return BackupRead.Error("スマホボタンの控えではありません")
    val settings = try {
        normalize(backupJson.decodeFromJsonElement<SettingsSpec>(settingsElement))
    } catch (_: Exception) {
        return BackupRead.Error("スマホボタンの控えではありません")
    }
    return BackupRead.Ok(settings)
}
