/*
 * Copyright 2026, Lawnchair
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

package app.lawnchair.gestures.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.addIf
import app.lawnchair.util.ProvideLifecycleState

class LawnchairShortcutActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LawnchairTheme {
                EdgeToEdge()
                val windowSizeClass = calculateWindowSizeClass(this)

                val isExpandedScreen =
                    windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
                        windowSizeClass.heightSizeClass in
                        setOf(WindowHeightSizeClass.Expanded, WindowHeightSizeClass.Medium)

                ProvideLifecycleState {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier.addIf(isExpandedScreen) { requiredWidth(640.dp) },
                        ) {
                            val context = LocalContext.current
                            CreateActionsScreen(
                                onSelect = {
                                    saveChanges(context, it)
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun saveChanges(context: Context, selectedHandler: GestureHandlerConfig) {
        val shortcutManager =
            ContextCompat.getSystemService(applicationContext, ShortcutManager::class.java)
                ?: return

        val shortcutInfo = ShortcutInfo.Builder(
            context,
            "$GESTURE_SHORTCUT_ID_PREFIX:$selectedHandler",
        )
            .apply {
                setShortLabel(selectedHandler.getDisplayLabel(context))
                setIcon(selectedHandler.getIcon(context))
                setIntent(
                    Intent(context, RunHandlerActivity::class.java).apply {
                        action = START_ACTION
                        putExtra(EXTRA_HANDLER, GestureHandlerConfig.toString(selectedHandler))
                    },
                )
            }.build()

        val intent = shortcutManager.createShortcutResultIntent(shortcutInfo)
        setResult(RESULT_OK, intent)
    }

    companion object {
        const val START_ACTION = "app.lawnchair.START_ACTION"
        const val EXTRA_HANDLER = "app.lawnchair.EXTRA_HANDLER"
        const val GESTURE_SHORTCUT_ID_PREFIX = "gesture:"

        fun shouldSkipShortcutBadge(context: Context, si: ShortcutInfo): Boolean {
            val value = context.packageName == si.`package` &&
                si.id.startsWith(GESTURE_SHORTCUT_ID_PREFIX)

            return value
        }
    }
}
