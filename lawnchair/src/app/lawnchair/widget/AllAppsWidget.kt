package app.lawnchair.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.createBitmap
import com.android.launcher3.R
import app.lawnchair.proxy.AllAppsProxyActivity
import com.android.launcher3.Launcher

class AllAppsWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val launcher = Launcher.ACTIVITY_TRACKER.getCreatedActivity<Launcher>()
        val iconBitmap = launcher?.let { createTintedIcon(it) }

        for (appWidgetId in appWidgetIds) {
            val intent = Intent(context, AllAppsProxyActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val views = RemoteViews(context.packageName, R.layout.widget_all_apps)

            iconBitmap?.let {
                views.setImageViewBitmap(R.id.widget_button, it)
            }

            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun createTintedIcon(launcher: Launcher): Bitmap {
        val drawable = AppCompatResources.getDrawable(launcher, R.drawable.ic_app_drawer)!!.mutate()
        val color = launcher.getColor(R.color.all_apps_button_color)

        drawable.setTint(color)

        val size = launcher.resources
            .getDimensionPixelSize(R.dimen.all_apps_button_size)
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }
}
