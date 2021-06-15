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
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.ThemeChoice
import app.lawnchair.util.androidColorId
import com.android.launcher3.Utilities
import com.android.launcher3.uioverrides.WallpaperColorInfo

private val DarkColorPalette = darkColors(
    primary = Blue600,
    secondary = Blue600
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
    MaterialTheme(
        colors = getColors(darkTheme),
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

@Composable
fun getColors(darkTheme: Boolean): Colors = when {
    Utilities.ATLEAST_S -> {
        if (darkTheme) {
            val accent = colorResource(id = androidColorId(name = "system_accent1_100"))
            val surface = colorResource(id = androidColorId(name = "system_neutral1_800"))
            val background = colorResource(id = androidColorId(name = "system_neutral1_900"))
            darkColors(
                primary = accent,
                secondary = accent,
                background = background,
                surface = surface
            )
        } else {
            val accent = colorResource(id = androidColorId(name = "system_accent1_600"))
            val surface = colorResource(id = androidColorId(name = "system_neutral1_100"))
            val background = colorResource(id = androidColorId(name = "system_neutral1_50"))
            lightColors(
                primary = accent,
                secondary = accent,
                background = background,
                surface = surface
            )
        }
    }
    darkTheme -> DarkColorPalette
    else -> LightColorPalette
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
fun isAutoThemeDark(): Boolean = if (Utilities.ATLEAST_Q) isSystemInDarkTheme() else isWallpaperDark()

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
