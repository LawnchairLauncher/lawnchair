/*
 * Copyright 2021, Lawnchair
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

package app.lawnchair.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.toAndroidColor
import app.lawnchair.ui.preferences.components.ThemeChoice
import com.android.launcher3.Utilities

@Composable
fun LawnchairTheme(
    darkTheme: Boolean = isSelectedThemeDark(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = getColors(darkTheme),
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

@Composable
fun getColors(darkTheme: Boolean): Colors {
    val context = LocalContext.current
    val prefs = PreferenceManager.getInstance(context)
    val customColor = prefs.accentColor.observeAsState().value
    val useSystemAccent = prefs.useSystemAccent.observeAsState().value
    val colorScheme = remember(customColor, useSystemAccent) {
        ThemeProvider.INSTANCE.get(context).colorScheme
    }

    return remember(colorScheme) {
        val accent = Color(colorScheme.accent1[if (darkTheme) 100 else 600]!!.toAndroidColor())
        val surface = Color(colorScheme.neutral1[if (darkTheme) 900 else 100]!!.toAndroidColor())
        val background = Color(colorScheme.neutral1[if (darkTheme) 900 else 50]!!.toAndroidColor())

        if (darkTheme) {
            darkColors(
                primary = accent,
                secondary = accent,
                background = background,
                surface = surface
            )
        } else {
            lightColors(
                primary = accent,
                secondary = accent,
                background = background,
                surface = surface
            )
        }
    }
}

@Composable
fun isSelectedThemeDark(): Boolean {
    val themeChoice by preferenceManager().launcherTheme.observeAsState()
    return when (themeChoice) {
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        else -> isAutoThemeDark()
    }
}

@Composable
fun isAutoThemeDark(): Boolean = if (Utilities.ATLEAST_Q) isSystemInDarkTheme() else false
