package dev.nossam.zangyo.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

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

    @Test
    fun leaveDaysAreOnlyAnnualLeaveAnd2026StillCoversTheYear() {
        val html = File(repoRoot(), "zangyo-app/index.html").readText()
        val shifts = Regex(""""(\d{4}-\d{2}-\d{2})":`(\w+)`""")
            .findAll(html.substringAfter("var Ue={").substringBefore("},We=e(("))
            .associate { it.groupValues[1] to it.groupValues[2] }
        val leave = Regex("""leave:`([^`]+)`\.split""")
            .find(html)!!
            .groupValues[1]
            .split('.')
            .filter { runCatching { LocalDate.parse(it) }.isSuccess }
            .toSet()
        val counts = AlarmPlanner.countYear(
            WorkData(shifts, emptySet(), leave, emptyMap<String, List<AlarmRow>>()),
            2026,
        )
        assertEquals(110, counts.day)
        assertEquals(124, counts.night)
        assertEquals(234, counts.attended)
        assertEquals(121, counts.off)
        assertEquals(10, counts.leave)
        assertEquals(365, counts.attended + counts.off + counts.leave)
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
