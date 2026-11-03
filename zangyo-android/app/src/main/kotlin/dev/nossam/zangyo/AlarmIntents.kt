package dev.nossam.zangyo

internal object AlarmIntents {
    const val ACTION_FIRE = "dev.nossam.zangyo.action.FIRE"
    const val ACTION_MAINT = "dev.nossam.zangyo.action.MAINT"
    const val ACTION_DISMISS = "dev.nossam.zangyo.action.DISMISS"
    const val ACTION_SNOOZE = "dev.nossam.zangyo.action.SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_SOURCE = "source"
    const val CHANNEL_ID = "zangyo-alarms"
    const val NOTIFICATION_ID = 4101
}
