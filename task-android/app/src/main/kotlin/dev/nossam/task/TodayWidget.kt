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
import dev.nossam.task.plan.WidgetRow
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
                    if (intent.getStringExtra(EXTRA_OP) == OP_MOVE) {
                        context.startActivity(
                            Intent(context, MoveDateActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(AlarmIntents.EXTRA_KIND, kind)
                                .putExtra(AlarmIntents.EXTRA_ITEM, itemId)
                                .putExtra(AlarmIntents.EXTRA_TAB, tabId)
                                .putExtra(AlarmIntents.EXTRA_TITLE, intent.getStringExtra(AlarmIntents.EXTRA_TITLE).orEmpty()),
                        )
                    } else {
                        TaskAlarms.complete(context, kind, itemId, tabId)
                        updateAll(context)
                    }
                }
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
        const val EXTRA_OP = "op"
        const val OP_MOVE = "move"
        const val OP_CHECK = "check"

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
    private var rows: List<WidgetRow> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        rows = loadWindow(context)
    }

    override fun onDestroy() {
        rows = emptyList()
    }

    override fun getCount(): Int = rows.size

    override fun getViewTypeCount(): Int = 1

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today_item)
        when (val row = rows.getOrNull(position)) {
            is WidgetRow.Header -> {
                views.setViewVisibility(R.id.widget_section, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_section, row.label)
                views.setViewVisibility(R.id.widget_item_check, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_item_time, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_item_title, android.view.View.GONE)
            }
            is WidgetRow.Item -> itemView(views, row)
            null -> Unit
        }
        return views
    }

    private fun itemView(views: RemoteViews, row: WidgetRow.Item): RemoteViews {
        val entry = row.entry
        views.setViewVisibility(R.id.widget_section, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_item_check, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_item_title, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_item_time, entry.timeText)
        views.setViewVisibility(
            R.id.widget_item_time,
            if (entry.timeText.isBlank()) android.view.View.GONE else android.view.View.VISIBLE,
        )
        views.setTextViewText(R.id.widget_item_title, entry.titleText)
        val check = Intent().apply {
            putExtra(TodayWidget.EXTRA_OP, TodayWidget.OP_CHECK)
            putExtra(AlarmIntents.EXTRA_KIND, entry.item.kind)
            putExtra(AlarmIntents.EXTRA_ITEM, entry.item.id)
            putExtra(AlarmIntents.EXTRA_TAB, entry.item.tabId.orEmpty())
        }
        val move = Intent().apply {
            putExtra(TodayWidget.EXTRA_OP, TodayWidget.OP_MOVE)
            putExtra(AlarmIntents.EXTRA_KIND, entry.item.kind)
            putExtra(AlarmIntents.EXTRA_ITEM, entry.item.id)
            putExtra(AlarmIntents.EXTRA_TAB, entry.item.tabId.orEmpty())
            putExtra(AlarmIntents.EXTRA_TITLE, entry.item.title)
        }
        views.setOnClickFillInIntent(R.id.widget_item_check, check)
        views.setOnClickFillInIntent(R.id.widget_item_title, move)
        views.setOnClickFillInIntent(R.id.widget_item_time, move)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getItemId(position: Int): Long = when (val row = rows.getOrNull(position)) {
        is WidgetRow.Header -> row.label.hashCode().toLong()
        is WidgetRow.Item -> row.entry.item.id.hashCode().toLong()
        null -> position.toLong()
    }

    override fun hasStableIds(): Boolean = true
}

internal fun loadWindow(context: Context): List<WidgetRow> {
    val json = AlarmStore(context).readWork() ?: return emptyList()
    val board = try {
        parseBoard(json)
    } catch (_: Exception) {
        return emptyList()
    }
    return MemoPlanner.widgetRows(board, ZonedDateTime.now(MemoPlanner.ZONE))
}
