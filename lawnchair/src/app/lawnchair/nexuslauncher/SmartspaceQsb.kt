package app.lawnchair.nexuslauncher

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Bundle
import android.os.Process
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.R
import com.android.launcher3.qsb.QsbContainerView

class SmartspaceQsb @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : QsbContainerView(context, attrs, defStyleAttr) {
    class SmartSpaceFragment : QsbFragment() {
        override fun createHost(): QsbWidgetHost {
            return QsbWidgetHost(
                context,
                SMART_SPACE_WIDGET_HOST_ID,
            ) { c: Context -> ThemedSmartSpaceHostView(c) }
        }

        @SuppressLint("NewApi")
        override fun getSearchWidgetProvider(): AppWidgetProviderInfo? {
            for (info in AppWidgetManager.getInstance(context)
                .getInstalledProvidersForPackage(WIDGET_PACKAGE_NAME, Process.myUserHandle())) {
                if (WIDGET_CLASS_NAME == info.provider.className) {
                    return info
                }
            }
            return null
        }

        override fun getDefaultView(container: ViewGroup, showSetupIcon: Boolean): View {
            return getDateView(container)
        }

        override fun createBindOptions(): Bundle {
            val opts = super.createBindOptions()
            opts.putString("attached-launcher-identifier", context.packageName)
            opts.putBoolean("com.google.android.apps.gsa.widget.PREINSTALLED", true)
            return opts
        }

        companion object {
            private const val SMART_SPACE_WIDGET_HOST_ID = 1027
        }

        init {
            mKeyWidgetId = "smart_space_widget_id"
        }
    }

    companion object {
        const val WIDGET_PACKAGE_NAME = "com.google.android.googlequicksearchbox"
        private const val WIDGET_CLASS_NAME =
            "com.google.android.apps.gsa.staticplugins.smartspace.widget.SmartspaceWidgetProvider"

        fun getDateView(parent: ViewGroup): View {
            return LayoutInflater.from(parent.context)
                .inflate(R.layout.smart_space_date_view, parent, false)
        }
    }
}
