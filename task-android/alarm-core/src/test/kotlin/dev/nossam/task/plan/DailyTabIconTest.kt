package dev.nossam.task.plan

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DailyTabIconTest {
    @Test
    fun onlyTheDailyTabUsesTheCalendarIcon() {
        val html = File(repoRoot(), "task-app/index.html").readText()
        assertTrue(html.contains("re.node;var ie=S(re)"))
        assertTrue(html.contains("name:`calendar-days`"))
        assertTrue(html.contains("Ne.node;var Pe=S(Ne)"))
        assertTrue(html.contains("name:`tag`"))
        assertTrue(
            html.contains("n.tabs.map(e=>[e.id,e.name,e.kind===`car`?oe:e.id===`nichijo`||e.name===`日常`?ie:Pe])"),
        )
        assertTrue(html.contains("[`memo`,`メモ`,xe]"))
        assertTrue(html.contains("[`all`,`すべて`,ye]"))
        assertTrue(html.contains("[`clean`,`掃除`,Ae]"))
        assertTrue(html.contains("[`bousai`,`防災`,k]"))
        assertTrue(html.contains("[`settings`,`控え`,De]"))
    }

    private fun repoRoot(): File {
        var dir = File("").absoluteFile
        repeat(6) {
            if (File(dir, "task-app/index.html").isFile) return dir
            dir = dir.parentFile ?: return dir
        }
        return dir
    }
}
