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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.asState
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.theme.toM3ColorScheme
import app.lawnchair.ui.preferences.components.ThemeChoice
import app.lawnchair.wallpaper.WallpaperManagerCompat
import com.android.launcher3.Utilities

@Composable
fun LawnchairTheme(
    darkTheme: Boolean = isSelectedThemeDark,
    content: @Composable () -> Unit,
) {
    val colorScheme = getColorScheme(darkTheme = darkTheme)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
        shapes = Shapes,
    )
}

@Composable
fun getColorScheme(darkTheme: Boolean): ColorScheme {
    val context = LocalContext.current
    val preferenceManager2 = preferenceManager2()
    val accentColor by preferenceManager2.accentColor.asState()
    val colorScheme = remember(accentColor) {
        ThemeProvider.INSTANCE.get(context).colorScheme
    }
//
//    colorScheme.accent1

    return colorScheme.toM3ColorScheme(isDark = darkTheme)
}

val isSelectedThemeDark: Boolean
    @Composable get() {
        val themeChoice by preferenceManager().launcherTheme.observeAsState()
        return when (themeChoice) {
            ThemeChoice.LIGHT -> false
            ThemeChoice.DARK -> true
            else -> isAutoThemeDark
        }
    }

val isAutoThemeDark: Boolean @Composable get() = when {
    Utilities.ATLEAST_P -> isSystemInDarkTheme()
    else -> wallpaperSupportsDarkTheme
}

val wallpaperSupportsDarkTheme: Boolean
    @Composable get() {
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
