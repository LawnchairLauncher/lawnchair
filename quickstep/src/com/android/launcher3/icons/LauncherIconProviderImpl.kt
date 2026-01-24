/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.launcher3.icons

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageItemInfo
import android.content.res.Resources.NotFoundException
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import com.android.launcher3.LauncherModel
import com.android.launcher3.Utilities
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.graphics.ShapeDelegate.Circle
import com.android.launcher3.graphics.ThemeManager
import com.android.launcher3.icons.cache.CachingLogic
import com.android.launcher3.icons.cache.LauncherActivityCachingLogic
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.DaggerSingletonTracker
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.PluginManagerWrapper
import com.android.systemui.plugins.IconProcessorPlugin
import com.android.systemui.plugins.PluginLifecycleManager
import com.android.systemui.plugins.PluginListener
import javax.inject.Inject
import javax.inject.Provider

/** Extension of LauncherIconProvider with system APIs and plugin support */
private const val TAG = "LauncherIconProviderImpl"

@LauncherAppSingleton
class LauncherIconProviderImpl
@Inject
constructor(
    @ApplicationContext ctx: Context,
    themeManager: ThemeManager,
    private val modelProvider: Provider<LauncherModel>,
    private val iconCacheProvider: Provider<IconCache>,
    pluginManagerWrapper: PluginManagerWrapper,
    lifecycle: DaggerSingletonTracker,
) : LauncherIconProvider(ctx, themeManager), PluginListener<IconProcessorPlugin> {

    init {
        pluginManagerWrapper.addPluginListener(this, IconProcessorPlugin::class.java)
        lifecycle.addCloseable { pluginManagerWrapper.removePluginListener(this) }
    }

    private var processor: IconProcessorPlugin? = null

    override fun getApplicationInfoHash(appInfo: ApplicationInfo): String =
        (appInfo.sourceDir?.hashCode() ?: 0).toString() + " " + appInfo.longVersionCode

    override fun loadPackageIcon(
        info: PackageItemInfo,
        appInfo: ApplicationInfo,
        density: Int,
    ): Drawable? {
        fun Drawable.preprocess(resId: Int) =
            processor?.preprocessDrawable(this, resId, appInfo) ?: this

        try {
            val resources = mContext.packageManager.getResourcesForApplication(appInfo)
            // Try to load the package item icon first
            if (info !== appInfo && info.icon != 0) {
                try {
                    val icon = resources.getDrawableForDensity(info.icon, density, null)
                    if (icon != null) return icon.preprocess(info.icon)
                } catch (_: NotFoundException) {}
            }
            // Load the fallback app icon
            if (appInfo.icon != 0) {
                // Tries to load the round icon res, if the app defines it as an adaptive icon
                if (Utilities.ATLEAST_R && mThemeManager.iconShape is Circle) {
                    if (appInfo.roundIconRes != 0 && appInfo.roundIconRes != appInfo.icon) {
                        try {
                            val d =
                                resources.getDrawableForDensity(appInfo.roundIconRes, density, null)
                            if (d is AdaptiveIconDrawable) return d.preprocess(appInfo.roundIconRes)
                        } catch (_: NotFoundException) {}
                    }
                }

                try {
                    return resources
                        .getDrawableForDensity(appInfo.icon, density, null)
                        ?.preprocess(appInfo.icon)
                } catch (_: NotFoundException) {}
            }
        } catch (_: Exception) {}
        return null
    }

    override fun onPluginLoaded(
        plugin: IconProcessorPlugin?,
        pluginContext: Context?,
        manager: PluginLifecycleManager<IconProcessorPlugin>?,
    ) {
        plugin?.setIconChangeNotifier { pkg, userHandle ->
            modelProvider.get().onAppIconChanged(pkg, userHandle)
        }
        processor = plugin
        Log.d(TAG, "Plugin connected $plugin")
        MODEL_EXECUTOR.execute {
            iconCacheProvider.get().clearMemoryCache()
            modelProvider.get().reloadIfActive()
        }
    }

    override fun onPluginUnloaded(
        plugin: IconProcessorPlugin?,
        manager: PluginLifecycleManager<IconProcessorPlugin>?,
    ) {
        processor = null
        Log.d(TAG, "Plugin disconnected")
    }

    override fun notifyIconLoaded(icon: BitmapInfo, key: ComponentKey, logic: CachingLogic<*>) {
        if (logic == LauncherActivityCachingLogic)
            processor?.notifyAppIconLoaded(key.componentName, key.user, icon.flags)
    }
}
