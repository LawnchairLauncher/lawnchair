package app.lawnchair.widget.worldclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * Shows one row per city. Every time is a TextClock bound to its zone, so the widget keeps
 * itself current without alarms or update periods, including the zone abbreviation across DST.
 * 12/24 h is forced by giving both formats the same pattern, independent of the system setting.
 */
class WorldClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val store = WorldClockStore(context)
        appWidgetIds.forEach(store::delete)
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val config = WorldClockStore(context).load(widgetId)
            val pattern = if (config.use24h) "HH:mm" else "h:mm"
            val root = RemoteViews(context.packageName, R.layout.widget_world_clock)
            root.removeAllViews(R.id.world_clock_rows)
            config.cities.forEach { city ->
                val row = RemoteViews(context.packageName, R.layout.widget_world_clock_row)
                row.setTextViewText(R.id.world_clock_city, city.label)
                row.setTextViewText(R.id.world_clock_region, city.region)
                row.setViewVisibility(R.id.world_clock_region, if (city.region.isBlank()) View.GONE else View.VISIBLE)
                for (clock in intArrayOf(R.id.world_clock_time, R.id.world_clock_ampm, R.id.world_clock_day)) {
                    row.setString(clock, "setTimeZone", city.zone)
                }
                row.setCharSequence(R.id.world_clock_time, "setFormat12Hour", pattern)
                row.setCharSequence(R.id.world_clock_time, "setFormat24Hour", pattern)
                row.setViewVisibility(R.id.world_clock_ampm, if (config.use24h) View.GONE else View.VISIBLE)
                root.addView(R.id.world_clock_rows, row)
            }
            // Locked widgets ignore taps; the launcher's widget settings button still opens the editor
            if (!config.locked) {
                val edit = Intent(context, WorldClockConfigActivity::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    // distinct data keeps one PendingIntent per widget
                    .setData(Uri.parse("lawnchair-worldclock://$widgetId"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                root.setOnClickPendingIntent(
                    R.id.world_clock_root,
                    PendingIntent.getActivity(
                        context,
                        widgetId,
                        edit,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            }
            manager.updateAppWidget(widgetId, root)
        }
    }
}
