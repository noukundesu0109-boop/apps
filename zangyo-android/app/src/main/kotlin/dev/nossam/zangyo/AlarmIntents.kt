package dev.nossam.zangyo

internal object AlarmIntents {
    const val ACTION_FIRE = "dev.nossam.zangyo.action.FIRE"
    const val ACTION_MAINT = "dev.nossam.zangyo.action.MAINT"
    const val ACTION_DISMISS = "dev.nossam.zangyo.action.DISMISS"
    const val ACTION_SNOOZE = "dev.nossam.zangyo.action.SNOOZE"
    const val ACTION_CLOSE = "dev.nossam.zangyo.action.CLOSE_ALARM"
    const val ACTION_CANCEL_SNOOZE = "dev.nossam.zangyo.action.CANCEL_SNOOZE"
    const val EXTRA_ID = "id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_DETAIL = "detail"
    const val EXTRA_SOURCE = "source"
    const val ACTION_SKIP_NEXT = "dev.nossam.zangyo.action.SKIP_NEXT"
    const val EXTRA_OPEN_TAB = "dev.nossam.zangyo.extra.TAB"
    const val CHANNEL_ID = "zangyo-alarms"
    const val FALLBACK_CHANNEL_ID = "zangyo-alarms-fallback"
    const val NEXT_CHANNEL_ID = "zangyo-next-alarm"
    const val NOTIFICATION_ID = 4101
    const val NEXT_NOTIFICATION_ID = 4102
    const val FALLBACK_NOTIFICATION_ID = 4104
    const val NEXT_SKIP_REQUEST = 4103
    const val NEXT_CANCEL_SNOOZE_REQUEST = 4105
    const val SHOW_TAB_REQUEST = 8
}
