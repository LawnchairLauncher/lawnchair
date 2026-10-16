package app.lawnchair.omni.experience.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.lawnchair.omni.experience.ExperienceStore
import com.android.launcher3.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class StackHost(context: Context, outerId: Int) : AppWidgetHost(context, hostId(outerId)) {
    fun releaseViews() = clearViews()
    companion object {
        fun hostId(outerId: Int): Int = 0x40000000 or outerId
    }
}

class OmniWidgetStack : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { manager.updateAppWidget(it, RemoteViews(context.packageName, R.layout.omni_stack_placeholder)) }
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { StackHost(context, it).deleteHost() }
                ExperienceStore.get(context).update { state -> state.copy(stacks = state.stacks.filterNot { it.outerId in appWidgetIds }) }
            } finally {
                result.finish()
            }
        }
    }
    companion object {
        fun component(context: Context) = ComponentName(context, OmniWidgetStack::class.java)
    }
}
