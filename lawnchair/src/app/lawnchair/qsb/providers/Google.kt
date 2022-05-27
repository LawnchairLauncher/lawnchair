package app.lawnchair.qsb.providers

import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.Intent
import android.view.View
import app.lawnchair.HeadlessWidgetsManager
import app.lawnchair.qsb.ThemingMethod
import app.lawnchair.util.pendingIntent
import app.lawnchair.util.recursiveChildren
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView
import kotlinx.coroutines.flow.*

object Google : QsbSearchProvider(
    id = "google",
    name = R.string.search_provider_google,
    icon = R.drawable.ic_super_g_color,
    themingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    packageName = "com.google.android.googlequicksearchbox",
    action = "android.search.action.GLOBAL_SEARCH",
    supportVoiceIntent = true,
    website = "https://www.google.com/"
) {
    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        if (!forceWebsite) {
            val subscription = getSearchIntent(launcher)
            val pendingIntent = subscription.firstOrNull()
            if (pendingIntent != null) {
                launcher.startIntentSender(
                    pendingIntent.intentSender, null,
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                    0
                )
                return
            }
        }
        super.launch(launcher, forceWebsite)
    }

    fun getSearchIntent(context: Context): Flow<PendingIntent?> {
        val info = QsbContainerView.getSearchWidgetProviderInfo(context, Google.packageName) ?: return flowOf(null)
        val headlessWidgetsManager = HeadlessWidgetsManager.INSTANCE.get(context)
        return headlessWidgetsManager.subscribeUpdates(info, "hotseatWidgetId")
            .map(::findSearchIntent)
    }

    private fun findSearchIntent(view: AppWidgetHostView): PendingIntent? {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        )
        return view.recursiveChildren
            .filter { it.pendingIntent != null }
            .sortedByDescending { it.measuredWidth * it.measuredHeight }
            .firstOrNull()
            ?.pendingIntent
    }
}
