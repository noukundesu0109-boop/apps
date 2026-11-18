package dev.nossam.zangyo.plan

data class SnoozeRecord(
    val id: String,
    val atMillis: Long,
    val title: String,
    val detail: String,
)

/** Pending snooze list. A new snooze replaces the earlier ones, and stopping an alarm clears them. */
object SnoozePlan {
    fun replace(existing: List<SnoozeRecord>, next: SnoozeRecord): List<SnoozeRecord> = listOf(next)

    fun cancel(existing: List<SnoozeRecord>, id: String): List<SnoozeRecord> {
        val raw = id.removePrefix("z|")
        if (raw.isBlank()) return existing
        return existing.filter { it.id != raw }
    }

    fun clear(existing: List<SnoozeRecord>): List<SnoozeRecord> = emptyList()
}
