package dev.nossam.task

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import dev.nossam.task.plan.MemoPlanner
import dev.nossam.task.plan.WidgetEntry
import dev.nossam.task.plan.parseBoard
import java.time.ZonedDateTime

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_CHECK -> {
                val kind = intent.getStringExtra(AlarmIntents.EXTRA_KIND).orEmpty()
                val itemId = intent.getStringExtra(AlarmIntents.EXTRA_ITEM).orEmpty()
                val tabId = intent.getStringExtra(AlarmIntents.EXTRA_TAB).orEmpty()
                if (kind.isNotBlank() && itemId.isNotBlank()) {
                    TaskAlarms.complete(context, kind, itemId, tabId)
                }
                updateAll(context)
                return
            }
            ACTION_REFRESH -> {
                updateAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java)
        manager?.cancel(refreshPending(context))
    }

    companion object {
        const val ACTION_CHECK = "dev.nossam.task.action.WIDGET_CHECK"
        const val ACTION_REFRESH = "dev.nossam.task.action.WIDGET_REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            scheduleRefresh(context, ids.isNotEmpty())
            if (ids.isEmpty()) return
            for (id in ids) {
                manager.updateAppWidget(id, views(context, id))
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
            }
        }

        private fun scheduleRefresh(context: Context, armed: Boolean) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = refreshPending(context)
            if (!armed) {
                manager.cancel(pending)
                return
            }
            val board = AlarmStore(context).readWork()?.let { raw ->
                try {
                    parseBoard(raw)
                } catch (_: Exception) {
                    null
                }
            } ?: dev.nossam.task.plan.TaskBoard()
            val at = MemoPlanner.nextWidgetRefresh(board, ZonedDateTime.now(MemoPlanner.ZONE))
            val trigger = at.toInstant().toEpochMilli()
            try {
                if (canExact(context)) {
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                } else {
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
                }
            } catch (_: Exception) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
        }

        private fun refreshPending(context: Context): PendingIntent {
            val intent = Intent(context, TodayWidget::class.java).setAction(ACTION_REFRESH)
            return PendingIntent.getBroadcast(
                context,
                REFRESH_REQUEST,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun canExact(context: Context): Boolean {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.canScheduleExactAlarms()
            } else {
                true
            }
        }

        private fun views(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val service = Intent(context, TodayWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, service)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            views.setPendingIntentTemplate(R.id.widget_list, checkTemplate(context))
            views.setOnClickPendingIntent(R.id.widget_add, VoiceWidget.voicePending(context))
            return views
        }

        private fun checkTemplate(context: Context): PendingIntent {
            val intent = Intent(context, TodayWidget::class.java).setAction(ACTION_CHECK)
            val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(
                context,
                21,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
        }

        private const val REFRESH_REQUEST = 77
    }
}

class TodayWidgetService : android.widget.RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodayWidgetFactory(applicationContext)
    }
}

private class TodayWidgetFactory(
    private val context: Context,
) : android.widget.RemoteViewsService.RemoteViewsFactory {
    private var items: List<WidgetEntry> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = loadWindow(context)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
        val views = RemoteViews(context.packageName, R.layout.widget_today_item)
        if (item == null) return views
        views.setTextViewText(R.id.widget_item_time, item.timeText)
        views.setViewVisibility(
            R.id.widget_item_time,
            if (item.timeText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE,
        )
        views.setTextViewText(R.id.widget_item_title, item.titleText)
        val fill = Intent().apply {
            putExtra(AlarmIntents.EXTRA_KIND, item.item.kind)
            putExtra(AlarmIntents.EXTRA_ITEM, item.item.id)
            putExtra(AlarmIntents.EXTRA_TAB, item.item.tabId.orEmpty())
        }
        views.setOnClickFillInIntent(R.id.widget_item_check, fill)
        views.setOnClickFillInIntent(R.id.widget_item_row, fill)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.item?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal fun loadWindow(context: Context): List<WidgetEntry> {
    val json = AlarmStore(context).readWork() ?: return emptyList()
    val board = try {
        parseBoard(json)
    } catch (_: Exception) {
        return emptyList()
    }
    return MemoPlanner.widgetWindow(board, ZonedDateTime.now(MemoPlanner.ZONE))
}
