package app.lawnchair

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.RemoteViews
import app.lawnchair.smartspace.SmartspaceAppWidgetProvider
import com.android.launcher3.R
import com.android.launcher3.widget.LauncherAppWidgetHostView

class LawnchairAppWidgetHostView(context: Context) : LauncherAppWidgetHostView(context) {

    private var customView: ViewGroup? = null

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        super.setAppWidget(appWidgetId, info)
        customView = null

        val layoutId = customLayouts[info.provider] ?: return
        removeAllViews()
        customView = LayoutInflater.from(context).inflate(layoutId, this, false) as ViewGroup
        customView!!.setOnLongClickListener(this)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        if (customView != null) {
            removeAllViews()
            addView(customView, MATCH_PARENT, MATCH_PARENT)
        }
        super.updateAppWidget(remoteViews)
    }

    companion object {

        private val customLayouts = mapOf(
            SmartspaceAppWidgetProvider.componentName to R.layout.search_container_workspace
        )
    }
}
