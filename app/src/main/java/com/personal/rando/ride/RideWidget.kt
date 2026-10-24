package com.personal.rando.ride

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.personal.rando.MainActivity
import com.personal.rando.R
import com.personal.rando.rando
import com.personal.rando.ui.Format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** 小组件上显示的东西 */
data class WidgetText(val km: String, val detail: String, val button: String)

/**
 * 桌面小组件：本周骑了多少公里、几次、多久；下面「开始记录」，点了打开 Rando 开始记录。
 * 骑完一次、开始记录时马上刷新，平时一小时刷新一次
 */
class RideWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** 点「开始记录」时带给 MainActivity 的 action */
        const val ACTION_START = "com.personal.rando.START_RECORDING"

        /** 骑完、开始结束记录时调一下，桌面上的数字马上变 */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(app, RideWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { render(app, manager, ids) }
        }

        fun text(week: PeriodTotal, recording: RecordingState?): WidgetText = WidgetText(
            km = String.format(Locale.US, "%.1f", week.meters / 1000),
            detail = if (week.rides == 0) "这周还没骑" else "${week.rides} 次 · ${Format.clock(week.movingSeconds)}",
            button = recording?.let { "记录中 · ${Format.distance(it.distanceMeters.toInt())}" } ?: "开始记录",
        )

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val session = context.rando.session
            val rides = session.rides.list()
            val week = RideStats.recent(rides, Period.WEEK, 1, System.currentTimeMillis()).last()
            val t = text(week, session.recording.value)
            val start = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).setAction(ACTION_START).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val open = PendingIntent.getActivity(
                context, 1,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_week).apply {
                setTextViewText(R.id.widget_km, t.km)
                setTextViewText(R.id.widget_detail, t.detail)
                setTextViewText(R.id.widget_button, t.button)
                setOnClickPendingIntent(R.id.widget_button, start)
                setOnClickPendingIntent(R.id.widget_root, open)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
