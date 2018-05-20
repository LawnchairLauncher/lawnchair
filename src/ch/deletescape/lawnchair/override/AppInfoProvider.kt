package ch.deletescape.lawnchair.override

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.LauncherActivityInfo
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.AppInfo
import com.android.launcher3.LauncherAppState
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.util.ComponentKey

class AppInfoProvider private constructor(private val context: Context) : CustomInfoProvider<AppInfo>() {

    private val prefs by lazy { context.lawnchairPrefs }
    private val launcherApps by lazy { LauncherAppsCompat.getInstance(context) }

    override fun getTitle(info: AppInfo): String {
        val app = getLauncherActivityInfo(info)
        return app?.let { getTitle(it) } as String? ?: "" // TODO: can this really be null?
    }

    override fun getDefaultTitle(info: AppInfo): String {
        val app = getLauncherActivityInfo(info)
        return app?.label as String? ?: "" // TODO: can this really be null?
    }

    override fun getCustomTitle(info: AppInfo): String? {
        return prefs.customAppName[ComponentKey(info.componentName, info.user)]
    }

    fun getTitle(app: LauncherActivityInfo): CharSequence {
        return prefs.customAppName[getComponentKey(app)] ?: app.label
    }

    override fun setTitle(info: AppInfo, title: String?) {
        setTitle(ComponentKey(info.componentName, info.user), title)
    }

    fun setTitle(key: ComponentKey, title: String?) {
        prefs.customAppName[key] = title
        LauncherAppState.getInstance(context).iconCache.updateIconsForPkg(key.componentName.packageName, key.user)
    }

    fun getLauncherActivityInfo(info: AppInfo): LauncherActivityInfo? {
        return launcherApps.getActivityList(info.componentName.packageName, info.user)
                .firstOrNull { it.componentName == info.componentName }
    }

    private fun getComponentKey(app: LauncherActivityInfo) = ComponentKey(app.componentName, app.user)

    companion object {

        @SuppressLint("StaticFieldLeak")
        private var INSTANCE: AppInfoProvider? = null

        fun getInstance(context: Context): AppInfoProvider {
            if (INSTANCE == null) {
                INSTANCE = AppInfoProvider(context)
            }
            return INSTANCE!!
        }
    }
}