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

package app.lawnchair.ui.preferences

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.util.preferences.observeAsState
import app.lawnchair.util.preferences.preferenceManager
import com.google.accompanist.insets.ProvideWindowInsets

class PreferenceActivity : ComponentActivity() {
    @ExperimentalMaterialApi
    @ExperimentalAnimationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            LawnchairTheme(accent = accentColor()) {
                ProvideWindowInsets {
                    Preferences()
                }
            }
        }
    }
}

@Composable
fun accentColor(): Color {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val accentColor by preferenceManager().accentColor.observeAsState()
    return remember(darkTheme, accentColor) {
        Color(app.lawnchair.util.Color.parseAccentColorInt(accentColor, context, darkTheme))
    }
}
