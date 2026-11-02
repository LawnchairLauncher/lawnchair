package app.lawnchair.qsb

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import com.android.launcher3.qsb.QsbContainerView

class GoogleQsbContainerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : QsbContainerView(context, attrs, defStyleAttr) {

    class QsbFragment : QsbContainerView.QsbFragment() {
        override fun isQsbEnabled(): Boolean = true

        // The generic lookup skips providers with a configure activity and falls back to PremiumSearchWidgetProvider,
        // which renders empty. Pick the search box providers directly.
        override fun getSearchWidgetProvider(): AppWidgetProviderInfo? {
            val providers = AppWidgetManager.getInstance(context)
                .getInstalledProvidersForPackage(GOOGLE_PACKAGE_NAME, null)
            return PREFERRED_PROVIDER_CLASSES.firstNotNullOfOrNull { className ->
                providers.firstOrNull { it.provider.className == className }
            } ?: getSearchWidgetProviderInfo(context, GOOGLE_PACKAGE_NAME)
        }

        override fun createBindOptions(): Bundle {
            val opts = super.createBindOptions()
            opts.putString("attached-launcher-identifier", context.packageName)
            opts.putBoolean("com.google.android.apps.gsa.widget.PREINSTALLED", true)
            return opts
        }
    }

    companion object {
        private const val GOOGLE_PACKAGE_NAME = "com.google.android.googlequicksearchbox"

        private val PREFERRED_PROVIDER_CLASSES = listOf(
            "com.google.android.apps.gsa.staticplugins.searchwidget.GoogleSearchWidgetProvider",
            "com.google.android.googlequicksearchbox.SearchWidgetProvider",
        )
    }
}
