package dev.nossam.task.plan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetCompletionSyncTest {
    private val day = "2026-11-06"

    @Test
    fun aStaleSnapshotKeepsTheCleaningCompletionAndANewMemo() {
        val pending = listOf(PendingCompletion("clean", "c1", "", day))
        val stale = """
            {"memos":[{"id":"m2","text":"牛乳","due":"2026-11-07","done":null}],
             "rings":{"clean":true},
             "cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-10-07","cycle":30,"time":"09:00","shift":"2026-11-06"},
                         {"id":"c2","place":"玄関","task":"掃き","done":"2026-11-01","cycle":14}]}
        """.trimIndent()
        val merged = mergePendingCompletions(stale, pending)
        val board = parseBoard(merged.json)
        assertEquals(day, board.cleaning.first { it.id == "c1" }.done)
        assertNull(board.cleaning.first { it.id == "c1" }.shift)
        assertEquals("2026-11-01", board.cleaning.first { it.id == "c2" }.done)
        assertEquals("牛乳", board.memos.single().text)
        assertEquals(1, merged.stillPending.size)
        val again = mergePendingCompletions(stale, merged.stillPending)
        assertEquals(day, parseBoard(again.json).cleaning.first { it.id == "c1" }.done)
        val before = MemoPlanner.planAlerts(parseBoard(stale), morning(day))
        val after = MemoPlanner.planAlerts(parseBoard(merged.json), morning(day))
        assertEquals(day, before.single().date)
        assertEquals("2026-12-06", after.single().date)
    }

    @Test
    fun theScreenEchoClearsTheGuardAndALaterDateEditStays() {
        val pending = listOf(PendingCompletion("clean", "c1", "", day))
        val echo = """{"cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-11-06","cycle":30}]}"""
        val confirmed = mergePendingCompletions(echo, pending)
        assertTrue(confirmed.stillPending.isEmpty())
        assertEquals(day, parseBoard(confirmed.json).cleaning.single().done)
        val corrected = """{"cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-11-08","cycle":30}]}"""
        val kept = mergePendingCompletions(corrected, confirmed.stillPending)
        assertEquals("2026-11-08", parseBoard(kept.json).cleaning.single().done)
    }

    @Test
    fun deletingTheItemIsKeptAndDoesNotComeBack() {
        val pending = listOf(PendingCompletion("clean", "c1", "", day))
        val deleted = """{"cleaning":[{"id":"c2","place":"玄関","task":"掃き","done":"2026-11-01","cycle":14}],"memos":[{"id":"m9","text":"残す","due":null,"done":null}]}"""
        val merged = mergePendingCompletions(deleted, pending)
        assertTrue(merged.stillPending.isEmpty())
        val board = parseBoard(merged.json)
        assertTrue(board.cleaning.none { it.id == "c1" })
        assertEquals("残す", board.memos.single().text)
    }

    @Test
    fun everyKindMergesOnceAndARepeatDoesNotStepTwice() {
        val pending = listOf(
            PendingCompletion("memo", "m1", "", day),
            PendingCompletion("bousai", "b1", "", day),
            PendingCompletion("car", "pad", "car", day),
            PendingCompletion("x:nichijo", "d1", "nichijo", day),
            PendingCompletion("x:shop", "s1", "shop", day),
        )
        val stale = """
            {"memos":[{"id":"m1","text":"病院","due":"2026-11-06","time":"18:30","done":null},
                      {"id":"m2","text":"新しいメモ","due":null,"done":null}],
             "bousai":[{"id":"b1","item":"水","qty":"1箱","due":"2026-11-06","time":"07:00"},
                       {"id":"b2","item":"ラジオ","due":"2030-05-01"}],
             "tabs":[
               {"id":"car","kind":"car","name":"車","items":[
                 {"id":"pad","name":"ブレーキパッド","done":"2026-05-06","months":6,"km":40000,"doneKm":12000,"time":"09:00"}
               ]},
               {"id":"nichijo","kind":"custom","name":"日常","items":[
                 {"id":"d1","name":"換気扇","repeat":"month","every":1,"basis":"plan","due":"2026-11-01","series":"2026-11-01","shift":"2026-11-06","time":"09:00"}
               ]},
               {"id":"shop","kind":"custom","name":"買い物","items":[
                 {"id":"s1","name":"電池","done":"2026-10-06","months":1,"time":"08:00"}
               ]}
             ]}
        """.trimIndent()
        val first = mergePendingCompletions(stale, pending)
        val second = mergePendingCompletions(stale, first.stillPending)
        val board = parseBoard(second.json)
        assertTrue(board.memos.first { it.id == "m1" }.done)
        assertEquals("新しいメモ", board.memos.first { it.id == "m2" }.text)
        assertNull(board.bousai.first { it.id == "b1" }.due)
        assertEquals("2030-05-01", board.bousai.first { it.id == "b2" }.due)
        val raw = JSONObject(second.json)
        val car = raw.getJSONArray("tabs").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        assertEquals(day, car.getString("done"))
        assertEquals(40000, car.getInt("km"))
        assertEquals(12000, car.getInt("doneKm"))
        assertEquals("2026-12-01", board.extras.first { it.id == "d1" }.due)
        assertEquals("2026-11-01", board.extras.first { it.id == "d1" }.series)
        assertEquals(day, board.extras.first { it.id == "s1" }.done)
        assertEquals(1, board.extras.first { it.id == "s1" }.months)
        val echoed = mergePendingCompletions(second.json, second.stillPending)
        assertTrue(echoed.stillPending.isEmpty())
        assertEquals("2026-12-01", parseBoard(echoed.json).extras.first { it.id == "d1" }.due)
        val corrected = JSONObject(echoed.json)
        val daily = corrected.getJSONArray("tabs").getJSONObject(1).getJSONArray("items").getJSONObject(0)
        daily.put("due", "2026-12-03")
        val kept = mergePendingCompletions(corrected.toString(), echoed.stillPending)
        assertEquals("2026-12-03", parseBoard(kept.json).extras.first { it.id == "d1" }.due)
    }

    @Test
    fun aCompletionWithoutAStoredDayIsNotReplayed() {
        val pending = listOf(PendingCompletion("clean", "c1", "", ""))
        val stale = """{"cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-10-07","cycle":30}]}"""
        val merged = mergePendingCompletions(stale, pending)
        assertEquals("2026-10-07", parseBoard(merged.json).cleaning.single().done)
        assertEquals(1, merged.stillPending.size)
    }

    @Test
    fun oneConfirmedItemDoesNotReleaseTheOther() {
        val pending = listOf(
            PendingCompletion("clean", "c1", "", day),
            PendingCompletion("memo", "m1", "", day),
        )
        val mixed = """
            {"cleaning":[{"id":"c1","place":"居間","task":"床","done":"2026-11-06","cycle":30}],
             "memos":[{"id":"m1","text":"病院","due":"2026-11-06","done":null},{"id":"m3","text":"追加した","due":null,"done":null}]}
        """.trimIndent()
        val merged = mergePendingCompletions(mixed, pending)
        assertEquals(listOf("m1"), merged.stillPending.map { it.id })
        val board = parseBoard(merged.json)
        assertTrue(board.memos.first { it.id == "m1" }.done)
        assertEquals("追加した", board.memos.first { it.id == "m3" }.text)
        assertEquals(day, board.cleaning.single().done)
    }

    private fun morning(iso: String) = LocalDate.parse(iso).atTime(8, 0).atZone(MemoPlanner.ZONE)
}
