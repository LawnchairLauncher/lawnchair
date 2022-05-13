package app.lawnchair

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.edit
import com.android.launcher3.Utilities
import com.android.launcher3.util.MainThreadInitializedObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn

class HeadlessWidgetsManager(private val context: Context) {

    private val scope = MainScope() + CoroutineName("HeadlessWidgetsManager")
    private val prefs = Utilities.getDevicePrefs(context)
    private val widgetManager = AppWidgetManager.getInstance(context)
    private val host = HeadlessAppWidgetHost(context)
    private val subscriptionsMap = mutableMapOf<String, WidgetSubscription>()

    init {
        host.startListening()
    }

    fun subscribeUpdates(info: AppWidgetProviderInfo, prefKey: String): Flow<AppWidgetHostView> {
        var subscription = subscriptionsMap[prefKey]
        if (subscription != null && info.provider != subscription.info.provider) {
            subscription.cancelCallback?.invoke()
            subscription = null
        }

        if (subscription != null) {
            return subscription.flow
        }

        subscription = createSubscription(info, prefKey)
        subscriptionsMap[prefKey] = subscription
        return subscription.flow
    }

    private fun createSubscription(info: AppWidgetProviderInfo, prefKey: String): WidgetSubscription {
        val subscription = WidgetSubscription(info)
        subscription.flow = callbackFlow {
            subscription.cancelCallback = { close() }
            val widgetId = bindWidget(info, prefKey)
            if (widgetId == -1) {
                close()
                return@callbackFlow
            }
            try {
                val view = host.createView(context, widgetId, info) as HeadlessAppWidgetHostView
                trySend(view)
                view.updateCallback = { trySend(it) }
                awaitCancellation()
            } finally {
                host.deleteAppWidgetId(widgetId)
            }
        }.shareIn(
            scope,
            SharingStarted.WhileSubscribed(),
            replay = 1
        )
        return subscription
    }

    private fun bindWidget(info: AppWidgetProviderInfo, prefKey: String): Int {
        var widgetId = prefs.getInt(prefKey, -1)
        val boundInfo = widgetManager.getAppWidgetInfo(widgetId)
        var isBound = boundInfo != null && info.provider == boundInfo.provider

        if (!isBound) {
            if (widgetId > -1) {
                host.deleteAppWidgetId(widgetId)
            }

            widgetId = host.allocateAppWidgetId()
            isBound = widgetManager.bindAppWidgetIdIfAllowed(
                widgetId, info.profile, info.provider, null)
        }

        if (!isBound) {
            host.deleteAppWidgetId(widgetId)
            widgetId = -1
        }

        prefs.edit { putInt(prefKey, widgetId) }

        return widgetId
    }

    private class HeadlessAppWidgetHost(context: Context) : AppWidgetHost(context, 1028) {

        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView {
            return HeadlessAppWidgetHostView(context)
        }
    }

    @SuppressLint("ViewConstructor")
    private class HeadlessAppWidgetHostView(context: Context) :
        AppWidgetHostView(context) {

        var updateCallback: ((view: AppWidgetHostView) -> Unit)? = null

        override fun updateAppWidget(remoteViews: RemoteViews?) {
            super.updateAppWidget(remoteViews)

            updateCallback?.invoke(this)
        }
    }

    private class WidgetSubscription(val info: AppWidgetProviderInfo) {

        lateinit var flow: Flow<AppWidgetHostView>
        var cancelCallback: (() -> Unit)? = null
    }

    companion object {

        val INSTANCE = MainThreadInitializedObject(::HeadlessWidgetsManager)
    }
}
