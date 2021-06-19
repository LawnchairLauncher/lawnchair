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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.ThemeChoice
import app.lawnchair.util.androidColorId
import com.android.launcher3.Utilities
import com.android.launcher3.uioverrides.WallpaperColorInfo
import dev.kdrag0n.android12ext.monet.theme.DynamicColorScheme
import dev.kdrag0n.android12ext.monet.theme.TargetColors

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
    val rawAccentColor = remember(darkTheme, customColor) { context.getAccentColor(darkTheme) }
    val accentColor = Color(rawAccentColor)
    val colorScheme = remember(rawAccentColor) { DynamicColorScheme(TargetColors(), rawAccentColor) }
    return when {
        Utilities.ATLEAST_S -> {
            if (darkTheme) {
                val background = colorResource(id = androidColorId(name = "system_neutral1_900"))
                val surface = colorResource(id = androidColorId(name = "system_neutral1_800"))
                darkColors(
                    primary = accentColor,
                    secondary = accentColor,
                    background = background,
                    surface = surface
                )
            } else {
                val background = colorResource(id = androidColorId(name = "system_neutral1_50"))
                val surface = colorResource(id = androidColorId(name = "system_neutral1_100"))
                lightColors(
                    primary = accentColor,
                    secondary = accentColor,
                    background = background,
                    surface = surface
                )
            }
        }
        darkTheme -> darkColors(
            primary = accentColor,
            secondary = accentColor,
            background = colorScheme.neutral1[900]!!.toComposeColor(),
            surface = colorScheme.neutral1[800]!!.toComposeColor()
        )
        else -> lightColors(
            primary = accentColor,
            secondary = accentColor,
            background = colorScheme.neutral1[50]!!.toComposeColor(),
            surface = colorScheme.neutral1[100]!!.toComposeColor()
        )
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
