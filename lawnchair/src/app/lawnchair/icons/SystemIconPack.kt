package app.lawnchair.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Process
import app.lawnchair.util.requireSystemService
import com.android.launcher3.R
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class SystemIconPack(context: Context) : IconPack(context, "") {

    override val label = context.getString(R.string.system_icons)
    private val appMap = run {
        val profiles = UserCache.INSTANCE.get(context).userProfiles
        val launcherApps: LauncherApps = context.requireSystemService()
        profiles
            .flatMap { launcherApps.getActivityList(null, Process.myUserHandle()) }
            .associateBy { ComponentKey(it.componentName, it.user) }
    }

    init {
        startLoad()
    }

    override fun getIcon(componentName: ComponentName) =
        IconEntry(packPackageName, ComponentKey(componentName, Process.myUserHandle()).toString(), IconType.Normal)
    override fun getCalendar(componentName: ComponentName): IconEntry? = null
    override fun getClock(entry: IconEntry): ClockMetadata? = null

    override fun getCalendars(): MutableSet<ComponentName> = mutableSetOf()
    override fun getClocks(): MutableSet<ComponentName> = mutableSetOf()

    override fun getIcon(iconEntry: IconEntry, iconDpi: Int): Drawable? {
        val key = ComponentKey.fromString(iconEntry.name)
        val app = appMap[key] ?: return null
        return app.getIcon(iconDpi)
    }

    override fun loadInternal() {

    }

    override fun getAllIcons(): Flow<List<IconPickerCategory>> = flow {
        val items = appMap
            .map { (key, info) ->
                IconPickerItem(
                    packPackageName = packPackageName,
                    drawableName = key.toString(),
                    label = info.label.toString(),
                    IconType.Normal
                )
            }
        emit(categorize(items))
    }.flowOn(Dispatchers.IO)
}
