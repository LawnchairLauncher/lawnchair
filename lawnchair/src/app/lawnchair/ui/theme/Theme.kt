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
import androidx.compose.material.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.m3ColorScheme
import app.lawnchair.theme.materialColors
import app.lawnchair.ui.preferences.components.ThemeChoice
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities
import androidx.compose.material3.MaterialTheme as Material3Theme

@Composable
fun LawnchairTheme(
    darkTheme: Boolean = isSelectedThemeDark(),
    content: @Composable () -> Unit
) {
    val colorScheme = getColorScheme(darkTheme = darkTheme)
    MaterialTheme(
        colors = materialColors(colorScheme, darkTheme),
        typography = Typography,
        shapes = Shapes
    ) {
        Material3Theme(
            colorScheme = colorScheme,
            typography = M3Typography,
            content = content
        )
    }
}

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    val prefs = PreferenceManager.getInstance(context)
    val accentColor = prefs.accentColor.observeAsState().value
    val colorScheme = remember(accentColor) {
        ThemeProvider.INSTANCE.get(context).colorScheme
    }

    return m3ColorScheme(colorScheme = colorScheme, isDark = darkTheme)
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
fun isAutoThemeDark() = when {
    Utilities.ATLEAST_P -> isSystemInDarkTheme()
    else -> wallpaperSupportsDarkTheme()
}

@Composable
fun wallpaperSupportsDarkTheme(): Boolean {
    val wallpaperManager = WallpaperManagerCompat.INSTANCE.get(LocalContext.current)
    var supportsDarkTheme by remember { mutableStateOf(wallpaperManager.supportsDarkTheme) }

    DisposableEffect(wallpaperManager) {
        val listener = object : WallpaperManagerCompat.OnColorsChangedListener {
            override fun onColorsChanged() {
                supportsDarkTheme = wallpaperManager.supportsDarkTheme
            }
        }
        wallpaperManager.addOnChangeListener(listener)
        onDispose { wallpaperManager.removeOnChangeListener(listener) }
    }
    return supportsDarkTheme
}
