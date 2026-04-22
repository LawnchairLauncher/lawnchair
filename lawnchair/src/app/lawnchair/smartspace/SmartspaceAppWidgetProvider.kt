package app.lawnchair.smartspace

import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import com.android.launcher3.BuildConfig

class SmartspaceAppWidgetProvider : AppWidgetProvider() {

    companion object {
        @JvmField val componentName = ComponentName(BuildConfig.APPLICATION_ID, SmartspaceAppWidgetProvider::class.java.name)
    }
}
