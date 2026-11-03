package dev.nossam.zangyo.drive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePlanTest {
    private val prefix = Regex("^zangyo-backup-")

    @Test
    fun keepsTheNewFileAndTrashesOnlyThisAppsOlderBackups() {
        val files = listOf(
            DriveFile("new", "zangyo-backup-20261103.json"),
            DriveFile("old", "zangyo-backup-20261101.json"),
            DriveFile("tasks", "tasks-backup-20261102.json"),
            DriveFile("note", "memo.txt"),
        )
        assertEquals(listOf("old"), DrivePlan.idsToTrash(files, prefix, "new"))
    }

    @Test
    fun doesNotTrashAnythingUntilTheUploadIdIsKnown() {
        val files = listOf(DriveFile("old", "zangyo-backup-20261101.json"))
        assertTrue(DrivePlan.idsToTrash(files, prefix, "").isEmpty())
    }

    @Test
    fun unregisteredAndCancelMessagesAreDistinct() {
        assertEquals("unregistered", DriveMessages.noticeForStatus(10).reason)
        assertEquals(DriveMessages.UNREGISTERED, DriveMessages.noticeForStatus(10).message)
        assertEquals(DriveMessages.UNREGISTERED, DriveMessages.noticeForStatus(12500).message)
        assertEquals(DriveMessages.CANCEL, DriveMessages.noticeForStatus(16).message)
        assertEquals(DriveMessages.CANCEL, DriveMessages.noticeForStatus(12501).message)
        assertEquals(DriveMessages.OFFLINE, DriveMessages.noticeForStatus(7).message)
        assertEquals(DriveMessages.PLAY, DriveMessages.noticeForStatus(1).message)
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
            "zangyo-backup-20261103.json",
            "folder-1",
            """{"app":"zangyo"}""",
            "bound",
        ).toString(Charsets.UTF_8)
        val meta = JSONObject(body.substringAfter("\r\n\r\n").substringBefore("\r\n--bound"))
        assertEquals("zangyo-backup-20261103.json", meta.getString("name"))
        assertEquals("application/json", meta.getString("mimeType"))
        assertEquals("folder-1", meta.getJSONArray("parents").getString(0))
        assertTrue(body.contains("""{"app":"zangyo"}"""))
        assertTrue(body.trimEnd().endsWith("--bound--"))
        assertEquals("multipart/related; boundary=bound", DrivePlan.contentType("bound"))
    }
}
