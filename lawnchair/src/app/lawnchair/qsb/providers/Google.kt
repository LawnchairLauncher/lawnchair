package app.lawnchair.qsb.providers

import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.view.descendants
import androidx.core.view.isVisible
import app.lawnchair.HeadlessWidgetsManager
import app.lawnchair.qsb.ThemingMethod
import app.lawnchair.smartspace.BcSmartSpaceUtil.setOnClickListener
import app.lawnchair.util.pendingIntent
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data object Google : QsbSearchProvider(
    id = "google",
    name = R.string.search_provider_google,
    icon = R.drawable.ic_super_g_color,
    themingMethod = ThemingMethod.THEME_BY_LAYER_ID,
    packageName = "com.google.android.googlequicksearchbox",
    action = "android.search.action.GLOBAL_SEARCH",
    supportVoiceIntent = true,
    website = "https://www.google.com/",
) {
    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        if (!forceWebsite) {
            val subscription = getSearchIntent(launcher)
            val pendingIntent = subscription.firstOrNull()
            if (pendingIntent != null) {
                val googleIntent = getGoogleSearchIntent(context = launcher)
                if (googleIntent != null) {
                    launcher.startActivity(googleIntent)
                    return
                }
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
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        return view.descendants
            .filter { it.pendingIntent != null }
            .sortedByDescending { it.measuredWidth * it.measuredHeight }
            .firstOrNull()
            ?.pendingIntent
    }
    private fun getGoogleSearchIntent(context: Context): Intent? {
        val intent =
            Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .setPackage("com.google.android.googlequicksearchbox")
        return if (intent.resolveActivity(context.packageManager) != null) intent else null
    }
}
