/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.app.WallpaperManager.FLAG_SYSTEM
import android.app.WallpaperManager.OnColorsChangedListener
import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.android.launcher3.dagger.ApplicationContext
import com.android.launcher3.dagger.LauncherAppComponent
import com.android.launcher3.dagger.LauncherAppSingleton
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR
import javax.inject.Inject

/**
 * This class caches the system's wallpaper color hints for use by other classes as a performance
 * enhancer. It also centralizes all the WallpaperManager color hint code in one location.
 */
@LauncherAppSingleton
class WallpaperColorHints
@Inject
constructor(@ApplicationContext private val context: Context, tracker: DaggerSingletonTracker) {
    var hints: Int = 0
        private set

    private val wallpaperManager
        get() = context.getSystemService(WallpaperManager::class.java)!!

    private val onColorHintsChangedListeners = mutableListOf<OnColorHintListener>()

    init {
        hints = wallpaperManager.getWallpaperColors(FLAG_SYSTEM)?.colorHints ?: 0
        val onColorsChangedListener = OnColorsChangedListener { colors, which ->
            onColorsChanged(colors, which)
        }
        UI_HELPER_EXECUTOR.execute {
            wallpaperManager.addOnColorsChangedListener(
                onColorsChangedListener,
                MAIN_EXECUTOR.handler,
            )
        }
        tracker.addCloseable {
            UI_HELPER_EXECUTOR.execute {
                wallpaperManager.removeOnColorsChangedListener(onColorsChangedListener)
            }
        }
    }

    @MainThread
    private fun onColorsChanged(colors: WallpaperColors?, which: Int) {
        if ((which and FLAG_SYSTEM) != 0) {
            val newHints = colors?.colorHints ?: 0
            if (newHints != hints) {
                hints = newHints
                onColorHintsChangedListeners.forEach { it.onColorHintsChanged(newHints) }
            }
        }
    }

    fun registerOnColorHintsChangedListener(listener: OnColorHintListener) {
        onColorHintsChangedListeners.add(listener)
    }

    fun unregisterOnColorsChangedListener(listener: OnColorHintListener) {
        onColorHintsChangedListeners.remove(listener)
    }

    companion object {
        @VisibleForTesting
        @JvmField
        val INSTANCE = DaggerSingletonObject(LauncherAppComponent::getWallpaperColorHints)

        @JvmStatic fun get(context: Context): WallpaperColorHints = INSTANCE.get(context)
    }
}

interface OnColorHintListener {
    fun onColorHintsChanged(colorHints: Int)
}
