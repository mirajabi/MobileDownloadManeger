package com.miaadrajabi.fetch

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews

/**
 * Home-screen glance at the newest download. Tapping it opens the queue.
 */
class FetchWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = TransferStore(context.applicationContext)
        push(context, store.snapshot())
    }

    companion object {
        private var lastAt = 0L
        private var lastStatus = ""

        fun push(context: Context, items: List<Transfer>) {
            val status = items.joinToString("|") { it.id + ":" + it.status }
            val now = android.os.SystemClock.elapsedRealtime()
            if (status == lastStatus && now - lastAt < 1000L) return
            lastStatus = status
            lastAt = now
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                android.content.ComponentName(context, FetchWidget::class.java)
            )
            if (ids.isEmpty()) return
            val views = render(context, items)
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }

        private fun render(context: Context, items: List<Transfer>): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_fetch)
            val active = items.firstOrNull {
                it.status == TransferStore.STATUS_RUNNING ||
                    it.status == TransferStore.STATUS_QUEUED ||
                    it.status == TransferStore.STATUS_RETRYING
            }
            val failed = items.firstOrNull { it.status == TransferStore.STATUS_FAILED }
            val title = when {
                active != null && active.title.isNotBlank() -> active.title
                active != null -> active.fileName
                failed != null && failed.title.isNotBlank() -> failed.title
                failed != null -> failed.fileName
                else -> "Fetch"
            }
            val detail = when {
                active != null && active.percent >= 0 -> "${active.percent}%  ·  ${active.message}"
                active != null -> active.message.ifBlank { "Downloading" }
                failed != null -> "Tap to resume a failed download"
                items.isEmpty() -> "No download yet"
                else -> "Queue is quiet"
            }
            views.setTextViewText(R.id.widgetTitle, title)
            views.setTextViewText(R.id.widgetDetail, detail)
            views.setProgressBar(
                R.id.widgetProgress,
                100,
                if (active != null && active.percent >= 0) active.percent else 0,
                active != null && active.percent < 0
            )
            val open = PendingIntent.getActivity(
                context,
                4,
                Intent(context, QueueActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or immutable()
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, open)
            return views
        }

        private fun immutable(): Int {
            return if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        }
    }
}
