package app.lawnchair.qsb.providers

import android.app.ActivityOptions
import android.app.PendingIntent
import android.appwidget.AppWidgetHostView
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.View
import androidx.core.view.descendants
import app.lawnchair.HeadlessWidgetsManager
import app.lawnchair.qsb.ThemingMethod
import app.lawnchair.util.pendingIntent
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.qsb.QsbContainerView
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

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
    suspend fun launchVoice(launcher: Launcher) {
        val pendingIntent = withTimeoutOrNull(2.seconds) {
            getVoiceIntent(launcher).filterNotNull().firstOrNull()
        }
        if (pendingIntent != null) {
            val options = ActivityOptions.makeBasic()
            if (Utilities.ATLEAST_U) {
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
            try {
                launcher.startIntentSender(
                    pendingIntent.intentSender,
                    null,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    Intent.FLAG_ACTIVITY_NEW_TASK,
                    0,
                    options.toBundle(),
                )
                return
            } catch (_: Exception) {
            }
        }
        try {
            launcher.startActivity(handleCreateVoiceIntent())
        } catch (_: Exception) {
            try {
                launcher.startActivity(
                    Intent(RecognizerIntent.ACTION_WEB_SEARCH).addFlags(INTENT_FLAGS),
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun handleCreateVoiceIntent(): Intent = Intent(RecognizerIntent.ACTION_WEB_SEARCH)
        .addFlags(INTENT_FLAGS)
        .setPackage(packageName)

    override suspend fun launch(launcher: Launcher, forceWebsite: Boolean) {
        if (!forceWebsite) {
            val pendingIntent = withTimeoutOrNull(2.seconds) {
                getSearchIntent(launcher).filterNotNull().firstOrNull()
            }
            if (pendingIntent != null) {
                val options = ActivityOptions.makeBasic()
                if (Utilities.ATLEAST_U) {
                    options.setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                try {
                    launcher.startIntentSender(
                        pendingIntent.intentSender,
                        null,
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                        0,
                        options.toBundle(),
                    )
                    return
                } catch (_: Exception) {
                }
            }
        }
        super.launch(launcher, forceWebsite)
    }

    fun getSearchIntent(context: Context): Flow<PendingIntent?> {
        return getWidgetIntents(context).map(::findSearchIntent)
    }

    fun getVoiceIntent(context: Context): Flow<PendingIntent?> {
        return getWidgetIntents(context).map(::findVoiceIntent)
    }

    private fun getWidgetIntents(context: Context): Flow<AppWidgetHostView> {
        val info = QsbContainerView.getSearchWidgetProviderInfo(context, Google.packageName)
            ?: return emptyFlow()
        val headlessWidgetsManager = HeadlessWidgetsManager.INSTANCE.get(context)
        return headlessWidgetsManager.subscribeUpdates(info, "hotseatWidgetId")
    }

    private fun findSearchIntent(view: AppWidgetHostView): PendingIntent? {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1000, 100)
        return view.descendants
            .filter { it.pendingIntent != null }
            .sortedByDescending { it.measuredWidth * it.measuredHeight }
            .firstOrNull()
            ?.pendingIntent
    }

    private fun findVoiceIntent(view: AppWidgetHostView): PendingIntent? {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 1000, 100)
        return view.descendants
            .filter { child ->
                if (child.pendingIntent == null) return@filter false
                val description = child.contentDescription?.toString()?.lowercase() ?: ""
                val resName = runCatching {
                    if (child.id != View.NO_ID) child.resources.getResourceEntryName(child.id).lowercase() else ""
                }.getOrDefault("")
                "voice" in description || "microphone" in description || "mic" in description || "assistant" in description ||
                    "voice" in resName || "microphone" in resName || "mic" in resName || "assistant" in resName
            }
            .maxByOrNull { it.measuredWidth * it.measuredHeight }
            ?.pendingIntent
    }
}
