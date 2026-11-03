package com.nossam.smahobutton.logic

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePlanTest {
    private val prefix = Regex("^smahobutton-backup-")

    @Test
    fun keepsTheNewFileAndTrashesOnlyThisAppsOlderBackups() {
        val files = listOf(
            DriveFile("new", "smahobutton-backup-20261103.json"),
            DriveFile("old", "smahobutton-backup-20261101.json"),
            DriveFile("task", "tasks-backup-20261102.json"),
            DriveFile("note", "memo.txt"),
        )
        assertEquals(listOf("old"), DrivePlan.idsToTrash(files, prefix, "new"))
    }

    @Test
    fun doesNotTrashAnythingUntilTheUploadIdIsKnown() {
        val files = listOf(DriveFile("old", "smahobutton-backup-20261101.json"))
        assertTrue(DrivePlan.idsToTrash(files, prefix, "").isEmpty())
    }

    @Test
    fun driveFileNameIsTheDateOnly() {
        assertEquals("smahobutton-backup-20261103.json", backupDriveName(LocalDate.of(2026, 11, 3)))
    }

    @Test
    fun unregisteredAndCancelMessagesAreDistinct() {
        assertEquals("unregistered", DriveMessages.noticeForStatus(10).reason)
        assertEquals(DriveMessages.UNREGISTERED, DriveMessages.noticeForStatus(10).message)
        assertEquals(DriveMessages.UNREGISTERED, DriveMessages.noticeForStatus(12500).message)
        assertEquals(DriveMessages.CANCEL, DriveMessages.noticeForStatus(16).message)
        assertEquals(DriveMessages.CANCEL, DriveMessages.noticeForStatus(12501).message)
        assertEquals(DriveMessages.OFFLINE, DriveMessages.noticeForStatus(7).message)
        assertEquals(DriveMessages.PLAY, DriveMessages.noticeForStatus(2).message)
        assertEquals(DriveMessages.GENERIC, DriveMessages.noticeForStatus(500).message)
    }

    @Test
    fun driveApiDisabledIsCalledOut() {
        val body = """{"error":{"errors":[{"reason":"accessNotConfigured"}]}}"""
        assertEquals(DriveMessages.DRIVE_API, DriveMessages.httpFailure(403, body).message)
        assertEquals(DriveMessages.GENERIC, DriveMessages.httpFailure(401, "invalid").message)
        assertEquals(DriveMessages.GENERIC, DriveMessages.httpFailure(500, "nope").message)
    }

    @Test
    fun multipartNamesTheFileInsideTheGrokFolder() {
        val body = DrivePlan.multipartBody(
            "smahobutton-backup-20261103.json",
            "folder-1",
            """{"app":"smahobutton"}""",
            "bound",
        ).toString(Charsets.UTF_8)
        val metaText = body.substringAfter("\r\n\r\n").substringBefore("\r\n--bound")
        val meta = Json.parseToJsonElement(metaText).jsonObject
        assertEquals("smahobutton-backup-20261103.json", meta.getValue("name").jsonPrimitive.content)
        assertEquals("application/json", meta.getValue("mimeType").jsonPrimitive.content)
        assertEquals("folder-1", meta.getValue("parents").jsonArray[0].jsonPrimitive.content)
        assertTrue(body.contains("""{"app":"smahobutton"}"""))
        assertTrue(body.trimEnd().endsWith("--bound--"))
        assertEquals("multipart/related; boundary=bound", DrivePlan.contentType("bound"))
    }

    @Test
    fun successMentionsTheTrashCountOnlyAfterARealUpload() {
        assertEquals(
            "Googleドライブの「Grok」に保存しました：smahobutton-backup-20261103.json。古いバックアップを2件ゴミ箱へ移しました。",
            DriveMessages.success("smahobutton-backup-20261103.json", cleaned = 2, unverified = false, cleanErr = false),
        )
        assertTrue(DriveMessages.success("a.json", 0, unverified = true, cleanErr = false).contains("削除していません"))
        assertTrue(DriveMessages.success("a.json", 0, unverified = false, cleanErr = true).contains("整理はできませんでした"))
    }
}
