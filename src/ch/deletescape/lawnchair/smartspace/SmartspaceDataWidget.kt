package ch.deletescape.lawnchair.smartspace

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.support.annotation.Keep
import android.util.Log
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import ch.deletescape.lawnchair.BlankActivity
import ch.deletescape.lawnchair.getAllChilds
import com.android.launcher3.Utilities

@Keep
class SmartspaceDataWidget(controller: LawnchairSmartspaceController) : LawnchairSmartspaceController.DataProvider(controller) {

    private val launcher = controller.launcher
    private val prefs = Utilities.getLawnchairPrefs(launcher)
    private val smartspaceWidgetHost = SmartspaceWidgetHost()
    private var smartspaceView: SmartspaceWidgetHostView? = null
    private val widgetIdPref = prefs::smartspaceWidgetId
    private val providerInfo = getSmartspaceWidgetProvider(launcher)
    private var isWidgetBound = false

    private fun startBinding() {
        val widgetManager = AppWidgetManager.getInstance(launcher)

        var widgetId = widgetIdPref.get()
        val widgetInfo = widgetManager.getAppWidgetInfo(widgetId)
        isWidgetBound = widgetInfo != null && widgetInfo.provider == providerInfo.provider

        val oldWidgetId = widgetId
        if (!isWidgetBound) {
            if (widgetId > -1) {
                // widgetId is already bound and its not the correct provider. reset host.
                smartspaceWidgetHost.deleteHost()
            }

            widgetId = smartspaceWidgetHost.allocateAppWidgetId()
            isWidgetBound = widgetManager.bindAppWidgetIdIfAllowed(
                    widgetId, providerInfo.profile, providerInfo.provider, null)
        }

        if (isWidgetBound) {
            smartspaceView = smartspaceWidgetHost.createView(launcher, widgetId, providerInfo) as SmartspaceWidgetHostView
            smartspaceWidgetHost.startListening()
            onSetupComplete()
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            BlankActivity.startActivityForResult(launcher, bindIntent, 1028, { resultCode, _ ->
                if (resultCode == Activity.RESULT_OK) {
                    startBinding()
                } else {
                    smartspaceWidgetHost.deleteAppWidgetId(widgetId)
                    widgetId = -1
                    widgetIdPref.set(-1)
                    onSetupComplete()
                }
            })
        }

        if (oldWidgetId != widgetId) {
            widgetIdPref.set(widgetId)
        }
    }

    override fun performSetup() {
        super.performSetup()

        startBinding()
    }

    override fun waitForSetup() {
        super.waitForSetup()

        if (!isWidgetBound) throw IllegalStateException("widget must be bound")
    }

    override fun onDestroy() {
        super.onDestroy()

        smartspaceWidgetHost.stopListening()
    }

    fun updateData(weatherIcon: Bitmap, temperatureString: String) {
        val temperatureAmount = temperatureString.substring(0, temperatureString.indexOfFirst { it < '0' || it > '9' })
        updateData(weatherIcon, temperatureAmount.toInt(), temperatureString.contains("C"))
    }

    inner class SmartspaceWidgetHost : AppWidgetHost(launcher, 1027) {

        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView {
            return SmartspaceWidgetHostView(context)
        }
    }

    inner class SmartspaceWidgetHostView(context: Context) : AppWidgetHostView(context) {

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)

            val childs = getAllChilds()
            if (childs.size > 2) {
                val lastTextView = childs.last { it is TextView } as TextView
                val lastImageView = childs.last { it is ImageView } as ImageView
                val drawable = lastImageView.drawable as? BitmapDrawable
                if (drawable != null) {
                    updateData(drawable.bitmap, lastTextView.text as String)
                }
            }
        }
    }

    companion object {

        private const val TAG = "SmartspaceDataWidget"
        private const val googlePackage = "com.google.android.googlequicksearchbox"
        private const val smartspaceComponent = "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"

        private val smartspaceProviderComponent = ComponentName(googlePackage, smartspaceComponent)

        fun getSmartspaceWidgetProvider(context: Context): AppWidgetProviderInfo {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val providers = appWidgetManager.installedProviders.filter { it.provider == smartspaceProviderComponent }
            Log.d(TAG, "providers = $providers")
            return providers.firstOrNull() ?: throw RuntimeException("smartspace widget not found")
        }
    }
}
