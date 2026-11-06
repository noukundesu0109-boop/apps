package dev.nossam.task

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

class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            manager.updateAppWidget(id, views(context, id))
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_CHECK) {
            val kind = intent.getStringExtra(AlarmIntents.EXTRA_KIND).orEmpty()
            val itemId = intent.getStringExtra(AlarmIntents.EXTRA_ITEM).orEmpty()
            val tabId = intent.getStringExtra(AlarmIntents.EXTRA_TAB).orEmpty()
            if (kind.isNotBlank() && itemId.isNotBlank()) {
                TaskAlarms.complete(context, kind, itemId, tabId)
            }
            updateAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_CHECK = "dev.nossam.task.action.WIDGET_CHECK"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            for (id in ids) {
                manager.updateAppWidget(id, views(context, id))
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list)
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
    private var items: List<dev.nossam.task.plan.DayItem> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        items = loadToday(context)
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews {
        val item = items.getOrNull(position)
        val views = RemoteViews(context.packageName, R.layout.widget_today_item)
        if (item == null) return views
        val clock = item.time?.takeIf { MemoPlanner.parseTime(it) != null }.orEmpty()
        views.setTextViewText(R.id.widget_item_time, clock)
        views.setViewVisibility(
            R.id.widget_item_time,
            if (clock.isBlank()) android.view.View.GONE else android.view.View.VISIBLE,
        )
        views.setTextViewText(R.id.widget_item_title, item.title)
        val fill = Intent().apply {
            putExtra(AlarmIntents.EXTRA_KIND, item.kind)
            putExtra(AlarmIntents.EXTRA_ITEM, item.id)
            putExtra(AlarmIntents.EXTRA_TAB, item.tabId.orEmpty())
        }
        views.setOnClickFillInIntent(R.id.widget_item_check, fill)
        views.setOnClickFillInIntent(R.id.widget_item_row, fill)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

internal fun loadToday(context: Context): List<dev.nossam.task.plan.DayItem> {
    val json = AlarmStore(context).readWork() ?: return emptyList()
    val board = try {
        dev.nossam.task.plan.parseBoard(json)
    } catch (_: Exception) {
        return emptyList()
    }
    val today = java.time.ZonedDateTime.now(MemoPlanner.ZONE).toLocalDate().toString()
    return MemoPlanner.openItemsOn(board, today)
}
