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
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.IconCache
import com.android.launcher3.icons.LauncherIcons
import com.android.launcher3.pm.InstallSessionHelper
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.DaggerSingletonObject
import com.android.launcher3.util.DaggerSingletonTracker
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

    /** Drops the plain icons held in memory. Does nothing while the cache has never been built. */
    fun clearMemoryCache() {
        if (plainCacheLazy.isInitialized()) plainCacheLazy.value.clearMemoryCache()
    }

    companion object {
        private const val PLAIN_ICONS_DB = "app_icons_plain.db"

        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getDrawerIconCache)
    }
}
