package ch.deletescape.lawnchair

import android.content.ComponentName
import android.content.Context
import com.google.android.apps.nexuslauncher.NexusAppFilter
import com.google.android.apps.nexuslauncher.NexusLauncherActivity
import java.util.HashSet

open class LawnchairAppFilter(context: Context) : NexusAppFilter(context) {

    private val hideList = HashSet<ComponentName>()

    init {
        hideList.add(ComponentName(context, NexusLauncherActivity::class.java.name))
    }

    override fun shouldShowApp(componentName: ComponentName): Boolean {
        return !hideList.contains(componentName) && super.shouldShowApp(componentName)
    }
}