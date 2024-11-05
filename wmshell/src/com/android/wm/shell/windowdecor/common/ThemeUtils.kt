/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.wm.shell.windowdecor.common

import android.annotation.ColorInt
import android.annotation.IntRange
import android.app.ActivityManager.RunningTaskInfo
import android.content.Context
import android.content.res.Configuration
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.graphics.Color
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/** The theme of a window decoration. */
internal enum class Theme { LIGHT, DARK }

/** Whether a [Theme] is light. */
internal fun Theme.isLight(): Boolean = this == Theme.LIGHT

/** Whether a [Theme] is dark. */
internal fun Theme.isDark(): Boolean = this == Theme.DARK

/** Returns a copy of the color with its [alpha] component replaced with the given value. */
@ColorInt
internal fun @receiver:ColorInt Int.withAlpha(@IntRange(from = 0, to = 255) alpha: Int): Int =
    Color.argb(
        alpha,
        Color.red(this),
        Color.green(this),
        Color.blue(this)
    )

/** Common opacity values used in window decoration views. */
const val OPACITY_100 = 255
const val OPACITY_11 = 28
const val OPACITY_12 = 31
const val OPACITY_15 = 38
const val OPACITY_40 = 102
const val OPACITY_55 = 140
const val OPACITY_65 = 166

/**
 * Utility class for determining themes based on system settings and app's [RunningTaskInfo].
 */
internal class DecorThemeUtil(private val context: Context) {
    private val lightColors = dynamicLightColorScheme(context)
    private val darkColors = dynamicDarkColorScheme(context)

    private val systemTheme: Theme
        get() = if ((context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES) {
            Theme.DARK
        } else {
            Theme.LIGHT
        }

    /**
     * Returns the [Theme] used by the app with the given [RunningTaskInfo].
     */
    fun getAppTheme(task: RunningTaskInfo): Theme {
        // TODO: use app's uiMode to find its actual light/dark value. It needs to be added to the
        //   TaskInfo/TaskDescription.
        val backgroundColor = task.taskDescription?.backgroundColor ?: return systemTheme
        return if (Color.valueOf(backgroundColor).luminance() < 0.5) {
            Theme.DARK
        } else {
            Theme.LIGHT
        }
    }

    /**
     * Returns the [ColorScheme] to use to style window decorations based on the given
     * [RunningTaskInfo].
     */
    fun getColorScheme(task: RunningTaskInfo): ColorScheme = when (getAppTheme(task)) {
        Theme.LIGHT -> lightColors
        Theme.DARK -> darkColors
    }
}
