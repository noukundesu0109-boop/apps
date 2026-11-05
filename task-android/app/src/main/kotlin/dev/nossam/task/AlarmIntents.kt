package dev.nossam.task

internal object AlarmIntents {
    const val ACTION_FIRE = "dev.nossam.task.action.FIRE"
    const val ACTION_MAINT = "dev.nossam.task.action.MAINT"
    const val ACTION_SUMMARY = "dev.nossam.task.action.SUMMARY"
    const val ACTION_LEAD = "dev.nossam.task.action.LEAD"
    const val ACTION_DONE = "dev.nossam.task.action.DONE"
    const val ACTION_DISMISS = "dev.nossam.task.action.DISMISS"
    const val ACTION_SNOOZE = "dev.nossam.task.action.SNOOZE"
    const val ACTION_MARK_DONE = "dev.nossam.task.action.MARK_DONE"
    const val ACTION_LATER = "dev.nossam.task.action.LATER"
    const val ACTION_SWIPE = "dev.nossam.task.action.SWIPE"
    const val ACTION_QUIET = "dev.nossam.task.action.QUIET"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_DATE = "date"
    const val EXTRA_TIME = "time"
    const val EXTRA_KIND = "kind"
    const val EXTRA_ITEM = "item"
    const val EXTRA_TAB = "tab"
    const val EXTRA_LEAD = "lead"
    const val CHANNEL_ID = "task-alarms"
    const val SUMMARY_CHANNEL_ID = "task-summary"
    const val NEXT_CHANNEL_ID = "task-next"
    const val LEAD_CHANNEL_ID = "task-ahead"
    const val NOTIFICATION_ID = 4101
    const val SUMMARY_NOTIFICATION_ID = 4102
    const val NEXT_NOTIFICATION_ID = 4103
    const val LEAD_NOTIFICATION_ID = 4104
    const val NEXT_DONE_REQUEST = 4105
}
