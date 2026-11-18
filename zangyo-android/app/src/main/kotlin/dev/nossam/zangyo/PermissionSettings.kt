package dev.nossam.zangyo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal object PermissionSettings {
    fun open(context: Context, kind: String) {
        val intent = when (kind) {
            "exact" -> exact(context)
            "battery" -> battery(context)
            "fullscreen" -> fullscreen(context)
            "notifications" -> notifications(context)
            else -> return
        }
        start(context, intent, kind == "battery")
    }

    private fun start(context: Context, intent: Intent, battery: Boolean) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            if (battery) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    return
                } catch (_: Exception) {
                }
            }
            try {
                context.startActivity(details(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }
    }

    private fun exact(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context))
        } else {
            details(context)
        }
    }

    private fun battery(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))
    }

    private fun fullscreen(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context))
        } else {
            details(context)
        }
    }

    private fun notifications(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            putExtra("app_package", context.packageName)
            putExtra("app_uid", context.applicationInfo.uid)
        }
    }

    private fun details(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")
}
