package dev.nossam.task.plan

import org.json.JSONArray
import org.json.JSONObject

object HistoryLog {
    fun seed(json: String): String {
        val root = parse(json) ?: return json
        if (root.optBoolean("historySeeded")) return json
        val history = root.optJSONArray("history") ?: JSONArray().also { root.put("history", it) }
        root.optJSONArray("memos")?.let { memos ->
            for (i in 0 until memos.length()) {
                val item = memos.optJSONObject(i) ?: continue
                val date = dateOf(item, "done") ?: continue
                add(history, entry("memo", "", "メモ", item.optString("id"), item.optString("text"), date, item.optString("time"), null))
            }
        }
        root.optJSONArray("cleaning")?.let { rows ->
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val date = dateOf(item, "done") ?: continue
                val name = listOf(item.optString("place"), item.optString("task")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "掃除" }
                add(history, entry("clean", "", "掃除", item.optString("id"), name, date, item.optString("time"), null))
            }
        }
        root.optJSONArray("tabs")?.let { tabs ->
            for (t in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(t) ?: continue
                val items = tab.optJSONArray("items") ?: continue
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val date = dateOf(item, "done") ?: continue
                    add(history, entry(tab.optString("id"), tab.optString("name"), tab.optString("name"), item.optString("id"), item.optString("name"), date, item.optString("time"), numberOrNull(item, "doneKm")))
                }
            }
        }
        root.put("historySeeded", true)
        if (!root.has("historyKeep")) root.put("historyKeep", "forever")
        return root.toString()
    }

    fun prune(json: String, today: String): String {
        val root = parse(json) ?: return json
        if (root.optString("historyKeep") != "year") return json
        val cut = MemoPlanner.parseDate(today)?.minusDays(365) ?: return json
        val history = root.optJSONArray("history")
        if (history != null) {
            val kept = JSONArray()
            for (i in 0 until history.length()) {
                val item = history.optJSONObject(i) ?: continue
                val date = MemoPlanner.parseDate(item.optString("date")) ?: continue
                if (!date.isBefore(cut)) kept.put(item)
            }
            root.put("history", kept)
        }
        val memos = root.optJSONArray("memos")
        if (memos != null) {
            val kept = JSONArray()
            for (i in 0 until memos.length()) {
                val item = memos.optJSONObject(i) ?: continue
                val done = MemoPlanner.parseDate(item.optString("done"))
                if (done != null && done.isBefore(cut)) continue
                kept.put(item)
            }
            root.put("memos", kept)
        }
        return root.toString()
    }

    fun record(root: JSONObject, kind: String, itemId: String, tabId: String, today: String) {
        val captured = capture(root, kind, itemId, tabId, today) ?: return
        val history = root.optJSONArray("history") ?: JSONArray().also { root.put("history", it) }
        add(history, captured)
    }

    fun undo(json: String, entryId: String): String {
        val root = parse(json) ?: return json
        val history = root.optJSONArray("history") ?: return json
        var entry: JSONObject? = null
        val kept = JSONArray()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            if (item.optString("id") == entryId) entry = item else kept.put(item)
        }
        val target = entry ?: return json
        root.put("history", kept)
        val tab = target.optString("tab")
        val itemId = target.optString("itemId")
        if (tab == "memo") {
            clearMemoDone(root.optJSONArray("memos"), itemId)
            return root.toString()
        }
        if (hasLater(kept, tab, itemId, target)) return root.toString()
        val previous = latest(kept, tab, itemId)
        val done = if (target.has("prevDone") && !target.isNull("prevDone")) target.optString("prevDone") else previous?.optString("date")
        when (tab) {
            "clean" -> restoreClean(root.optJSONArray("cleaning"), itemId, done, target)
            "bousai" -> restoreBousai(root.optJSONArray("bousai"), itemId, target)
            else -> restoreTab(root.optJSONArray("tabs"), tab, itemId, done, target)
        }
        return root.toString()
    }

    private fun capture(root: JSONObject, kind: String, itemId: String, tabId: String, today: String): JSONObject? {
        return when (kind) {
            MemoPlanner.KIND_MEMO -> root.optJSONArray("memos")?.let { rows ->
                find(rows, itemId)?.let { item ->
                    entry("memo", "", "メモ", itemId, item.optString("text"), today, item.optString("time"), null).put("prevDone", item.opt("done") ?: JSONObject.NULL)
                }
            }
            MemoPlanner.KIND_CLEAN -> root.optJSONArray("cleaning")?.let { rows ->
                find(rows, itemId)?.let { item ->
                    val name = listOf(item.optString("place"), item.optString("task")).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "掃除" }
                    entry("clean", "", "掃除", itemId, name, today, item.optString("time"), null)
                        .put("prevDone", item.opt("done") ?: JSONObject.NULL)
                        .put("prevShift", item.opt("shift") ?: JSONObject.NULL)
                }
            }
            MemoPlanner.KIND_BOUSAI -> root.optJSONArray("bousai")?.let { rows ->
                find(rows, itemId)?.let { item ->
                    entry("bousai", "", "防災", itemId, item.optString("item").ifBlank { "防災" }, today, null, null)
                        .put("prevDue", item.opt("due") ?: JSONObject.NULL)
                        .put("prevShift", item.opt("shift") ?: JSONObject.NULL)
                }
            }
            else -> root.optJSONArray("tabs")?.let { tabs ->
                for (t in 0 until tabs.length()) {
                    val tab = tabs.optJSONObject(t) ?: continue
                    if (tabId.isNotBlank() && tab.optString("id") != tabId) continue
                    val item = find(tab.optJSONArray("items"), itemId) ?: continue
                    return entry(tab.optString("id"), tab.optString("name"), tab.optString("name"), itemId, item.optString("name"), today, item.optString("time"), numberOrNull(item, "doneKm"))
                        .put("prevDone", item.opt("done") ?: JSONObject.NULL)
                        .put("prevDue", item.opt("due") ?: JSONObject.NULL)
                        .put("prevSeries", item.opt("series") ?: JSONObject.NULL)
                        .put("prevShift", item.opt("shift") ?: JSONObject.NULL)
                        .put("prevDoneKm", if (item.has("doneKm")) item.opt("doneKm") else JSONObject.NULL)
                }
                null
            }
        }
    }

    private fun entry(tab: String, tabName: String, label: String, itemId: String, name: String, date: String, time: String?, km: Double?): JSONObject {
        val clock = time?.takeIf { MemoPlanner.parseTime(it) != null }
        return JSONObject()
            .put("id", "h-$tab-$itemId-$date")
            .put("tab", tab)
            .put("tabName", tabName.ifBlank { label })
            .put("itemId", itemId)
            .put("name", name.ifBlank { label })
            .put("date", date)
            .put("time", clock ?: JSONObject.NULL)
            .put("km", km ?: JSONObject.NULL)
    }

    private fun add(history: JSONArray, entry: JSONObject) {
        val tab = entry.optString("tab")
        val itemId = entry.optString("itemId")
        val date = entry.optString("date")
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            if (item.optString("tab") == tab && item.optString("itemId") == itemId && item.optString("date") == date) return
        }
        history.put(entry)
    }

    private fun hasLater(history: JSONArray, tab: String, itemId: String, entry: JSONObject): Boolean {
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            if (item.optString("tab") != tab || item.optString("itemId") != itemId) continue
            val date = item.optString("date")
            if (date > entry.optString("date") || date == entry.optString("date") && item.optString("id") > entry.optString("id")) return true
        }
        return false
    }

    private fun latest(history: JSONArray, tab: String, itemId: String): JSONObject? {
        var best: JSONObject? = null
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            if (item.optString("tab") != tab || item.optString("itemId") != itemId) continue
            if (best == null || item.optString("date") > best.optString("date")) best = item
        }
        return best
    }

    private fun clearMemoDone(array: JSONArray?, itemId: String) {
        find(array, itemId)?.put("done", JSONObject.NULL)
    }

    private fun restoreClean(array: JSONArray?, itemId: String, done: String?, entry: JSONObject) {
        val item = find(array, itemId) ?: return
        putDateOrNull(item, "done", done)
        putDateOrNull(item, "shift", if (entry.has("prevShift") && !entry.isNull("prevShift")) entry.optString("prevShift") else null)
    }

    private fun restoreBousai(array: JSONArray?, itemId: String, entry: JSONObject) {
        val item = find(array, itemId) ?: return
        if (entry.has("prevDue")) putDateOrNull(item, "due", entry.optString("prevDue").takeIf { MemoPlanner.parseDate(it) != null })
        putDateOrNull(item, "shift", entry.optString("prevShift").takeIf { MemoPlanner.parseDate(it) != null })
    }

    private fun restoreTab(tabs: JSONArray?, tabId: String, itemId: String, done: String?, entry: JSONObject) {
        if (tabs == null) return
        for (t in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(t) ?: continue
            if (tab.optString("id") != tabId) continue
            val item = find(tab.optJSONArray("items"), itemId) ?: return
            putDateOrNull(item, "done", done)
            putDateOrNull(item, "shift", entry.optString("prevShift").takeIf { MemoPlanner.parseDate(it) != null })
            if (entry.has("prevDue")) putDateOrNull(item, "due", entry.optString("prevDue").takeIf { MemoPlanner.parseDate(it) != null })
            if (entry.has("prevSeries")) putDateOrNull(item, "series", entry.optString("prevSeries").takeIf { MemoPlanner.parseDate(it) != null })
            if (entry.has("prevDoneKm") && !entry.isNull("prevDoneKm")) item.put("doneKm", entry.opt("prevDoneKm"))
        }
    }

    private fun putDateOrNull(item: JSONObject, key: String, value: String?) {
        if (MemoPlanner.parseDate(value) != null) item.put(key, value) else item.put(key, JSONObject.NULL)
    }

    private fun find(array: JSONArray?, id: String): JSONObject? {
        if (array == null) return null
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("id") == id) return item
        }
        return null
    }

    private fun dateOf(item: JSONObject, key: String): String? = item.optString(key).takeIf { MemoPlanner.parseDate(it) != null }

    private fun numberOrNull(item: JSONObject, key: String): Double? {
        if (!item.has(key) || item.isNull(key)) return null
        return item.optDouble(key).takeIf { !it.isNaN() }
    }

    private fun parse(json: String): JSONObject? = try {
        JSONObject(json)
    } catch (_: Exception) {
        null
    }
}
