package dev.nossam.task

internal object AlarmIntents {
    const val ACTION_FIRE = "dev.nossam.task.action.FIRE"
    const val ACTION_MAINT = "dev.nossam.task.action.MAINT"
    const val ACTION_SUMMARY = "dev.nossam.task.action.SUMMARY"
    const val ACTION_DISMISS = "dev.nossam.task.action.DISMISS"
    const val ACTION_SNOOZE = "dev.nossam.task.action.SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_DATE = "date"
    const val CHANNEL_ID = "task-alarms"
    const val SUMMARY_CHANNEL_ID = "task-summary"
    const val NOTIFICATION_ID = 4101
    const val SUMMARY_NOTIFICATION_ID = 4102
}
