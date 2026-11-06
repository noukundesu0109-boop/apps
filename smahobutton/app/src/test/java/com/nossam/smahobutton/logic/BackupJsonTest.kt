package com.nossam.smahobutton.logic

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonTest {
    @Test
    fun fileNameUsesLocalDateAndTime() {
        val name = backupFileName(LocalDateTime.of(2026, 11, 3, 18, 5))
        assertEquals("smahobutton-backup-20261103-1805.json", name)
    }

    @Test
    fun roundTripKeepsButtonsVolumesAndToggles() {
        val original = SettingsSpec(
            enabled = true,
            restoreVolumeOnLeave = false,
            buttons = listOf(
                ButtonSpec(
                    id = "btn-1",
                    color = "pink",
                    size = "s",
                    alpha = 0.5f,
                    icon = "home",
                    label = "家",
                    edge = "left",
                    yFraction = 0.25f,
                    useMenu = true,
                    tap = ActionSpec(TYPE_HOME),
                    doubleTap = ActionSpec(TYPE_BACK),
                    longPress = ActionSpec(TYPE_LOCK),
                    menu = listOf(ActionSpec(TYPE_OPEN_APP, "com.google.android.youtube", "YouTube")),
                ),
                defaultButton(1).copy(edge = "right", yFraction = 0.75f, tap = ActionSpec(TYPE_SCREENSHOT)),
            ),
            appVolumes = listOf(
                AppVolumeSpec("com.google.android.youtube", "YouTube", 4),
                AppVolumeSpec("jp.hamitv.hamiand1", "TVer", 8),
            ),
        )
        val again = readBackup(encodeBackup(original))
        assertTrue(again is BackupRead.Ok)
        assertEquals(normalize(original), (again as BackupRead.Ok).settings)
        assertTrue(encodeBackup(original).contains("\"schema\": 1"))
    }

    @Test
    fun wrongFilesGetAFriendlyError() {
        assertTrue(readBackup(null) is BackupRead.Error)
        assertTrue(readBackup("   ") is BackupRead.Error)
        assertEquals("スマホボタンの控えではありません", (readBackup("hello") as BackupRead.Error).message)
        assertEquals(
            "スマホボタンの控えではありません",
            (readBackup("""{"enabled":true}""") as BackupRead.Error).message,
        )
        assertEquals(
            "スマホボタンの控えではありません",
            (readBackup("""{"schema":1,"app":"other","settings":{}}""") as BackupRead.Error).message,
        )
        assertEquals(
            "この控えは、今のアプリでは読めません",
            (readBackup("""{"schema":99,"app":"smahobutton","settings":{}}""") as BackupRead.Error).message,
        )
    }
}
