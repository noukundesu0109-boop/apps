package dev.nossam.task.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnoozePruneTest {
    @Test
    fun movingTheTimeDropsTheLaterSnooze() {
        val moved = TaskBoard(
            memos = listOf(
                MemoEntry(id = "m1", text = "病院", due = "2026-11-05", time = "19:00", done = false),
                MemoEntry(id = "m2", text = "薬", due = "2026-11-05", time = "20:00", done = false),
            ),
        )
        assertFalse(MemoPlanner.keepSnooze(moved, later()))
        assertEquals(listOf("other"), MemoPlanner.keepSnoozes(moved, listOf(later(), other())).map { it.id })
    }

    @Test
    fun aTimeAfterTheSnoozeAlsoDropsIt() {
        val laterToday = board(time = "19:00")
        val hold = later().copy(origTime = "19:00", atMillis = millis("2026-11-05", "18:40"))
        assertFalse(MemoPlanner.keepSnooze(laterToday, hold))
    }

    @Test
    fun markingDoneDropsTheLaterSnooze() {
        assertFalse(MemoPlanner.keepSnooze(board(done = true), later()))
        assertFalse(MemoPlanner.keepSnooze(board(done = true), later().copy(origDate = null, origTime = null)))
    }

    @Test
    fun deletingTheMemoDropsTheLaterSnooze() {
        assertFalse(MemoPlanner.keepSnooze(TaskBoard(), later()))
        assertFalse(MemoPlanner.keepSnooze(TaskBoard(), later().copy(origDate = null, origTime = null)))
    }

    @Test
    fun anUnchangedMemoKeepsTheLaterSnooze() {
        assertTrue(MemoPlanner.keepSnooze(board(), later()))
        assertEquals(listOf("s1"), MemoPlanner.keepSnoozes(board(), listOf(later())).map { it.id })
    }

    @Test
    fun anOldSnoozeWithoutTheOriginalTimeStaysUntilTheMemoIsDoneOrGone() {
        val old = later().copy(origDate = null, origTime = null)
        assertTrue(MemoPlanner.keepSnooze(board(time = "19:00"), old))
        assertFalse(MemoPlanner.keepSnooze(board(done = true), old))
        assertFalse(MemoPlanner.keepSnooze(TaskBoard(), old))
    }

    @Test
    fun completeRemovesOnlyThatItemsSnooze() {
        assertTrue(MemoPlanner.sameSnoozeItem("memo", "m1", "", "memo", "m1", ""))
        assertFalse(MemoPlanner.sameSnoozeItem("memo", "m1", "", "memo", "m2", ""))
        assertFalse(MemoPlanner.sameSnoozeItem("memo", "m1", "", "clean", "m1", ""))
        assertFalse(MemoPlanner.sameSnoozeItem("tab", "i1", "car", "tab", "i1", "shop"))
    }

    private fun board(time: String = "18:30", done: Boolean = false) = TaskBoard(
        memos = listOf(MemoEntry(id = "m1", text = "病院", due = "2026-11-05", time = time, done = done)),
    )

    private fun later() = SnoozeHold(
        id = "s1",
        atMillis = millis("2026-11-05", "18:40"),
        kind = MemoPlanner.KIND_MEMO,
        itemId = "m1",
        origDate = "2026-11-05",
        origTime = "18:30",
    )

    private fun other() = SnoozeHold(
        id = "other",
        atMillis = millis("2026-11-05", "20:10"),
        kind = MemoPlanner.KIND_MEMO,
        itemId = "m2",
        origDate = "2026-11-05",
        origTime = "20:00",
    )

    private fun millis(date: String, time: String): Long {
        val day = MemoPlanner.parseDate(date)!!
        val clock = MemoPlanner.parseTime(time)!!
        return day.atTime(clock).atZone(MemoPlanner.ZONE).toInstant().toEpochMilli()
    }
}
