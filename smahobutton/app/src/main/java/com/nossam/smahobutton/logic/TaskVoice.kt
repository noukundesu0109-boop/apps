package com.nossam.smahobutton.logic

/**
 * Opens the Task app the same way its voice widget and quick tile do.
 * [flags] are [android.content.Intent.FLAG_ACTIVITY_NEW_TASK] or
 * [android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP].
 */
data class TaskVoiceIntent(
    val packageName: String,
    val className: String,
    val extraKey: String,
    val extraValue: Boolean,
    val flags: Int,
)

const val TASK_PACKAGE = "dev.nossam.task"
const val TASK_ACTIVITY = "dev.nossam.task.MainActivity"
const val TASK_VOICE_EXTRA = "dev.nossam.task.extra.VOICE"

private const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
private const val FLAG_ACTIVITY_CLEAR_TOP = 0x04000000

fun taskVoiceIntent(): TaskVoiceIntent = TaskVoiceIntent(
    packageName = TASK_PACKAGE,
    className = TASK_ACTIVITY,
    extraKey = TASK_VOICE_EXTRA,
    extraValue = true,
    flags = FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP,
)
