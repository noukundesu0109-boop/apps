package dev.nossam.zangyo.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SnoozePlanTest {
    private val earlier = SnoozeRecord("aaa", 1_000L, "起床", "5分後")
    private val other = SnoozeRecord("bbb", 2_000L, "出勤", "5分後")

    @Test
    fun newSnoozeReplacesEarlierSnoozes() {
        val next = SnoozeRecord("ccc", 9_000L, "起床", "5分後")
        val kept = SnoozePlan.replace(listOf(earlier, other), next)
        assertEquals(listOf(next), kept)
    }

    @Test
    fun cancelRemovesOnlyTheChosenSnooze() {
        val pending = listOf(earlier, other)
        assertEquals(listOf(other), SnoozePlan.cancel(pending, "aaa"))
        assertEquals(listOf(earlier), SnoozePlan.cancel(pending, "z|bbb"))
        assertEquals(pending, SnoozePlan.cancel(pending, "missing"))
        assertEquals(pending, SnoozePlan.cancel(pending, "z|"))
    }

    @Test
    fun dismissClearsEveryPendingSnooze() {
        assertTrue(SnoozePlan.clear(listOf(earlier, other)).isEmpty())
        assertTrue(SnoozePlan.clear(emptyList()).isEmpty())
    }

    @Test
    fun alarmListOffersSnoozeCancelAndRenamesSkip() {
        val js = File(repoRoot(), "zangyo-android/app/src/main/webbridge/android-bridge.js").readText()
        assertTrue(js.contains("data-act=\"cancel-snooze\""))
        assertTrue(js.contains(">スヌーズを解除</button>"))
        assertTrue(js.contains(">この回は鳴らさない</button>"))
        assertTrue(js.contains(">戻す</button>"))
        assertTrue(!js.contains(">解除</button>"))
        assertTrue(js.contains("ZangyoNative.cancelSnooze"))
        assertTrue(js.contains("data-act=\"open-setting\""))
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        repeat(8) {
            if (File(dir, "zangyo-android/app/src/main/webbridge/android-bridge.js").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }
}
