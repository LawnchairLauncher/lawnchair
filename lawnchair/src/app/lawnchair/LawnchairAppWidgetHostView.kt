package app.lawnchair

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.RemoteViews
import app.lawnchair.smartspace.SmartspaceAppWidgetProvider
import com.android.launcher3.R
import com.android.launcher3.util.Themes
import com.android.launcher3.widget.LauncherAppWidgetHostView

class LawnchairAppWidgetHostView @JvmOverloads constructor(
    context: Context,
    private var previewMode: Boolean = false
) : LauncherAppWidgetHostView(context) {

    private var customView: ViewGroup? = null

    override fun setAppWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        super.setAppWidget(appWidgetId, info)
        customView = null

        inflateCustomView()
    }

    fun disablePreviewMode() {
        previewMode = false
        inflateCustomView()
    }

    private fun inflateCustomView() {
        val layoutId = customLayouts[appWidgetInfo.provider]
        if (layoutId == null) {
            customView = null
            return
        }
        val inflationContext = if (previewMode) Themes.createWidgetPreviewContext(context) else context
        customView = LayoutInflater.from(inflationContext)
            .inflate(layoutId, this, false) as ViewGroup
        customView!!.setOnLongClickListener(this)
        removeAllViews()
        addView(customView, MATCH_PARENT, MATCH_PARENT)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        if (customView != null) return
        super.updateAppWidget(remoteViews)
    }

    companion object {

        private val customLayouts = mapOf(
            SmartspaceAppWidgetProvider.componentName to R.layout.search_container_workspace
        )
    }
}
