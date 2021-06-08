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
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.ThemeChoice
import com.android.launcher3.Utilities
import com.android.launcher3.uioverrides.WallpaperColorInfo

private val DarkColorPalette = darkColors(
    primary = Blue600,
    secondary = Blue600,
    background = Color.Black
)

private val LightColorPalette = lightColors(
    primary = Blue800,
    secondary = Blue800
)

@Composable
fun LawnchairTheme(
    darkTheme: Boolean = isSelectedThemeDark(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

@Composable
fun isSelectedThemeDark(): Boolean {
    val themeChoice by preferenceManager().launcherTheme.observeAsState()
    return when (themeChoice) {
        ThemeChoice.light -> false
        ThemeChoice.dark -> true
        else -> isAutoThemeDark()
    }
}

@Composable
fun isAutoThemeDark(): Boolean {
    return when {
        Utilities.ATLEAST_Q -> isSystemInDarkTheme()
        else -> isWallpaperDark()
    }
}

@Composable
fun isWallpaperDark(): Boolean {
    val wallpaperColorInfo = WallpaperColorInfo.INSTANCE[LocalContext.current]
    var isDark by remember { mutableStateOf(wallpaperColorInfo.isDark) }
    DisposableEffect(wallpaperColorInfo) {
        val listener = WallpaperColorInfo.OnChangeListener { isDark = it.isDark }
        wallpaperColorInfo.addOnChangeListener(listener)
        onDispose { wallpaperColorInfo.removeOnChangeListener(listener) }
    }
    return isDark
}
