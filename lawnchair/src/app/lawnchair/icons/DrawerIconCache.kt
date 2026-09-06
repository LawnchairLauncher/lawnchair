/*
 * Copyright 2026, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.icons

import android.content.Context
import android.os.Looper
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.Favorites.DESKTOP_ICON_FLAG
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject

/**
 * A second [IconCache] for the app drawer, backed by an icon provider that ignores the icon pack
 * and per-app icon overrides.
 *
 * The icon pack is applied while the icon bitmap is built, so the regular cache holds one bitmap
 * per component that both the home screen and the drawer read. Keeping the plain icons in their
 * own cache is what lets the icon pack be limited to the home screen.
 *
 * The cache is only built once something asks for it, so users on the default setting
 * ([PreferenceManager.drawerIconPack] on) never pay for the extra database.
 */
@LauncherAppSingleton
class DrawerIconCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val idp: InvariantDeviceProfile,
    private val userCache: UserCache,
    private val themeManager: ThemeManager,
    private val installSessionHelper: InstallSessionHelper,
    private val iconPool: LauncherIcons.IconPool,
    private val lifecycle: DaggerSingletonTracker,
) {

    private val prefs = PreferenceManager.getInstance(context)

    private val plainCacheLazy = lazy {
        IconCache(
            context,
            idp,
            PLAIN_ICONS_DB,
            userCache,
            LawnchairIconProvider(context, themeManager, applyCustomIcons = false),
            installSessionHelper,
            iconPool,
            lifecycle,
        )
    }

    /** `true` while the drawer is meant to show the icons from the icon pack, as it always has. */
    private val usesIconPack get() = prefs.drawerIconPack.get()

    /**
     * The cache the drawer should read from: [default] when the icon pack covers the drawer too,
     * and the plain one when the icon pack is limited to the home screen.
     */
    fun cacheForDrawer(default: IconCache): IconCache = if (usesIconPack) default else plainCacheLazy.value

    /**
     * `true` while the drawer is showing the plain system icons, so an item taken from it carries
     * an icon the home screen would draw differently.
     */
    fun usesPlainDrawerIcons(): Boolean = !usesIconPack

    /** Drops the plain icons held in memory. Does nothing while the cache has never been built. */
    fun clearMemoryCache() {
        if (plainCacheLazy.isInitialized()) plainCacheLazy.value.clearMemoryCache()
    }

    companion object {
        private const val PLAIN_ICONS_DB = "app_icons_plain.db"
        private const val TAG = "DrawerIconCache"

        /** How long a drop waits for the icon before letting it arrive on its own. */
        private const val ICON_WAIT_MS = 500L

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDrawerIconCache)

        /**
         * Gives [info] the icon the home screen draws, for an item that was just taken out of the
         * drawer while the drawer is showing the plain system icons.
         *
         * The cache may only be read from its own thread, so a drop on the UI thread hands the
         * lookup over and waits for it. The wait is capped: a model load can hold that thread for
         * seconds, and a late icon is better than a frozen launcher. Returns `false` in that case,
         * meaning the caller should pick the icon up through [refreshHomeScreenIcon] instead.
         */
        @JvmStatic
        fun fillHomeScreenIcon(context: Context, info: WorkspaceItemInfo): Boolean {
            if (!INSTANCE.get(context).usesPlainDrawerIcons()) return true
            val cache = LauncherAppState.getInstance(context).iconCache
            if (Looper.myLooper() == Executors.MODEL_EXECUTOR.looper) {
                cache.getTitleAndIcon(info, DESKTOP_ICON_FLAG)
                return true
            }
            return try {
                Executors.MODEL_EXECUTOR
                    .submit(Callable { cache.getTitleAndIcon(info, DESKTOP_ICON_FLAG) })
                    .get(ICON_WAIT_MS, TimeUnit.MILLISECONDS)
                true
            } catch (_: TimeoutException) {
                false
            } catch (e: Exception) {
                Log.w(TAG, "Could not read the home screen icon for ${info.targetComponent}", e)
                true
            }
        }

        /** Delivers the icon to [receiver] once it is read, for when the wait above ran out. */
        @JvmStatic
        fun refreshHomeScreenIcon(
            context: Context,
            receiver: IconCache.ItemInfoUpdateReceiver,
            info: WorkspaceItemInfo,
        ) {
            LauncherAppState.getInstance(context)
                .iconCache
                .updateIconInBackground(receiver, info, DESKTOP_ICON_FLAG)
        }
    }
}
