package app.lawnchair

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
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
        inflateCustomView(info)
        super.setAppWidget(appWidgetId, info)
    }

    fun disablePreviewMode() {
        previewMode = false
        inflateCustomView(appWidgetInfo)
    }

    private fun inflateCustomView(info: AppWidgetProviderInfo) {
        val layoutId = customLayouts[info.provider]
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

    override fun getDefaultView(): View {
        if (customView != null) return getEmptyView()
        return super.getDefaultView()
    }

    override fun getErrorView(): View {
        if (customView != null) return getEmptyView()
        return super.getErrorView()
    }

    private fun getEmptyView(): View {
        return View(context)
    }

    companion object {

        private val customLayouts = mapOf(
            SmartspaceAppWidgetProvider.componentName to R.layout.search_container_workspace
        )
    }
}
