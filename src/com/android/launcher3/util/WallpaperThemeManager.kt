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

package com.android.launcher3.util

import android.app.Activity
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.os.Bundle
import app.lawnchair.theme.ThemeProvider
import com.android.launcher3.LauncherConstants.SavedInstanceKeys.RUNTIME_STATE_RECREATE_TO_UPDATE_THEME
import com.android.launcher3.R
import com.android.launcher3.Utilities

/** Utility class to manage activity's theme in case it is wallpaper dependent */
class WallpaperThemeManager(private val activity: Activity) :
    OnColorHintListener, ActivityLifecycleCallbacksAdapter, ComponentCallbacks {

    private var themeRes: Int = R.style.AppTheme

    private var recreateToUpdateTheme = false

    init {
        // Update theme
        WallpaperColorHints.get(activity).registerOnColorHintsChangedListener(this)
        val expectedTheme = Themes.getActivityThemeRes(activity)
        if (expectedTheme != themeRes) {
            themeRes = expectedTheme
            activity.setTheme(expectedTheme)
        }

        if (Utilities.ATLEAST_Q) {
            activity.registerActivityLifecycleCallbacks(this)
        } else {
            activity.application.registerActivityLifecycleCallbacks(this)
        }
        activity.registerComponentCallbacks(this)
    }

    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) =
        bundle.putBoolean(RUNTIME_STATE_RECREATE_TO_UPDATE_THEME, recreateToUpdateTheme)

    override fun onActivityDestroyed(unused: Activity) =
        WallpaperColorHints.get(activity).unregisterOnColorsChangedListener(this)

    override fun onConfigurationChanged(config: Configuration) = updateTheme()

    override fun onLowMemory() {}

    override fun onColorHintsChanged(colorHints: Int) = updateTheme()

    fun updateTheme() {
        if (themeRes != Themes.getActivityThemeRes(activity)) {
            recreateToUpdateTheme()
        }
    }

    fun recreateToUpdateTheme() {
        recreateToUpdateTheme = true
        activity.recreate()
    }

    companion object {

        /**
         * Sets a wallpaper dependent theme on this activity. The activity is automatically
         * recreated when a wallpaper change can potentially change the theme.
         */
        @JvmStatic
        fun Activity.setWallpaperDependentTheme() {
            if (!isDestroyed) {
                WallpaperThemeManager(this)
            }
        }
    }
}
