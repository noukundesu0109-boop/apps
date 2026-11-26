package dev.nossam.task.plan

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetMoveTest {
    @Test
    fun checkingADailyItemRecordsCompletionAndTheNextDate() {
        val json = """{"tabs":[{"id":"nichijo","name":"日常","kind":"custom","items":[
            {"id":"d1","name":"換気","repeat":"month","every":1,"basis":"plan","due":"2026-11-01","series":"2026-11-01","time":"09:00","sound":true}
        ]}]}"""
        val done = completeOccurrence(json, "x:nichijo", "d1", "nichijo", "2026-11-16")
        val item = parseBoard(done).extras.single()
        assertEquals("2026-11-16", item.done)
        assertNull(item.shift)
        assertEquals("2026-12-01", item.due)
        assertEquals("2026-11-01", item.series)
        assertEquals("09:00", item.time)
        val again = mergePendingCompletions(
            json,
            listOf(PendingCompletion("x:nichijo", "d1", "nichijo", "2026-11-16")),
        )
        assertEquals("2026-12-01", parseBoard(again.json).extras.single().due)
        assertEquals(1, again.stillPending.size)
        val echoed = mergePendingCompletions(
            done,
            listOf(PendingCompletion("x:nichijo", "d1", "nichijo", "2026-11-16")),
        )
        assertTrue(echoed.stillPending.isEmpty())
    }

    @Test
    fun movingAMemoKeepsItsTime() {
        val json = """{"memos":[{"id":"m1","text":"病院","due":"2026-11-16","time":"15:00"}]}"""
        val moved = moveOccurrence(json, "memo", "m1", "", "2026-11-20")
        val memo = parseBoard(moved).memos.single()
        assertEquals("2026-11-20", memo.due)
        assertEquals("15:00", memo.time)
        assertTrue(!memo.done)
        val pending = listOf(PendingMove("memo", "m1", "", "2026-11-20"))
        val restored = mergePendingMoves(json, pending)
        assertEquals("2026-11-20", parseBoard(restored.json).memos.single().due)
        assertEquals(1, restored.stillPending.size)
        val echoed = mergePendingMoves(moved, pending)
        assertTrue(echoed.stillPending.isEmpty())
    }

    @Test
    fun movingARecurringOccurrenceOnlyShiftsThisOne() {
        val json = """{"tabs":[{"id":"nichijo","name":"日常","kind":"custom","items":[
            {"id":"d1","name":"換気","repeat":"month","every":1,"basis":"plan","due":"2026-11-01","series":"2026-11-01","time":"09:00","sound":true}
        ]}]}"""
        val moved = moveOccurrence(json, "x:nichijo", "d1", "nichijo", "2026-11-20")
        val item = parseBoard(moved).extras.single()
        assertEquals("2026-11-20", item.shift)
        assertEquals("2026-11-01", item.due)
        assertEquals("2026-11-01", item.series)
        assertEquals("09:00", item.time)
        val raw = JSONObject(moved).getJSONArray("tabs").getJSONObject(0).getJSONArray("items").getJSONObject(0)
        assertEquals("2026-11-20", raw.getString("shift"))
        assertEquals("09:00", raw.getString("time"))
        val old = mergePendingMoves(json, listOf(PendingMove("x:nichijo", "d1", "nichijo", "2026-11-20")))
        assertEquals("2026-11-20", parseBoard(old.json).extras.single().shift)
        val echoed = mergePendingMoves(moved, listOf(PendingMove("x:nichijo", "d1", "nichijo", "2026-11-20")))
        assertTrue(echoed.stillPending.isEmpty())
    }
}
