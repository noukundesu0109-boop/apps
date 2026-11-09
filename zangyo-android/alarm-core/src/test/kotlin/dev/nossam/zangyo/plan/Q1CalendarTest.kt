package dev.nossam.zangyo.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Q1CalendarTest {
    @Test
    fun januaryThroughMarchFillsOnlyBlankDaysAndCovers2026() {
        val root = repoRoot()
        val html = File(root, "zangyo-app/index.html")
        val fixture = File(root, "zangyo-android/alarm-core/src/test/resources/zangyo-2026-q1-shifts.json")
        val script = File(root, "zangyo-android/alarm-core/src/test/resources/q1-migration-check.js")
        assertTrue(html.isFile)
        val process = ProcessBuilder("node", script.absolutePath, html.absolutePath, fixture.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.waitFor())
        assertTrue(output.contains("2026"))
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        repeat(6) {
            if (File(dir, "zangyo-app/index.html").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }
}
