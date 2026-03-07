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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
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
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.icons.CacheableShortcutCachingLogic.loadIcon
import com.android.launcher3.icons.CacheableShortcutInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

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

        val icon = Icon.createWithResource(context, selectedHandler.iconRes)

        val shortcutInfo = ShortcutInfo.Builder(context, selectedHandler::class.java.name)
            .apply {
                setShortLabel(selectedHandler.getLabel(context))
                setIcon(icon)
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
    }
}
