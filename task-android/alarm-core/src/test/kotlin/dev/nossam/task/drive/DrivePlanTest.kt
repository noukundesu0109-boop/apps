package dev.nossam.task.drive

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePlanTest {
    private val prefix = Regex("^tasks-backup-")

    @Test
    fun keepsTheNewFileAndTrashesOnlyThisAppsOlderBackups() {
        val files = listOf(
            DriveFile("new", "tasks-backup-20261103.json"),
            DriveFile("old", "tasks-backup-20261101.json"),
            DriveFile("zangyo", "zangyo-backup-20261102.json"),
            DriveFile("note", "memo.txt"),
        )
        assertEquals(listOf("old"), DrivePlan.idsToTrash(files, prefix, "new"))
    }

    @Test
    fun doesNotTrashAnythingUntilTheUploadIdIsKnown() {
        val files = listOf(DriveFile("old", "tasks-backup-20261101.json"))
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
            "tasks-backup-20261103.json",
            "folder-1",
            """{"app":"tasks"}""",
            "bound",
        ).toString(Charsets.UTF_8)
        val meta = JSONObject(body.substringAfter("\r\n\r\n").substringBefore("\r\n--bound"))
        assertEquals("tasks-backup-20261103.json", meta.getString("name"))
        assertEquals("application/json", meta.getString("mimeType"))
        assertEquals("folder-1", meta.getJSONArray("parents").getString(0))
        assertTrue(body.contains("""{"app":"tasks"}"""))
        assertTrue(body.trimEnd().endsWith("--bound--"))
        assertEquals("multipart/related; boundary=bound", DrivePlan.contentType("bound"))
    }

    @Test
    fun saveReplacesTheNewestBackupAndTrashesTheRest() {
        val files = listOf(
            DriveFile("old", "tasks-backup-20261101.json", "2026-11-01T00:00:00.000Z"),
            DriveFile("newer", "tasks-backup-20261102.json", "2026-11-04T00:00:00.000Z"),
            DriveFile("later-name", "tasks-backup-20261110.json", "2026-11-01T00:00:00.000Z"),
            DriveFile("zangyo", "zangyo-backup-20261104.json", "2026-11-05T00:00:00.000Z"),
        )
        val plan = DrivePlan.savePlan(files, prefix)
        assertEquals("newer", plan.updateId)
        assertEquals(listOf("old", "later-name"), plan.trashIds)
        assertEquals(null, DrivePlan.savePlan(emptyList(), prefix).updateId)
    }

    @Test
    fun savedTimeIsShownInJapanAndOnlyThisAppsBackupIsAccepted() {
        assertEquals("2026年11月4日 19:21", DrivePlan.savedLabel("2026-11-04T10:21:00.000Z", "tasks-backup-20261103.json"))
        assertEquals("2026年11月5日 0:30", DrivePlan.savedLabel("2026-11-04T15:30:00Z"))
        assertEquals("2026年11月3日", DrivePlan.savedLabel("", "tasks-backup-20261103.json"))
        assertEquals("日時不明", DrivePlan.savedLabel(null, "note.txt"))
        assertTrue(DrivePlan.looksLikeBackup("""{"cleaning":[],"bousai":[],"nextTaskNotice":true,"doneOnStop":false}"""))
        assertTrue(!DrivePlan.looksLikeBackup("""{"days":{},"adjust":{}}"""))
        assertTrue(!DrivePlan.looksLikeBackup("{"))
        assertEquals(DriveMessages.NONE, "このアプリでドライブに保存したバックアップがありません。先に「ドライブに保存」をしてください。")
        assertEquals(DriveMessages.CONFIRM, "今のデータをこのバックアップで置きかえます。よろしいですか？")
        assertEquals(DriveMessages.RESTORE_OFFLINE, DriveMessages.noticeForRestore(7).message)
        assertEquals(DriveMessages.CANCEL, DriveMessages.noticeForRestore(16).message)
        assertEquals(DriveMessages.RESTORE_GENERIC, DriveMessages.noticeForRestore(500).message)
    }
}
