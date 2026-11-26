package dev.nossam.task.plan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HistoryLogTest {
    @Test
    fun completedMemosAreNotDeletedAfterSevenDays() {
        val html = File(repoRoot(), "task-app/index.html").readText()
        assertFalse(html.contains("7日たつと消えます"))
        assertFalse(html.contains("tt(c)-tt(e.done)<Ct"))
        val json = """{"memos":[{"id":"m1","text":"病院","due":"2026-01-01","done":"2026-01-02","time":"09:00"}],"historyKeep":"forever"}"""
        val kept = JSONObject(HistoryLog.prune(json, "2026-01-20"))
        assertEquals(1, kept.getJSONArray("memos").length())
    }

    @Test
    fun completionFromTheAppOrWidgetIsAppendedOnce() {
        val json = """{"cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-10-01","cycle":30,"time":"09:00"}]}"""
        val once = completeOccurrence(json, "clean", "c1", "clean", "2026-11-16")
        val twice = completeOccurrence(once, "clean", "c1", "clean", "2026-11-16")
        val history = JSONObject(twice).getJSONArray("history")
        assertEquals(1, history.length())
        val entry = history.getJSONObject(0)
        assertEquals("clean", entry.getString("tab"))
        assertEquals("c1", entry.getString("itemId"))
        assertEquals("居間 床", entry.getString("name"))
        assertEquals("2026-11-16", entry.getString("date"))
        assertEquals("09:00", entry.getString("time"))
    }

    @Test
    fun seedKeepsExistingDoneDatesAndCompletedMemos() {
        val json = """{"memos":[{"id":"m1","text":"病院","due":"2026-11-01","done":"2026-11-02","time":"15:00"}],"cleaning":[{"id":"c1","place":"台所","task":"床","done":"2026-10-01","time":"08:00"}],"tabs":[{"id":"nichijo","name":"日常","kind":"custom","items":[{"id":"d1","name":"換気","done":"2026-11-03","time":"09:00"}]}]}"""
        val seeded = JSONObject(HistoryLog.seed(json))
        assertTrue(seeded.getBoolean("historySeeded"))
        assertEquals(3, seeded.getJSONArray("history").length())
        assertEquals(seeded.toString(), HistoryLog.seed(seeded.toString()))
    }

    @Test
    fun undoRestoresAMemoAndThePreviousTabDate() {
        val memo = """{"memos":[{"id":"m1","text":"病院","due":"2026-11-16","time":"15:00"}]}"""
        val done = JSONObject(completeOccurrence(memo, "memo", "m1", "", "2026-11-16"))
        val entry = done.getJSONArray("history").getJSONObject(0).getString("id")
        val back = JSONObject(HistoryLog.undo(done.toString(), entry))
        assertTrue(back.getJSONArray("memos").getJSONObject(0).isNull("done"))
        assertEquals(0, back.getJSONArray("history").length())

        val daily = """{"tabs":[{"id":"nichijo","name":"日常","kind":"custom","items":[{"id":"d1","name":"換気","repeat":"month","every":1,"basis":"plan","due":"2026-11-01","series":"2026-11-01","time":"09:00"}]}]}"""
        val finished = JSONObject(completeOccurrence(daily, "x:nichijo", "d1", "nichijo", "2026-11-16"))
        val id = finished.getJSONArray("history").getJSONObject(0).getString("id")
        val restored = JSONObject(HistoryLog.undo(finished.toString(), id))
        val item = restored.getJSONArray("tabs").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        assertEquals("2026-11-01", item.getString("due"))
        assertEquals("2026-11-01", item.getString("series"))
        assertTrue(item.isNull("done"))
    }

    @Test
    fun yearRetentionDropsEntriesOlderThan365Days() {
        val old = "2025-01-01"
        val json = """{"historyKeep":"year","historySeeded":true,"memos":[{"id":"m1","text":"古い","due":"2024-12-01","done":"$old"}],"history":[{"id":"h1","tab":"clean","itemId":"c1","name":"床","date":"$old"}]}"""
        val pruned = JSONObject(HistoryLog.prune(json, "2026-01-02"))
        assertEquals(0, pruned.getJSONArray("memos").length())
        assertEquals(0, pruned.getJSONArray("history").length())
        val kept = JSONObject(HistoryLog.prune(json.replace(""""historyKeep":"year"""", """"historyKeep":"forever""""), "2026-01-02"))
        assertEquals(1, kept.getJSONArray("memos").length())
        assertEquals(1, kept.getJSONArray("history").length())
    }

    @Test
    fun backupRoundTripKeepsHistoryAndOldBackupsStillSeed() {
        val current = HistoryLog.seed("""{"memos":[{"id":"m1","text":"病院","done":"2026-11-02"}],"historyKeep":"forever"}""")
        val restored = JSONObject(current)
        assertEquals(1, restored.getJSONArray("history").length())
        assertEquals("forever", restored.getString("historyKeep"))
        val legacy = """{"memos":[{"id":"m1","text":"病院","done":"2026-11-02"}],"cleaning":[]}"""
        val fromOld = JSONObject(HistoryLog.seed(legacy))
        assertTrue(fromOld.getBoolean("historySeeded"))
        assertEquals("memo", fromOld.getJSONArray("history").getJSONObject(0).getString("tab"))
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        repeat(8) {
            if (File(dir, "task-app/index.html").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }
}
