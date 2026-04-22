package app.lawnchair

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.os.Build
import android.os.Build.VERSION
import android.os.UserHandle
import android.util.Log
import app.lawnchair.icons.getCustomAppNameForComponent
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.Flags.useNewIconForArchivedApps
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.icons.BaseIconFactory.IconOptions
import com.android.launcher3.icons.BitmapInfo
import com.android.launcher3.icons.IconProvider
import com.android.launcher3.icons.cache.BaseIconCache
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.SafeCloseable
import javax.inject.Inject

private const val TAG = "LawnchairActivityCachingLogic"

@LauncherAppSingleton
class LawnchairActivityCachingLogic @Inject constructor(
    @ApplicationContext private val context: Context,
) : SafeCloseable,
    CachingLogic<LauncherActivityInfo> {

    private val prefs = PreferenceManager.getInstance(context)

    override fun getLabel(info: LauncherActivityInfo): CharSequence? {
        val key = ComponentKey(info.componentName, info.user)
        val customLabel = prefs.customAppName[key]
        if (!customLabel.isNullOrEmpty()) {
            return customLabel
        }
        return getCustomAppNameForComponent(info)
    }

    override fun getComponent(info: LauncherActivityInfo): ComponentName = info.componentName

    override fun getUser(info: LauncherActivityInfo): UserHandle = info.user

    override fun getApplicationInfo(info: LauncherActivityInfo) = info.applicationInfo

    override fun loadIcon(
        context: Context,
        cache: BaseIconCache,
        info: LauncherActivityInfo,
    ): BitmapInfo {
        // LC-Note: LauncherActivityInfo.getActivityInfo or known as info.getActivityInfo in the code requires Android 12
        val activityInfo = if (VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.activityInfo
        } else {
            context.packageManager.getActivityInfo(info.componentName, 0)
        }
        cache.iconFactory.use { li ->
            val iconOptions: IconOptions = IconOptions().setUser(info.user)
            iconOptions
                .setIsArchived(
                    useNewIconForArchivedApps() &&
                        VERSION.SDK_INT >= 35 &&
                        activityInfo.isArchived,
                )
                .setSourceHint(getSourceHint(info, cache))
            val iconDrawable = cache.iconProvider.getIcon(activityInfo, li.fullResIconDpi)
            if (VERSION.SDK_INT >= 30 && context.packageManager.isDefaultApplicationIcon(
                    iconDrawable,
                )
            ) {
                Log.w(
                    TAG,
                    "loadIcon: Default app icon returned from PackageManager." +
                        " component=${info.componentName}, user=${info.user}",
                    Exception(),
                )
                // Make sure this default icon always matches BaseIconCache#getDefaultIcon
                return cache.getDefaultIcon(info.user)
            }
            return li.createBadgedIconBitmap(iconDrawable, iconOptions)
        }
    }

    override fun getFreshnessIdentifier(
        item: LauncherActivityInfo,
        provider: IconProvider,
    ): String? = provider.getStateForApp(getApplicationInfo(item))

    override fun close() {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getLawnchairActivityCachingLogic)
    }
}
